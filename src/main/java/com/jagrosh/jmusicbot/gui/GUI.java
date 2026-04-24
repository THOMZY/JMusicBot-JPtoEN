/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class GUI extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(GUI.class);
    private final ConsolePanel console;
    private PlaylistManagerPanel playlistManager;
    private Bot bot;
    private final Instant startedAt;

    private final JLabel botStatusValue;
    private final JLabel uptimeValue;
    private final JLabel guildCountValue;
    private final JLabel pingValue;
    private final JLabel memoryValue;
    private final JLabel logLineValue;
    private final JLabel ffmpegValue;
    private final JLabel ytDlpVersionValue;
    private final JLabel statusBadge;

    private final JComboBox<GuildSelectionItem> guildSelector;
    private final JLabel selectedGuildStatusValue;
    private final JLabel currentTrackValue;
    private final JLabel playerStateValue;
    private final JLabel queueSizeValue;
    private final JLabel playerVolumeValue;
    private final JLabel playerHintValue;
    private final JLabel actionFeedbackValue;
    private final JLabel queuePanelTitleValue;
    private final DefaultTableModel queueTableModel;
    private final JTable queueTable;
    private final JTextField playUrlField;
    private final JButton addTrackButton;
    private final JButton playButton;
    private final JButton pauseButton;
    private final JButton skipButton;
    private final JButton stopButton;
    private final JSlider volumeSlider;

    private boolean updatingGuildSelector;
    private boolean updatingVolumeFromStatus;
    private long lastControllerRefreshAt;
    private String guildSelectorSnapshot = "";
    private volatile boolean jdaSnapshotInFlight;
    private volatile long lastJdaSnapshotRequestAt;
    private volatile String cachedJdaStatus = "Starting";
    private volatile String cachedGuildCount = "0";
    private volatile String cachedPing = "-";
    private volatile boolean cachedConnected;
    private boolean listTargetsLoadedAfterConnect;
    private Instant lastExternalToolsRefreshedAt;
    private volatile boolean externalToolsSnapshotInFlight;

    private static final int STATUS_REFRESH_INTERVAL_MS = 1000;
    private static final int CONTROLLER_REFRESH_INTERVAL_MS = 3000;
    private static final int JDA_SNAPSHOT_INTERVAL_MS = 5000;
    private static final long GUI_SLOW_REFRESH_WARN_MS = 200;

    public GUI() {
        super();
        console = new ConsolePanel();
        playlistManager = null;
        startedAt = Instant.now();
        listTargetsLoadedAfterConnect = false;

        botStatusValue = new JLabel("Initializing");
        uptimeValue = new JLabel("00:00:00");
        guildCountValue = new JLabel("0");
        pingValue = new JLabel("-");
        memoryValue = new JLabel("-");
        logLineValue = new JLabel("0");
        ffmpegValue = new JLabel("Checking");
        ytDlpVersionValue = new JLabel("Checking");
        statusBadge = new JLabel("Initializing");
        lastExternalToolsRefreshedAt = Instant.EPOCH;

        guildSelector = new JComboBox<>();
        selectedGuildStatusValue = new JLabel("No server selected");
        currentTrackValue = new JLabel("Nothing playing");
        playerStateValue = new JLabel("Idle");
        queueSizeValue = new JLabel("0");
        playerVolumeValue = new JLabel("100%");
        playerHintValue = new JLabel("Select a server to control music playback.");
        actionFeedbackValue = new JLabel("Ready");
        queuePanelTitleValue = new JLabel("Queue (0)");
        queueTableModel = new DefaultTableModel(new Object[]{"Track", "^", "v", "x"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        queueTable = new JTable(queueTableModel);
        playUrlField = new JTextField();
        addTrackButton = new JButton("Play URL");
        playButton = new JButton("Play");
        pauseButton = new JButton("Pause");
        skipButton = new JButton("Skip");
        stopButton = new JButton("Stop");
        volumeSlider = new JSlider(0, 150, 100);
    }
    
    public GUI(Bot bot) {
        this();
        setBot(bot);
    }
    
    public void setBot(Bot bot) {
        this.bot = bot;
        if (bot != null && playlistManager == null) {
            playlistManager = new PlaylistManagerPanel(bot);
        }
    }

    public void init() {
        installLookAndFeel();

        if (bot != null && playlistManager == null) {
            playlistManager = new PlaylistManagerPanel(bot);
        }

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("JMusicBot JP");
        setMinimumSize(new Dimension(980, 640));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createMainTabs(), BorderLayout.CENTER);

        setContentPane(root);
        applyDashboardStyling();
        setupPlayerControlActions();

        Timer refreshTimer = new Timer(STATUS_REFRESH_INTERVAL_MS, e -> refreshStatus());
        refreshTimer.start();
        refreshStatus();

        pack();
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setVisible(true);
        setState(Frame.NORMAL);
        toFront();
        requestFocus();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownBotSafely();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                refreshTimer.stop();
            }
        });
    }

    private void installLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Component.arc", 14);
            UIManager.put("Button.arc", 14);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.showButtons", true);
        } catch (Exception ignored) {
            // Fall back to default look-and-feel if FlatLaf cannot be applied.
        }
    }

    private JTabbedPane createMainTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", createDashboard());
        tabs.addTab("Console", console);
        if (playlistManager != null) {
            tabs.addTab("Playlist Manager", playlistManager);
        }
        return tabs;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("JMusicBot Control Center");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel subtitle = new JLabel("Monitor runtime status and manage logs from one place");
        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(subtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        statusBadge.setOpaque(true);
        statusBadge.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        JButton clearButton = new JButton("Clear logs");
        JButton copyButton = new JButton("Copy logs");
        clearButton.addActionListener(e -> console.clearConsole());
        copyButton.addActionListener(e -> console.copyAllLogs());

        right.add(statusBadge);
        right.add(clearButton);
        right.add(copyButton);

        header.add(textPanel, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createDashboard() {
        JPanel dashboard = new JPanel(new BorderLayout(12, 12));
        dashboard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel cards = new JPanel(new GridLayout(2, 4, 12, 12));
        cards.add(createStatCard("Bot status", botStatusValue));
        cards.add(createStatCard("Uptime", uptimeValue));
        cards.add(createStatCard("Connected guilds", guildCountValue));
        cards.add(createStatCard("Gateway ping", pingValue));
        cards.add(createStatCard("Memory usage", memoryValue));
        cards.add(createStatCard("Console lines", logLineValue));
        cards.add(createStatCard("ffmpeg", ffmpegValue));
        cards.add(createStatCard("yt-dlp version", ytDlpVersionValue));

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 12));
        center.add(createPlayerControllerCard());
        center.add(createQueueCard());

        dashboard.add(cards, BorderLayout.NORTH);
        dashboard.add(center, BorderLayout.CENTER);
        return dashboard;
    }

    private JPanel createQueueCard() {
        JPanel queueCard = new JPanel(new BorderLayout(10, 10));
        queueCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Queue"),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        queuePanelTitleValue.setFont(queuePanelTitleValue.getFont().deriveFont(Font.BOLD, 13f));
        queueTable.setRowHeight(26);
        queueTable.setFillsViewportHeight(true);
        queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        queueTable.setShowGrid(false);
        queueTable.setIntercellSpacing(new Dimension(0, 0));
        queueTable.getTableHeader().setReorderingAllowed(false);
        queueTable.getTableHeader().setResizingAllowed(false);
        queueTable.getColumnModel().getColumn(0).setPreferredWidth(500);
        queueTable.getColumnModel().getColumn(1).setPreferredWidth(34);
        queueTable.getColumnModel().getColumn(2).setPreferredWidth(34);
        queueTable.getColumnModel().getColumn(3).setPreferredWidth(34);
        queueTable.getColumnModel().getColumn(1).setMaxWidth(34);
        queueTable.getColumnModel().getColumn(2).setMaxWidth(34);
        queueTable.getColumnModel().getColumn(3).setMaxWidth(34);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        queueTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        queueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = queueTable.rowAtPoint(e.getPoint());
                int col = queueTable.columnAtPoint(e.getPoint());
                if (row < 0 || col < 1 || col > 3) {
                    return;
                }
                handleQueueAction(row, col);
            }
        });

        JScrollPane queueScroll = new JScrollPane(queueTable);
        queueScroll.setBorder(BorderFactory.createLineBorder(new Color(226, 226, 226)));

        JPanel loadRow = new JPanel(new BorderLayout(8, 0));
        playUrlField.setToolTipText("Paste a direct link or search query");
        loadRow.add(playUrlField, BorderLayout.CENTER);
        loadRow.add(addTrackButton, BorderLayout.EAST);

        queueCard.add(queuePanelTitleValue, BorderLayout.NORTH);
        queueCard.add(queueScroll, BorderLayout.CENTER);
        queueCard.add(loadRow, BorderLayout.SOUTH);
        return queueCard;
    }

    private JPanel createPlayerControllerCard() {
        JPanel card = new JPanel(new BorderLayout(12, 12));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Player Controller"),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JPanel top = new JPanel(new BorderLayout(8, 6));
        top.add(new JLabel("Server"), BorderLayout.NORTH);
        JPanel selectorRow = new JPanel(new BorderLayout(8, 0));
        selectorRow.add(guildSelector, BorderLayout.CENTER);
        selectorRow.add(selectedGuildStatusValue, BorderLayout.EAST);
        top.add(selectorRow, BorderLayout.CENTER);
        top.add(playerHintValue, BorderLayout.SOUTH);

        JPanel infoGrid = new JPanel(new GridLayout(4, 1, 8, 6));
        infoGrid.add(createInfoRow("Now Playing", currentTrackValue));
        infoGrid.add(createInfoRow("State", playerStateValue));
        infoGrid.add(createInfoRow("Queue", queueSizeValue));
        infoGrid.add(createInfoRow("Volume", playerVolumeValue));

        JPanel controls = new JPanel(new GridLayout(1, 4, 8, 0));
        controls.add(playButton);
        controls.add(pauseButton);
        controls.add(skipButton);
        controls.add(stopButton);

        JPanel volumePanel = new JPanel(new BorderLayout(8, 0));
        volumePanel.add(new JLabel("Volume"), BorderLayout.WEST);
        volumePanel.add(volumeSlider, BorderLayout.CENTER);

        JPanel feedbackPanel = new JPanel(new BorderLayout());
        feedbackPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(218, 218, 218)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        feedbackPanel.add(actionFeedbackValue, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(volumePanel, BorderLayout.CENTER);
        bottom.add(feedbackPanel, BorderLayout.SOUTH);

        card.add(top, BorderLayout.NORTH);
        card.add(infoGrid, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createInfoRow(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel key = new JLabel(label + ":");
        key.setFont(key.getFont().deriveFont(Font.PLAIN, 12f));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 13f));
        row.add(key, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(4, 8));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(214, 214, 214)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 22f));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void applyDashboardStyling() {
        statusBadge.putClientProperty("JComponent.roundRect", true);
        selectedGuildStatusValue.putClientProperty("JComponent.roundRect", true);
        playerStateValue.putClientProperty("JComponent.roundRect", true);
        actionFeedbackValue.putClientProperty("JComponent.roundRect", true);

        statusBadge.setOpaque(true);
        selectedGuildStatusValue.setOpaque(true);
        playerStateValue.setOpaque(true);
        actionFeedbackValue.setOpaque(true);

        selectedGuildStatusValue.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        playerStateValue.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        actionFeedbackValue.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        playerHintValue.setForeground(new Color(110, 110, 110));
        actionFeedbackValue.setForeground(new Color(28, 108, 58));
        actionFeedbackValue.setBackground(new Color(226, 245, 234));
        actionFeedbackValue.setHorizontalAlignment(SwingConstants.LEFT);

        guildSelector.setMaximumRowCount(20);
        guildSelector.setRenderer(new GuildSelectionRenderer());

        styleControlButton(playButton);
        styleControlButton(pauseButton);
        styleControlButton(skipButton);
        styleControlButton(stopButton);
        styleControlButton(addTrackButton);

        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(false);

        volumeSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!volumeSlider.isEnabled()) {
                    return;
                }
                int min = volumeSlider.getMinimum();
                int max = volumeSlider.getMaximum();
                int width = Math.max(1, volumeSlider.getWidth() - 1);
                int x = Math.max(0, Math.min(e.getX(), width));
                int value = min + (int) Math.round((double) x / (double) width * (max - min));
                volumeSlider.setValue(value);
            }
        });
    }

    private void styleControlButton(JButton button) {
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFocusPainted(false);
    }

    private void setupPlayerControlActions() {
        guildSelector.addActionListener(e -> {
            if (!updatingGuildSelector) {
                refreshPlayerController();
            }
        });

        playButton.addActionListener(e -> runPlayerAction("Playback resumed", guild -> {
            AudioHandler handler = getAudioHandler(guild);
            if (handler != null) {
                handler.getPlayer().setPaused(false);
                return true;
            }
            return false;
        }));

        pauseButton.addActionListener(e -> runPlayerAction("Playback paused", guild -> {
            AudioHandler handler = getAudioHandler(guild);
            if (handler != null) {
                handler.getPlayer().setPaused(true);
                return true;
            }
            return false;
        }));

        skipButton.addActionListener(e -> runPlayerAction("Skipped current track", guild -> {
            AudioHandler handler = getAudioHandler(guild);
            if (handler != null) {
                handler.getPlayer().stopTrack();
                return true;
            }
            return false;
        }));

        stopButton.addActionListener(e -> runPlayerAction("Playback stopped and queue cleared", guild -> {
            AudioHandler handler = getAudioHandler(guild);
            if (handler != null) {
                handler.stopAndClear();
                guild.getAudioManager().closeAudioConnection();
                return true;
            }
            return false;
        }));

        addTrackButton.addActionListener(e -> playFromInput());
        playUrlField.addActionListener(e -> playFromInput());

        volumeSlider.addChangeListener(e -> {
            int volume = volumeSlider.getValue();
            playerVolumeValue.setText(volume + "%");
            if (updatingVolumeFromStatus || volumeSlider.getValueIsAdjusting()) {
                return;
            }
            runPlayerAction("Volume set to " + volume + "%", guild -> {
                AudioHandler handler = getAudioHandler(guild);
                if (handler != null) {
                    handler.getPlayer().setVolume(volume);
                }
                bot.getSettingsManager().getSettings(guild).setVolume(volume);
                return true;
            });
        });
    }

    private void runPlayerAction(String successMessage, GuildAction action) {
        if (bot == null || bot.getJDA() == null) {
            setActionFeedback("Bot is not ready yet", false);
            return;
        }
        Guild guild = getSelectedGuild();
        if (guild == null) {
            setActionFeedback("No server selected", false);
            return;
        }
        try {
            if (action.run(guild)) {
                setActionFeedback(successMessage, true);
            } else {
                setActionFeedback("No active player in this server", false);
            }
        } catch (Exception ex) {
            setActionFeedback("Action failed: " + ex.getMessage(), false);
        }
        refreshPlayerController();
    }

    private void playFromInput() {
        String input = playUrlField.getText() == null ? "" : playUrlField.getText().trim();
        if (input.isEmpty()) {
            setActionFeedback("Paste a URL or search query first", false);
            return;
        }
        if (bot == null || bot.getJDA() == null) {
            setActionFeedback("Bot is not ready yet", false);
            return;
        }

        Guild guild = getSelectedGuild();
        if (guild == null) {
            setActionFeedback("No server selected", false);
            return;
        }

        AudioHandler handler = bot.getPlayerManager().setUpHandler(guild);
        if (handler == null) {
            setActionFeedback("Could not initialize audio for this server", false);
            return;
        }

        final String identifier = input.startsWith("http://") || input.startsWith("https://")
                ? input
                : "ytsearch:" + input;

        setActionFeedback("Loading track...", true);

        bot.getPlayerManager().loadItemOrdered(guild, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                int pos = handler.addTrack(new QueuedTrack(track, bot.getJDA().getSelfUser())) + 1;
                String msg = pos == 0
                        ? "Now playing: " + ellipsize(track.getInfo().title, 48)
                        : "Added to queue (#" + pos + "): " + ellipsize(track.getInfo().title, 48);
                SwingUtilities.invokeLater(() -> {
                    setActionFeedback(msg, true);
                    playUrlField.setText("");
                    refreshPlayerController();
                });
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    SwingUtilities.invokeLater(() -> setActionFeedback("Playlist is empty", false));
                    return;
                }
                AudioTrack track = playlist.getSelectedTrack() != null
                        ? playlist.getSelectedTrack()
                        : playlist.getTracks().get(0);
                int pos = handler.addTrack(new QueuedTrack(track, bot.getJDA().getSelfUser())) + 1;
                String msg = pos == 0
                        ? "Now playing: " + ellipsize(track.getInfo().title, 48)
                        : "Added to queue (#" + pos + "): " + ellipsize(track.getInfo().title, 48);
                SwingUtilities.invokeLater(() -> {
                    setActionFeedback(msg, true);
                    playUrlField.setText("");
                    refreshPlayerController();
                });
            }

            @Override
            public void noMatches() {
                SwingUtilities.invokeLater(() -> setActionFeedback("No results found", false));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                SwingUtilities.invokeLater(() ->
                        setActionFeedback("Failed to load track: " + exception.getMessage(), false));
            }
        });
    }

    private void refreshPlayerController() {
        refreshGuildSelector();

        Guild guild = getSelectedGuild();
        if (guild == null || bot == null || bot.getJDA() == null) {
            selectedGuildStatusValue.setText("No server");
            selectedGuildStatusValue.setBackground(new Color(238, 238, 238));
            selectedGuildStatusValue.setForeground(new Color(90, 90, 90));
            currentTrackValue.setText("Nothing playing");
            currentTrackValue.setToolTipText(null);
            setPlayerStateBadge("Idle", new Color(238, 238, 238), new Color(90, 90, 90));
            queueSizeValue.setText("0");
            setControlState(false, false, false, false, false);
            refreshQueuePanel(null);
            return;
        }

        selectedGuildStatusValue.setText(getGuildVoiceStateText(guild));
        if (isGuildVoiceConnected(guild)) {
            selectedGuildStatusValue.setBackground(new Color(226, 245, 234));
            selectedGuildStatusValue.setForeground(new Color(28, 108, 58));
        } else {
            selectedGuildStatusValue.setBackground(new Color(238, 238, 238));
            selectedGuildStatusValue.setForeground(new Color(90, 90, 90));
        }

        AudioHandler handler = getAudioHandler(guild);
        int currentVolume = bot.getSettingsManager().getSettings(guild).getVolume();

        if (handler == null) {
            currentTrackValue.setText("Not connected to voice");
            currentTrackValue.setToolTipText(null);
            setPlayerStateBadge("Idle", new Color(238, 238, 238), new Color(90, 90, 90));
            queueSizeValue.setText("0");
            setControlState(false, false, false, false, true);
            updateVolumeUi(currentVolume);
            refreshQueuePanel(null);
            return;
        }

        AudioTrack track = handler.getPlayer().getPlayingTrack();
        boolean playing = track != null;
        boolean paused = playing && handler.getPlayer().isPaused();
        int queueSize = handler.getQueue().size();
        currentVolume = handler.getPlayer().getVolume();

        if (playing && track != null) {
            String author = track.getInfo().author == null ? "Unknown" : track.getInfo().author;
            String fullTrackText = track.getInfo().title + " - " + author;
            currentTrackValue.setText(ellipsize(fullTrackText, 64));
            currentTrackValue.setToolTipText(fullTrackText);
            if (paused) {
                setPlayerStateBadge("Paused", new Color(255, 244, 214), new Color(140, 96, 0));
            } else {
                setPlayerStateBadge("Playing", new Color(226, 245, 234), new Color(28, 108, 58));
            }
        } else {
            currentTrackValue.setText("Nothing playing");
            currentTrackValue.setToolTipText(null);
            setPlayerStateBadge("Idle", new Color(238, 238, 238), new Color(90, 90, 90));
        }

        queueSizeValue.setText(String.valueOf(queueSize));
        updateVolumeUi(currentVolume);
        setControlState(paused, playing && !paused, playing || queueSize > 0, playing || queueSize > 0, true);
        refreshQueuePanel(handler);
    }

    private void refreshQueuePanel(AudioHandler handler) {
        queueTableModel.setRowCount(0);
        if (handler == null) {
            queuePanelTitleValue.setText("Queue (0)");
            queueTableModel.addRow(new Object[]{"No queue for this server.", "", "", ""});
            return;
        }

        int size = handler.getQueue().size();
        queuePanelTitleValue.setText("Queue (" + size + ")");
        if (size == 0) {
            queueTableModel.addRow(new Object[]{"Queue is empty.", "", "", ""});
            return;
        }

        int index = 1;
        for (QueuedTrack queuedTrack : handler.getQueue().getList()) {
            AudioTrack track = queuedTrack.getTrack();
            String title = track != null && track.getInfo() != null && track.getInfo().title != null
                    ? track.getInfo().title
                    : "Unknown track";
            queueTableModel.addRow(new Object[]{index + ". " + ellipsize(title, 72), "^", "v", "x"});
            index++;
            if (index > 50) {
                queueTableModel.addRow(new Object[]{"... and more", "", "", ""});
                break;
            }
        }
    }

    private void handleQueueAction(int row, int col) {
        if (bot == null || bot.getJDA() == null) {
            setActionFeedback("Bot is not ready yet", false);
            return;
        }
        Guild guild = getSelectedGuild();
        if (guild == null) {
            setActionFeedback("No server selected", false);
            return;
        }
        AudioHandler handler = getAudioHandler(guild);
        if (handler == null || row >= handler.getQueue().size()) {
            setActionFeedback("No queue item selected", false);
            return;
        }

        try {
            if (col == 1) {
                if (row <= 0) {
                    setActionFeedback("Track is already at the top", false);
                    return;
                }
                handler.getQueue().moveItem(row, row - 1);
                setActionFeedback("Moved track up", true);
            } else if (col == 2) {
                if (row >= handler.getQueue().size() - 1) {
                    setActionFeedback("Track is already at the bottom", false);
                    return;
                }
                handler.getQueue().moveItem(row, row + 1);
                setActionFeedback("Moved track down", true);
            } else if (col == 3) {
                handler.getQueue().remove(row);
                setActionFeedback("Removed track from queue", true);
            }
        } catch (Exception ex) {
            setActionFeedback("Queue action failed: " + ex.getMessage(), false);
        }

        refreshPlayerController();
    }

    private void updateVolumeUi(int volume) {
        updatingVolumeFromStatus = true;
        volumeSlider.setValue(volume);
        playerVolumeValue.setText(volume + "%");
        updatingVolumeFromStatus = false;
    }

    private void setControlState(boolean canPlay, boolean canPause, boolean canSkip, boolean canStop, boolean canAdjustVolume) {
        playButton.setEnabled(canPlay);
        pauseButton.setEnabled(canPause);
        skipButton.setEnabled(canSkip);
        stopButton.setEnabled(canStop);
        volumeSlider.setEnabled(canAdjustVolume);
        addTrackButton.setEnabled(canAdjustVolume);
        playUrlField.setEnabled(canAdjustVolume);
    }

    private void refreshGuildSelector() {
        if (bot == null || bot.getJDA() == null) {
            updatingGuildSelector = true;
            guildSelector.removeAllItems();
            updatingGuildSelector = false;
            guildSelectorSnapshot = "";
            return;
        }

        String snapshot = bot.getJDA().getGuilds().stream()
                .map(guild -> guild.getId() + ":" + guild.getName())
                .collect(Collectors.joining("|"));
        if (snapshot.equals(guildSelectorSnapshot)) {
            return;
        }

        String previousGuildId = null;
        GuildSelectionItem selected = (GuildSelectionItem) guildSelector.getSelectedItem();
        if (selected != null) {
            previousGuildId = selected.id;
        }

        updatingGuildSelector = true;
        guildSelector.removeAllItems();
        for (Guild guild : bot.getJDA().getGuilds()) {
            guildSelector.addItem(new GuildSelectionItem(guild.getId(), guild.getName()));
        }

        if (guildSelector.getItemCount() > 0) {
            int selectedIndex = 0;
            if (previousGuildId != null) {
                for (int i = 0; i < guildSelector.getItemCount(); i++) {
                    GuildSelectionItem item = guildSelector.getItemAt(i);
                    if (item.id.equals(previousGuildId)) {
                        selectedIndex = i;
                        break;
                    }
                }
            }
            guildSelector.setSelectedIndex(selectedIndex);
            guildSelector.setEnabled(true);
        } else {
            guildSelector.setEnabled(false);
        }
        updatingGuildSelector = false;
        guildSelectorSnapshot = snapshot;
    }

    private Guild getSelectedGuild() {
        if (bot == null || bot.getJDA() == null) {
            return null;
        }
        GuildSelectionItem selected = (GuildSelectionItem) guildSelector.getSelectedItem();
        if (selected == null) {
            return null;
        }
        return bot.getJDA().getGuildById(selected.id);
    }

    private AudioHandler getAudioHandler(Guild guild) {
        if (guild == null) {
            return null;
        }
        if (guild.getAudioManager().getSendingHandler() instanceof AudioHandler handler) {
            return handler;
        }
        return null;
    }

    private void setPlayerStateBadge(String text, Color bg, Color fg) {
        playerStateValue.setText(text);
        playerStateValue.setBackground(bg);
        playerStateValue.setForeground(fg);
    }

    private void setActionFeedback(String text, boolean success) {
        actionFeedbackValue.setText(text);
        if (success) {
            actionFeedbackValue.setBackground(new Color(226, 245, 234));
            actionFeedbackValue.setForeground(new Color(28, 108, 58));
        } else {
            actionFeedbackValue.setBackground(new Color(252, 228, 228));
            actionFeedbackValue.setForeground(new Color(138, 29, 29));
        }
    }

    private static String ellipsize(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private boolean isGuildVoiceConnected(Guild guild) {
        if (guild == null) {
            return false;
        }
        return guild.getSelfMember() != null
                && guild.getSelfMember().getVoiceState() != null
                && guild.getSelfMember().getVoiceState().inAudioChannel();
    }

    private String getGuildVoiceStateText(Guild guild) {
        if (guild == null) {
            return "No server";
        }
        return isGuildVoiceConnected(guild) ? "Voice connected" : "Voice idle";
    }

    private interface GuildAction {
        boolean run(Guild guild);
    }

    private class GuildSelectionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof GuildSelectionItem item && bot != null && bot.getJDA() != null) {
                Guild guild = bot.getJDA().getGuildById(item.id);
                String prefix = (guild != null && isGuildVoiceConnected(guild)) ? "[LIVE] " : "[IDLE] ";
                setText(prefix + item.name);
            }
            return this;
        }
    }

    private static class GuildSelectionItem {
        private final String id;
        private final String name;

        private GuildSelectionItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private void refreshStatus() {
        long started = System.nanoTime();
        try {
            if (bot == null) {
                botStatusValue.setText("Initializing");
                guildCountValue.setText("0");
                pingValue.setText("-");
                setStatusBadge("Starting", new Color(255, 242, 204), new Color(130, 99, 40));
                listTargetsLoadedAfterConnect = false;
                return;
            }

            JDA jda = bot.getJDA();

            if (jda == null) {
                botStatusValue.setText("Starting");
                guildCountValue.setText("0");
                pingValue.setText("-");
                setStatusBadge("Starting", new Color(255, 242, 204), new Color(130, 99, 40));
                cachedJdaStatus = "Starting";
                cachedGuildCount = "0";
                cachedPing = "-";
                cachedConnected = false;
                listTargetsLoadedAfterConnect = false;
            } else {
                requestJdaSnapshotIfNeeded(jda);
                botStatusValue.setText(cachedJdaStatus);
                guildCountValue.setText(cachedGuildCount);
                pingValue.setText(cachedPing);
                if (cachedConnected) {
                    setStatusBadge("Connected", new Color(218, 242, 220), new Color(36, 107, 52));
                    if (!listTargetsLoadedAfterConnect && playlistManager != null) {
                        playlistManager.onBotConnected();
                        listTargetsLoadedAfterConnect = true;
                    }
                } else {
                    setStatusBadge("Waiting", new Color(255, 242, 204), new Color(130, 99, 40));
                    listTargetsLoadedAfterConnect = false;
                }
            }

            Duration uptime = Duration.between(startedAt, Instant.now());
            long hours = uptime.toHours();
            long minutes = uptime.toMinutesPart();
            long seconds = uptime.toSecondsPart();
            uptimeValue.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));

            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long maxMb = runtime.maxMemory() / (1024 * 1024);
            memoryValue.setText(usedMb + " / " + maxMb + " MB");

            logLineValue.setText(String.valueOf(console.getLogLineCount()));
            refreshExternalToolStatusIfNeeded();
            maybeRefreshController();
        } catch (Throwable t) {
            setActionFeedback("GUI refresh warning: " + t.getClass().getSimpleName(), false);
            log.warn("GUI refresh failed", t);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            if (elapsedMs > GUI_SLOW_REFRESH_WARN_MS) {
                log.warn("Slow GUI refresh detected: {} ms", elapsedMs);
            }
        }
    }

    private void requestJdaSnapshotIfNeeded(JDA jda) {
        long now = System.currentTimeMillis();
        if (jdaSnapshotInFlight || now - lastJdaSnapshotRequestAt < JDA_SNAPSHOT_INTERVAL_MS) {
            return;
        }
        jdaSnapshotInFlight = true;
        lastJdaSnapshotRequestAt = now;

        CompletableFuture.supplyAsync(() -> {
            JdaSnapshot snapshot = new JdaSnapshot();
            try {
                snapshot.status = jda.getStatus().name();
                snapshot.guildCount = String.valueOf(jda.getGuilds().size());
                snapshot.ping = jda.getGatewayPing() + " ms";
                snapshot.connected = jda.getStatus() == JDA.Status.CONNECTED;
            } catch (Throwable t) {
                snapshot.status = "Waiting";
                snapshot.guildCount = cachedGuildCount;
                snapshot.ping = cachedPing;
                snapshot.connected = false;
            }
            return snapshot;
        }).whenComplete((snapshot, error) -> SwingUtilities.invokeLater(() -> {
            try {
                if (error == null && snapshot != null) {
                    cachedJdaStatus = snapshot.status;
                    cachedGuildCount = snapshot.guildCount;
                    cachedPing = snapshot.ping;
                    cachedConnected = snapshot.connected;
                }
            } finally {
                jdaSnapshotInFlight = false;
            }
        }));
    }

    private static final class JdaSnapshot {
        private String status;
        private String guildCount;
        private String ping;
        private boolean connected;
    }

    private void maybeRefreshController() {
        long now = System.currentTimeMillis();
        if (now - lastControllerRefreshAt < CONTROLLER_REFRESH_INTERVAL_MS) {
            return;
        }
        lastControllerRefreshAt = now;
        refreshPlayerController();
    }

    private void setStatusBadge(String text, Color background, Color foreground) {
        statusBadge.setText(text);
        statusBadge.setBackground(background);
        statusBadge.setForeground(foreground);
    }

    private void refreshExternalToolStatusIfNeeded() {
        if (bot == null || bot.getPlayerManager() == null) {
            ffmpegValue.setText("Unknown");
            ytDlpVersionValue.setText("Unknown");
            return;
        }

        Instant now = Instant.now();
        if (Duration.between(lastExternalToolsRefreshedAt, now).compareTo(Duration.ofSeconds(30)) < 0) {
            return;
        }
        if (externalToolsSnapshotInFlight) {
            return;
        }
        lastExternalToolsRefreshedAt = now;
        externalToolsSnapshotInFlight = true;

        boolean ffmpegInstalled = bot.getPlayerManager().isFfmpegAvailable();
        ffmpegValue.setText(ffmpegInstalled ? "Installed" : "Not detected");

        CompletableFuture.supplyAsync(() -> bot.getPlayerManager().getYtDlpVersion())
                .whenComplete((version, error) -> SwingUtilities.invokeLater(() -> {
                    try {
                        if (error == null) {
                            ytDlpVersionValue.setText(version == null ? "Not detected" : version);
                        }
                    } finally {
                        externalToolsSnapshotInFlight = false;
                    }
                }));
    }

    private void shutdownBotSafely() {
        if (bot == null) {
            System.exit(0);
            return;
        }

        try {
            BotConfig config = bot.getConfig();
            if (config != null && config.isWebPanelEnabled()) {
                new Thread(() -> {
                    try {
                        com.jagrosh.jmusicbot.webpanel.WebPanelApplication.stop();
                        Thread.sleep(500);
                        bot.shutdown();
                    } catch (Exception ex) {
                        System.err.println("Error during shutdown: " + ex.getMessage());
                        System.exit(0);
                    }
                }).start();
                return;
            }
            bot.shutdown();
        } catch (Exception ex) {
            System.err.println("Error during shutdown: " + ex.getMessage());
            System.exit(0);
        }
    }
}
