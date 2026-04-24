package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.FilterChainConfig;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class FiltersCmd extends MusicCommand {

    private static final long TIMEOUT_SECONDS = 180;

    public FiltersCmd(Bot bot) {
        super(bot);
        this.name = "filters";
        this.aliases = new String[]{"filter", "fx"};
        this.help = "Manage audio DSP filters";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = false;
        this.beListening = false;
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            event.reply("No audio handler available. Play something first.").setEphemeral(true).queue();
            return;
        }

        String baseId = "filters:" + event.getUser().getId() + ":" + System.currentTimeMillis();

        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setEmbeds(buildEmbed(handler));
        List<ActionRow> rows = buildButtonRows(baseId, handler.getFilterChain());
        mb.setComponents(rows.toArray(ActionRow[]::new));

        event.reply(mb.build()).queue(hook -> {
            hook.retrieveOriginal().queue(message -> {
                waitForInteraction(message, baseId, handler, event.getUser().getId());
            });
        });
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            event.reply("No audio handler available. Play something first.");
            return;
        }

        String baseId = "filters:" + event.getAuthor().getId() + ":" + System.currentTimeMillis();

        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setEmbeds(buildEmbed(handler));
        List<ActionRow> rowsForPrefix = buildButtonRows(baseId, handler.getFilterChain());
        mb.setComponents(rowsForPrefix.toArray(ActionRow[]::new));

        event.getChannel().sendMessage(mb.build()).queue(message -> {
            waitForInteraction(message, baseId, handler, event.getAuthor().getId());
        });
    }

    private void waitForInteraction(Message message, String baseId, AudioHandler handler, String userId) {
        // Wait for button interaction
        bot.getWaiter().waitForEvent(
                ButtonInteractionEvent.class,
                ev -> ev.getMessageIdLong() == message.getIdLong()
                        && ev.getComponentId().startsWith(baseId)
                        && ev.getUser().getId().equals(userId),
                ev -> handleButton(ev, message, baseId, handler, userId),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
                () -> message.editMessageComponents().queue(s -> {}, f -> {})
        );
    }

    private void handleButton(ButtonInteractionEvent ev, Message message, String baseId, AudioHandler handler, String userId) {
        try {
            String componentId = ev.getComponentId();
            String action = componentId.substring(baseId.length() + 1); // skip "baseId:"
            FilterChainConfig fc = handler.getFilterChain();

            switch (action) {
                case "timescale" -> toggleFilter(fc.getTimescale(), handler);
                case "tremolo" -> toggleFilter(fc.getTremolo(), handler);
                case "vibrato" -> toggleFilter(fc.getVibrato(), handler);
                case "karaoke" -> toggleFilter(fc.getKaraoke(), handler);
                case "rotation" -> toggleFilter(fc.getRotation(), handler);
                case "distortion" -> toggleFilter(fc.getDistortion(), handler);
                case "channelmix" -> toggleFilter(fc.getChannelMix(), handler);
                case "lowpass" -> toggleFilter(fc.getLowPass(), handler);
                case "reverb" -> toggleFilter(fc.getReverb(), handler);
                case "equalizer" -> toggleFilter(fc.getEqualizer(), handler);
                case "reset" -> {
                    fc.resetAll();
                    handler.applyFilters();
                }
                case "edit" -> {
                    // Show select menu for choosing which filter to edit
                    ev.deferEdit().queue();
                    List<ActionRow> rows = buildEditSelectRow(baseId, fc);
                    message.editMessageComponents(rows.toArray(ActionRow[]::new)).queue(
                            s -> waitForSelectMenu(message, baseId, handler, userId),
                            f -> waitForInteraction(message, baseId, handler, userId)
                    );
                    return;
                }
            }

            // Update message with new state
            List<ActionRow> rows = buildButtonRows(baseId, fc);
            ev.editMessageEmbeds(buildEmbed(handler))
                    .setComponents(rows.toArray(ActionRow[]::new))
                    .queue(
                            s -> waitForInteraction(message, baseId, handler, userId),
                            f -> waitForInteraction(message, baseId, handler, userId)
                    );
        } catch (Exception ex) {
            ev.reply("An error occurred while processing the filter action.").setEphemeral(true).queue(s -> {}, f -> {});
            waitForInteraction(message, baseId, handler, userId);
        }
    }

    private void waitForSelectMenu(Message message, String baseId, AudioHandler handler, String userId) {
        bot.getWaiter().waitForEvent(
                StringSelectInteractionEvent.class,
                ev -> ev.getMessageIdLong() == message.getIdLong()
                        && ev.getComponentId().equals(baseId + ":select")
                        && ev.getUser().getId().equals(userId),
                ev -> handleSelectMenu(ev, message, baseId, handler, userId),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
                () -> message.editMessageComponents().queue(s -> {}, f -> {})
        );
        // Also keep listening for buttons (back / toggle buttons)
        bot.getWaiter().waitForEvent(
                ButtonInteractionEvent.class,
                ev -> ev.getMessageIdLong() == message.getIdLong()
                        && ev.getComponentId().startsWith(baseId)
                        && ev.getUser().getId().equals(userId),
                ev -> handleButton(ev, message, baseId, handler, userId),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
                () -> {}
        );
    }

    private void handleSelectMenu(StringSelectInteractionEvent ev, Message message, String baseId, AudioHandler handler, String userId) {
        String selected = ev.getValues().get(0);
        Modal modal = buildModal(baseId, selected, handler.getFilterChain());
        if (modal == null) {
            ev.deferEdit().queue();
            waitForInteraction(message, baseId, handler, userId);
            return;
        }
        ev.replyModal(modal).queue();

        // Wait for modal submit
        bot.getWaiter().waitForEvent(
                ModalInteractionEvent.class,
                mev -> mev.getModalId().equals(baseId + ":modal:" + selected)
                        && mev.getUser().getId().equals(userId),
                mev -> handleModal(mev, message, baseId, selected, handler, userId),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
                () -> {}
        );
    }

    private void handleModal(ModalInteractionEvent ev, Message message, String baseId, String filterName,
                             AudioHandler handler, String userId) {
        FilterChainConfig fc = handler.getFilterChain();
        try {
            applyModalValues(ev, filterName, fc);
            handler.applyFilters();
            ev.deferEdit().queue();
            List<ActionRow> modalRows = buildButtonRows(baseId, fc);
            message.editMessageEmbeds(buildEmbed(handler))
                    .setComponents(modalRows.toArray(ActionRow[]::new))
                    .queue(s -> waitForInteraction(message, baseId, handler, userId),
                            f -> waitForInteraction(message, baseId, handler, userId));
        } catch (NumberFormatException ex) {
            ev.reply("Invalid number format. Please enter valid numeric values.").setEphemeral(true).queue();
            waitForInteraction(message, baseId, handler, userId);
        }
    }

    private void applyModalValues(ModalInteractionEvent ev, String filterName, FilterChainConfig fc) {
        switch (filterName) {
            case "timescale" -> {
                FilterChainConfig.TimescaleConfig c = fc.getTimescale();
                c.setEnabled(true);
                ModalMapping speed = ev.getValue("speed");
                ModalMapping pitch = ev.getValue("pitch");
                ModalMapping rate = ev.getValue("rate");
                if (speed != null) c.setSpeed(Double.parseDouble(speed.getAsString()));
                if (pitch != null) c.setPitch(Double.parseDouble(pitch.getAsString()));
                if (rate != null) c.setRate(Double.parseDouble(rate.getAsString()));
            }
            case "tremolo" -> {
                FilterChainConfig.TremoloConfig c = fc.getTremolo();
                c.setEnabled(true);
                ModalMapping freq = ev.getValue("frequency");
                ModalMapping depth = ev.getValue("depth");
                if (freq != null) c.setFrequency(Float.parseFloat(freq.getAsString()));
                if (depth != null) c.setDepth(Float.parseFloat(depth.getAsString()));
            }
            case "vibrato" -> {
                FilterChainConfig.VibratoConfig c = fc.getVibrato();
                c.setEnabled(true);
                ModalMapping freq = ev.getValue("frequency");
                ModalMapping depth = ev.getValue("depth");
                if (freq != null) c.setFrequency(Float.parseFloat(freq.getAsString()));
                if (depth != null) c.setDepth(Float.parseFloat(depth.getAsString()));
            }
            case "karaoke" -> {
                FilterChainConfig.KaraokeConfig c = fc.getKaraoke();
                c.setEnabled(true);
                ModalMapping level = ev.getValue("level");
                ModalMapping mono = ev.getValue("monoLevel");
                ModalMapping band = ev.getValue("filterBand");
                ModalMapping width = ev.getValue("filterWidth");
                if (level != null) c.setLevel(Float.parseFloat(level.getAsString()));
                if (mono != null) c.setMonoLevel(Float.parseFloat(mono.getAsString()));
                if (band != null) c.setFilterBand(Float.parseFloat(band.getAsString()));
                if (width != null) c.setFilterWidth(Float.parseFloat(width.getAsString()));
            }
            case "rotation" -> {
                FilterChainConfig.RotationConfig c = fc.getRotation();
                c.setEnabled(true);
                ModalMapping hz = ev.getValue("rotationHz");
                if (hz != null) c.setRotationHz(Double.parseDouble(hz.getAsString()));
            }
            case "distortion" -> {
                FilterChainConfig.DistortionConfig c = fc.getDistortion();
                c.setEnabled(true);
                ModalMapping v;
                if ((v = ev.getValue("sinOffset")) != null) c.setSinOffset(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("sinScale")) != null) c.setSinScale(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("offset")) != null) c.setOffset(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("scale")) != null) c.setScale(Float.parseFloat(v.getAsString()));
            }
            case "channelmix" -> {
                FilterChainConfig.ChannelMixConfig c = fc.getChannelMix();
                c.setEnabled(true);
                ModalMapping v;
                if ((v = ev.getValue("leftToLeft")) != null) c.setLeftToLeft(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("leftToRight")) != null) c.setLeftToRight(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("rightToLeft")) != null) c.setRightToLeft(Float.parseFloat(v.getAsString()));
                if ((v = ev.getValue("rightToRight")) != null) c.setRightToRight(Float.parseFloat(v.getAsString()));
            }
            case "lowpass" -> {
                FilterChainConfig.LowPassConfig c = fc.getLowPass();
                c.setEnabled(true);
                ModalMapping smoothing = ev.getValue("smoothing");
                if (smoothing != null) c.setSmoothing(Float.parseFloat(smoothing.getAsString()));
            }
            case "reverb" -> {
                FilterChainConfig.ReverbConfig c = fc.getReverb();
                c.setEnabled(true);
                ModalMapping room = ev.getValue("roomSize");
                ModalMapping damp = ev.getValue("damping");
                ModalMapping wet = ev.getValue("wetLevel");
                if (room != null) c.setRoomSize(Float.parseFloat(room.getAsString()));
                if (damp != null) c.setDamping(Float.parseFloat(damp.getAsString()));
                if (wet != null) c.setWetLevel(Float.parseFloat(wet.getAsString()));
            }
            case "equalizer" -> {
                FilterChainConfig.EqualizerConfig c = fc.getEqualizer();
                c.setEnabled(true);
                ModalMapping v;
                if ((v = ev.getValue("subBass")) != null) {
                    float gain = Float.parseFloat(v.getAsString());
                    for (int i = 0; i <= 2; i++) c.setGain(i, gain); // 25, 40, 63 Hz
                }
                if ((v = ev.getValue("bass")) != null) {
                    float gain = Float.parseFloat(v.getAsString());
                    for (int i = 3; i <= 5; i++) c.setGain(i, gain); // 100, 160, 250 Hz
                }
                if ((v = ev.getValue("mid")) != null) {
                    float gain = Float.parseFloat(v.getAsString());
                    for (int i = 6; i <= 8; i++) c.setGain(i, gain); // 400, 630, 1k Hz
                }
                if ((v = ev.getValue("highMid")) != null) {
                    float gain = Float.parseFloat(v.getAsString());
                    for (int i = 9; i <= 11; i++) c.setGain(i, gain); // 1.6k, 2.5k, 4k Hz
                }
                if ((v = ev.getValue("treble")) != null) {
                    float gain = Float.parseFloat(v.getAsString());
                    for (int i = 12; i <= 14; i++) c.setGain(i, gain); // 6.3k, 10k, 16k Hz
                }
            }
        }
    }

    // ========================= Toggle Helper =========================

    private void toggleFilter(FilterChainConfig.TimescaleConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.TremoloConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.VibratoConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.KaraokeConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.RotationConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.DistortionConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.ChannelMixConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.LowPassConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.ReverbConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }
    private void toggleFilter(FilterChainConfig.EqualizerConfig c, AudioHandler h) {
        c.setEnabled(!c.isEnabled());
        h.applyFilters();
    }

    // ========================= Embed Builder =========================

    private MessageEmbed buildEmbed(AudioHandler handler) {
        FilterChainConfig fc = handler.getFilterChain();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("\uD83C\uDF9B\uFE0F Audio Filters");
        eb.setColor(fc.isAnyEnabled() ? new Color(88, 101, 242) : Color.GRAY);

        // Stream warning
        var track = handler.getPlayer().getPlayingTrack();
        if (track != null && track.getInfo().isStream && fc.getTimescale().isEnabled()) {
            eb.setFooter("⚠ Timescale speed/rate changes may cause issues on live streams. Pitch is safe.");
        }

        eb.addField("⏱ Timescale", formatTimescale(fc.getTimescale()), true);
        eb.addField("\uD83E\uDD41 Tremolo", formatTremolo(fc.getTremolo()), true);
        eb.addField("\uD83C\uDF00 Vibrato", formatVibrato(fc.getVibrato()), true);
        eb.addField("\uD83C\uDFA4 Karaoke", formatKaraoke(fc.getKaraoke()), true);
        eb.addField("\uD83D\uDD04 Rotation", formatRotation(fc.getRotation()), true);
        eb.addField("⚡ Distortion", formatDistortion(fc.getDistortion()), true);
        eb.addField("\uD83C\uDF9A Channel Mix", formatChannelMix(fc.getChannelMix()), true);
        eb.addField("\uD83D\uDD09 Low Pass", formatLowPass(fc.getLowPass()), true);
        eb.addField("\uD83C\uDFB6 Reverb", formatReverb(fc.getReverb()), true);
        eb.addField("\uD83C\uDF9B Equalizer", formatEqualizer(fc.getEqualizer()), true);

        return eb.build();
    }

    private String formatTimescale(FilterChainConfig.TimescaleConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nSpeed: " + c.getSpeed() + "x" +
                "\nPitch: " + c.getPitch() +
                "\nRate: " + c.getRate();
    }
    private String formatTremolo(FilterChainConfig.TremoloConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nFreq: " + c.getFrequency() + " Hz" +
                "\nDepth: " + c.getDepth();
    }
    private String formatVibrato(FilterChainConfig.VibratoConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nFreq: " + c.getFrequency() + " Hz" +
                "\nDepth: " + c.getDepth();
    }
    private String formatKaraoke(FilterChainConfig.KaraokeConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nLevel: " + c.getLevel() +
                "\nMono: " + c.getMonoLevel() +
                "\nBand: " + c.getFilterBand() + " Hz";
    }
    private String formatRotation(FilterChainConfig.RotationConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nSpeed: " + c.getRotationHz() + " Hz";
    }
    private String formatDistortion(FilterChainConfig.DistortionConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nSin: " + c.getSinOffset() + "/" + c.getSinScale() +
                "\nOffset: " + c.getOffset() + " Scale: " + c.getScale();
    }
    private String formatChannelMix(FilterChainConfig.ChannelMixConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nL→L: " + c.getLeftToLeft() + " L→R: " + c.getLeftToRight() +
                "\nR→L: " + c.getRightToLeft() + " R→R: " + c.getRightToRight();
    }
    private String formatLowPass(FilterChainConfig.LowPassConfig c) {
        return (c.isEnabled() ? "✅ Enabled" : "❌ Disabled") +
                "\nSmoothing: " + c.getSmoothing();
    }    private String formatReverb(FilterChainConfig.ReverbConfig c) {
        return (c.isEnabled() ? "\u2705 Enabled" : "\u274C Disabled") +
                "\nRoom: " + c.getRoomSize() +
                "\nDamping: " + c.getDamping() +
                "\nWet: " + c.getWetLevel();
    }
    private String formatEqualizer(FilterChainConfig.EqualizerConfig c) {
        if (!c.isEnabled()) return "\u274C Disabled";
        StringBuilder sb = new StringBuilder("\u2705 Enabled");
        float[] gains = c.getBandGains();
        // Show simplified band group summary
        float subBass = (gains[0] + gains[1] + gains[2]) / 3f;
        float bass = (gains[3] + gains[4] + gains[5]) / 3f;
        float mid = (gains[6] + gains[7] + gains[8]) / 3f;
        float highMid = (gains[9] + gains[10] + gains[11]) / 3f;
        float treble = (gains[12] + gains[13] + gains[14]) / 3f;
        sb.append(String.format("\nSub:%.2f Bass:%.2f", subBass, bass));
        sb.append(String.format("\nMid:%.2f Hi:%.2f Tr:%.2f", mid, highMid, treble));
        return sb.toString();
    }
    // ========================= Button Rows =========================

    private List<ActionRow> buildButtonRows(String baseId, FilterChainConfig fc) {
        List<ActionRow> rows = new ArrayList<>();

        rows.add(ActionRow.of(
                filterButton(baseId, "timescale", "⏱ Timescale", fc.getTimescale().isEnabled()),
                filterButton(baseId, "tremolo", "\uD83E\uDD41 Tremolo", fc.getTremolo().isEnabled()),
                filterButton(baseId, "vibrato", "\uD83C\uDF00 Vibrato", fc.getVibrato().isEnabled()),
                filterButton(baseId, "karaoke", "\uD83C\uDFA4 Karaoke", fc.getKaraoke().isEnabled()),
                filterButton(baseId, "rotation", "\uD83D\uDD04 Rotation", fc.getRotation().isEnabled())
        ));

        rows.add(ActionRow.of(
                filterButton(baseId, "distortion", "⚡ Distortion", fc.getDistortion().isEnabled()),
                filterButton(baseId, "channelmix", "\uD83C\uDF9A ChMix", fc.getChannelMix().isEnabled()),
                filterButton(baseId, "lowpass", "\uD83D\uDD09 LowPass", fc.getLowPass().isEnabled()),                filterButton(baseId, "reverb", "\uD83C\uDFB6 Reverb", fc.getReverb().isEnabled()),
                filterButton(baseId, "equalizer", "\uD83C\uDF9B EQ", fc.getEqualizer().isEnabled())
        ));

        rows.add(ActionRow.of(                Button.of(ButtonStyle.SECONDARY, baseId + ":edit", "✏️ Edit"),
                Button.of(ButtonStyle.DANGER, baseId + ":reset", "\uD83D\uDDD1\uFE0F Reset")
        ));

        return rows;
    }

    private Button filterButton(String baseId, String id, String label, boolean enabled) {
        return Button.of(enabled ? ButtonStyle.SUCCESS : ButtonStyle.SECONDARY, baseId + ":" + id, label);
    }

    // ========================= Edit Select Menu =========================

    private List<ActionRow> buildEditSelectRow(String baseId, FilterChainConfig fc) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(baseId + ":select")
                .setPlaceholder("Select a filter to edit...")
                .setRequiredRange(1, 1);

        menu.addOption("⏱ Timescale", "timescale", "Speed, Pitch, Rate");
        menu.addOption("\uD83E\uDD41 Tremolo", "tremolo", "Frequency, Depth");
        menu.addOption("\uD83C\uDF00 Vibrato", "vibrato", "Frequency, Depth");
        menu.addOption("\uD83C\uDFA4 Karaoke", "karaoke", "Level, Mono, Band, Width");
        menu.addOption("\uD83D\uDD04 Rotation", "rotation", "Rotation speed Hz");
        menu.addOption("⚡ Distortion", "distortion", "Sin, Offset, Scale");
        menu.addOption("\uD83C\uDF9A Channel Mix", "channelmix", "L/R mixing");
        menu.addOption("\uD83D\uDD09 Low Pass", "lowpass", "Smoothing");
        menu.addOption("\uD83C\uDFB6 Reverb", "reverb", "Room Size, Damping, Wet Level");
        menu.addOption("\uD83C\uDF9B Equalizer", "equalizer", "Sub Bass, Bass, Mid, High Mid, Treble");

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(menu.build()));
        // Keep toggle buttons for convenience
        rows.addAll(buildButtonRows(baseId, fc));
        return rows;
    }

    // ========================= Modals =========================

    private Modal buildModal(String baseId, String filterName, FilterChainConfig fc) {
        String modalId = baseId + ":modal:" + filterName;
        return switch (filterName) {
            case "timescale" -> {
                FilterChainConfig.TimescaleConfig c = fc.getTimescale();
                yield Modal.create(modalId, "⏱ Timescale Settings")
                        .addComponents(Label.of("Speed (0.1 - 10.0)", TextInput.create("speed", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getSpeed())).setRequired(true).build()))
                        .addComponents(Label.of("Pitch (0.1 - 10.0)", TextInput.create("pitch", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getPitch())).setRequired(true).build()))
                        .addComponents(Label.of("Rate (0.1 - 10.0)", TextInput.create("rate", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getRate())).setRequired(true).build()))
                        .build();
            }
            case "tremolo" -> {
                FilterChainConfig.TremoloConfig c = fc.getTremolo();
                yield Modal.create(modalId, "\uD83E\uDD41 Tremolo Settings")
                        .addComponents(Label.of("Frequency Hz (0.1 - 100)", TextInput.create("frequency", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getFrequency())).setRequired(true).build()))
                        .addComponents(Label.of("Depth (0.01 - 1.0)", TextInput.create("depth", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getDepth())).setRequired(true).build()))
                        .build();
            }
            case "vibrato" -> {
                FilterChainConfig.VibratoConfig c = fc.getVibrato();
                yield Modal.create(modalId, "\uD83C\uDF00 Vibrato Settings")
                        .addComponents(Label.of("Frequency Hz (0.1 - 14)", TextInput.create("frequency", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getFrequency())).setRequired(true).build()))
                        .addComponents(Label.of("Depth (0.01 - 1.0)", TextInput.create("depth", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getDepth())).setRequired(true).build()))
                        .build();
            }
            case "karaoke" -> {
                FilterChainConfig.KaraokeConfig c = fc.getKaraoke();
                yield Modal.create(modalId, "\uD83C\uDFA4 Karaoke Settings")
                        .addComponents(Label.of("Level (0.0 - 1.0)", TextInput.create("level", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getLevel())).setRequired(true).build()))
                        .addComponents(Label.of("Mono Level (0.0 - 1.0)", TextInput.create("monoLevel", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getMonoLevel())).setRequired(true).build()))
                        .addComponents(Label.of("Filter Band Hz (0 - 1000)", TextInput.create("filterBand", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getFilterBand())).setRequired(true).build()))
                        .addComponents(Label.of("Filter Width Hz (0 - 1000)", TextInput.create("filterWidth", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getFilterWidth())).setRequired(true).build()))
                        .build();
            }
            case "rotation" -> {
                FilterChainConfig.RotationConfig c = fc.getRotation();
                yield Modal.create(modalId, "\uD83D\uDD04 Rotation Settings")
                        .addComponents(Label.of("Rotation Speed Hz (0 - 50)", TextInput.create("rotationHz", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getRotationHz())).setRequired(true).build()))
                        .build();
            }
            case "distortion" -> {
                FilterChainConfig.DistortionConfig c = fc.getDistortion();
                yield Modal.create(modalId, "⚡ Distortion Settings")
                        .addComponents(Label.of("Sin Offset", TextInput.create("sinOffset", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getSinOffset())).setRequired(true).build()))
                        .addComponents(Label.of("Sin Scale", TextInput.create("sinScale", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getSinScale())).setRequired(true).build()))
                        .addComponents(Label.of("Offset", TextInput.create("offset", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getOffset())).setRequired(true).build()))
                        .addComponents(Label.of("Scale", TextInput.create("scale", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getScale())).setRequired(true).build()))
                        .build();
            }
            case "channelmix" -> {
                FilterChainConfig.ChannelMixConfig c = fc.getChannelMix();
                yield Modal.create(modalId, "\uD83C\uDF9A Channel Mix Settings")
                        .addComponents(Label.of("Left → Left (0.0 - 1.0)", TextInput.create("leftToLeft", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getLeftToLeft())).setRequired(true).build()))
                        .addComponents(Label.of("Left → Right (0.0 - 1.0)", TextInput.create("leftToRight", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getLeftToRight())).setRequired(true).build()))
                        .addComponents(Label.of("Right → Left (0.0 - 1.0)", TextInput.create("rightToLeft", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getRightToLeft())).setRequired(true).build()))
                        .addComponents(Label.of("Right → Right (0.0 - 1.0)", TextInput.create("rightToRight", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getRightToRight())).setRequired(true).build()))
                        .build();
            }
            case "lowpass" -> {
                FilterChainConfig.LowPassConfig c = fc.getLowPass();
                yield Modal.create(modalId, "\uD83D\uDD09 Low Pass Settings")
                        .addComponents(Label.of("Smoothing (1.0 - 100.0)", TextInput.create("smoothing", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getSmoothing())).setRequired(true).build()))
                        .build();
            }
            case "reverb" -> {
                FilterChainConfig.ReverbConfig c = fc.getReverb();
                yield Modal.create(modalId, "\uD83C\uDFB6 Reverb Settings")
                        .addComponents(Label.of("Room Size (0.0 - 1.0)", TextInput.create("roomSize", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getRoomSize())).setRequired(true).build()))
                        .addComponents(Label.of("Damping (0.0 - 1.0)", TextInput.create("damping", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getDamping())).setRequired(true).build()))
                        .addComponents(Label.of("Wet Level (0.0 - 1.0)", TextInput.create("wetLevel", TextInputStyle.SHORT)
                                .setValue(String.valueOf(c.getWetLevel())).setRequired(true).build()))
                        .build();
            }
            case "equalizer" -> {
                FilterChainConfig.EqualizerConfig c = fc.getEqualizer();
                float[] g = c.getBandGains();
                float subBassAvg = (g[0] + g[1] + g[2]) / 3f;
                float bassAvg = (g[3] + g[4] + g[5]) / 3f;
                float midAvg = (g[6] + g[7] + g[8]) / 3f;
                float highMidAvg = (g[9] + g[10] + g[11]) / 3f;
                float trebleAvg = (g[12] + g[13] + g[14]) / 3f;
                yield Modal.create(modalId, "\uD83C\uDF9B Equalizer Settings")
                        .addComponents(Label.of("Sub Bass 25-63Hz (-0.25 to 1.0)", TextInput.create("subBass", TextInputStyle.SHORT)
                                .setValue(String.format("%.2f", subBassAvg)).setRequired(true).build()))
                        .addComponents(Label.of("Bass 100-250Hz (-0.25 to 1.0)", TextInput.create("bass", TextInputStyle.SHORT)
                                .setValue(String.format("%.2f", bassAvg)).setRequired(true).build()))
                        .addComponents(Label.of("Mid 400-1kHz (-0.25 to 1.0)", TextInput.create("mid", TextInputStyle.SHORT)
                                .setValue(String.format("%.2f", midAvg)).setRequired(true).build()))
                        .addComponents(Label.of("High Mid 1.6-4kHz (-0.25 to 1.0)", TextInput.create("highMid", TextInputStyle.SHORT)
                                .setValue(String.format("%.2f", highMidAvg)).setRequired(true).build()))
                        .addComponents(Label.of("Treble 6.3-16kHz (-0.25 to 1.0)", TextInput.create("treble", TextInputStyle.SHORT)
                                .setValue(String.format("%.2f", trebleAvg)).setRequired(true).build()))
                        .build();
            }
            default -> null;
        };
    }
}
