/*
 * Copyright 2018 John Grosh (jagrosh).
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
package dev.cosgy.jmusicbot.slashcommands;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public abstract class MusicCommand extends SlashCommand {
    protected final Bot bot;
    protected boolean bePlaying;
    protected boolean beListening;
    Logger log = LoggerFactory.getLogger("MusicCommand");

    public MusicCommand(Bot bot) {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Music");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        bot.getPlayerManager().setUpHandler(event.getGuild());
        if (bePlaying && !((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).isMusicPlaying(event.getJDA())) {
            event.reply(event.getClient().getError() + "To use this command, music must be playing.").queue();
            return;
        }
        if (beListening && !ensureSlashListeningState(event, settings)) {
            return;
        }

        doCommand(event);
    }

    @Override
    protected void execute(CommandEvent event) {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        if (!ensureAllowedTextChannel(event, settings)) {
            return;
        }

        bot.getPlayerManager().setUpHandler(event.getGuild());

        if (bePlaying && !((AudioHandler) event.getGuild().getAudioManager().getSendingHandler()).isMusicPlaying(event.getJDA())) {
            event.reply(event.getClient().getError() + "To use this command, music must be playing.");
            return;
        }
        if (beListening && !ensureTextListeningState(event, settings)) {
            return;
        }

        doCommand(event);
    }

    private boolean ensureAllowedTextChannel(CommandEvent event, Settings settings) {
        TextChannel channel = settings.getTextChannel(event.getGuild());
        if (channel == null || event.getTextChannel().equals(channel)) {
            return true;
        }

        try {
            event.getMessage().delete().queue();
        } catch (PermissionException ignore) {
        }
        event.replyInDm(event.getClient().getError() + String.format("Commands can only be executed in %s", channel.getAsMention()));
        return false;
    }

    private boolean ensureSlashListeningState(SlashCommandEvent event, Settings settings) {
        Member selfMember = DiscordCompat.getSelfMember(event.getGuild());
        AudioChannelUnion current = resolveCurrentChannel(settings, selfMember, event.getGuild());
        GuildVoiceState userState = event.getMember() != null ? event.getMember().getVoiceState() : null;

        if (isListeningRequirementFailed(userState, current)) {
            event.reply(event.getClient().getError() + String.format("To use this command, you need to be in %s!", (current == null ? "a voice channel" : "**" + current.getAsMention() + "**"))).queue();
            return false;
        }

        if (isBotAlreadyInVoice(selfMember)) {
            return true;
        }

        try {
            event.getGuild().getAudioManager().openAudioConnection(userState.getChannel());
            event.getGuild().getAudioManager().setSelfMuted(false);
        } catch (PermissionException ex) {
            event.reply(event.getClient().getError() + String.format("Cannot connect to **%s**!", userState.getChannel().getAsMention())).queue();
            return false;
        }

        notifyStageChannelJoin(event, selfMember, userState);
        return true;
    }

    private boolean ensureTextListeningState(CommandEvent event, Settings settings) {
        Member selfMember = DiscordCompat.getSelfMember(event.getGuild());
        AudioChannelUnion current = resolveCurrentChannel(settings, selfMember, event.getGuild());
        GuildVoiceState userState = event.getMember() != null ? event.getMember().getVoiceState() : null;

        if (isListeningRequirementFailed(userState, current)) {
            event.replyError(String.format("To use this command, you need to be in %s!", (current == null ? "a voice channel" : "**" + current.getName() + "**")));
            return false;
        }

        if (isBotAlreadyInVoice(selfMember)) {
            return true;
        }

        try {
            event.getGuild().getAudioManager().openAudioConnection(userState.getChannel());
        } catch (PermissionException ex) {
            event.reply(event.getClient().getError() + String.format("Cannot connect to **%s**!", userState.getChannel().getName()));
            return false;
        }

        notifyStageChannelJoin(event, selfMember, userState);
        return true;
    }

    private AudioChannelUnion resolveCurrentChannel(Settings settings, Member selfMember, Guild guild) {
        AudioChannelUnion current = selfMember != null && selfMember.getVoiceState() != null
                ? selfMember.getVoiceState().getChannel()
                : null;
        if (current == null) {
            current = (AudioChannelUnion) settings.getVoiceChannel(guild);
        }
        return current;
    }

    private boolean isListeningRequirementFailed(GuildVoiceState userState, AudioChannelUnion current) {
        return userState == null
                || !userState.inAudioChannel()
                || userState.isDeafened()
                || (current != null && !userState.getChannel().equals(current));
    }

    private boolean isBotAlreadyInVoice(Member selfMember) {
        return selfMember != null
                && selfMember.getVoiceState() != null
                && selfMember.getVoiceState().inAudioChannel();
    }

    private void notifyStageChannelJoin(CommandEvent event, Member selfMember, GuildVoiceState userState) {
        if (userState.getChannel().getType() != ChannelType.STAGE) {
            return;
        }
        String nickname = selfMember != null ? selfMember.getNickname() : null;
        event.getTextChannel().sendMessage(event.getClient().getWarning() + String.format("Joined a stage channel. You need to manually invite as a speaker to use %s in a stage channel.", nickname == null ? event.getJDA().getSelfUser().getName() : nickname)).queue();
    }

    private void notifyStageChannelJoin(SlashCommandEvent event, Member selfMember, GuildVoiceState userState) {
        if (userState.getChannel().getType() != ChannelType.STAGE) {
            return;
        }
        String nickname = selfMember != null ? selfMember.getNickname() : null;
        event.getTextChannel().sendMessage(event.getClient().getWarning() + String.format("Joined a stage channel. You need to manually invite as a speaker to use %s in a stage channel.", nickname == null ? event.getJDA().getSelfUser().getName() : nickname)).queue();
    }

    public abstract void doCommand(CommandEvent event);

    public abstract void doCommand(SlashCommandEvent event);
}