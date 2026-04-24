/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.cosgy.jmusicbot.framework.jdautilities.command;

import net.dv8tion.jda.annotations.ForRemoval;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Shared base class for slash command execution.
 * Handles permission checks, cooldowns, and command data generation.
 */
public abstract class SlashCommand extends Command
{
    
    protected Map<DiscordLocale, String> nameLocalization = new HashMap<>();

    
    protected Map<DiscordLocale, String> descriptionLocalization = new HashMap<>();

    
    @Deprecated
    protected String requiredRole = null;

    
    protected SlashCommand[] children = new SlashCommand[0];

    
    protected SubcommandGroupData subcommandGroup = null;

    
    protected List<OptionData> options = new ArrayList<>();

    
    protected CommandClient client;

    
    protected abstract void execute(SlashCommandEvent event);

    
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event) {}

    
    @Override
    protected void execute(CommandEvent event) {}

    
    public final void run(SlashCommandEvent event)
    {
        this.client = event.getClient();
        if (failsOwnerOnlyCheck(event)
                || failsAllowedChannelCheck(event)
                || failsRequiredRoleCheck(event)
                || failsContextAndPermissionChecks(event)
                || failsCooldownCheck(event)) {
            return;
        }

        executeAndNotify(event);
    }

    private boolean failsOwnerOnlyCheck(SlashCommandEvent event)
    {
        if(ownerCommand && !(isOwner(event, client)))
        {
            terminate(event, "Only an owner may run this command. Sorry.", client);
            return true;
        }
        return false;
    }

    private boolean failsAllowedChannelCheck(SlashCommandEvent event)
    {
        try
        {
            if(!isAllowed(event.getTextChannel()))
            {
                terminate(event, "That command cannot be used in this channel!", client);
                return true;
            }
        }
        catch (Exception ignored)
        {
            // Non-text slash command contexts can throw when resolving a text channel.
        }
        return false;
    }

    private boolean failsRequiredRoleCheck(SlashCommandEvent event)
    {
        if(requiredRole==null)
            return false;

        if(!(event.getChannelType() == ChannelType.TEXT)
                || event.getMember()==null
                || event.getMember().getRoles().stream().noneMatch(r -> r.getName().equalsIgnoreCase(requiredRole)))
        {
            terminate(event, client.getError()+" You must have a role called `"+requiredRole+"` to use that!", client);
            return true;
        }
        return false;
    }

    private boolean failsContextAndPermissionChecks(SlashCommandEvent event)
    {
        if(event.getChannelType() == ChannelType.PRIVATE)
        {
            if(guildOnly)
            {
                terminate(event, client.getError()+" This command cannot be used in direct messages", client);
                return true;
            }
            return false;
        }

        if(failsUserPermissions(event) || failsBotPermissions(event))
            return true;

        if(nsfwOnly && event.getChannelType() == ChannelType.TEXT && !event.getTextChannel().isNSFW())
        {
            terminate(event, "This command may only be used in NSFW text channels!", client);
            return true;
        }

        return false;
    }

    private boolean failsUserPermissions(SlashCommandEvent event)
    {
        for(Permission p: userPermissions)
        {
            if(event.getMember() == null)
                continue;

            if(p.isChannel())
            {
                if(!event.getMember().hasPermission(event.getGuildChannel(), p))
                {
                    terminate(event, String.format(userMissingPermMessage, client.getError(), p.getName(), "channel"), client);
                    return true;
                }
            }
            else if(!event.getMember().hasPermission(p))
            {
                terminate(event, String.format(userMissingPermMessage, client.getError(), p.getName(), "server"), client);
                return true;
            }
        }
        return false;
    }

    private boolean failsBotPermissions(SlashCommandEvent event)
    {
        Member selfMember = event.getGuild() != null ? event.getGuild().getSelfMember() : null;
        for (Permission p : botPermissions)
        {
            if (p == Permission.VIEW_CHANNEL || p == Permission.MESSAGE_EMBED_LINKS)
                continue;

            if (p.isChannel())
            {
                GuildVoiceState voiceState = event.getMember() != null ? event.getMember().getVoiceState() : null;
                AudioChannelUnion channel = voiceState != null ? voiceState.getChannel() : null;
                if (channel == null || !channel.getType().isAudio())
                {
                    terminate(event, client.getError() + " You must be in a voice channel to use that!", client);
                    return true;
                }
                if (selfMember == null || !selfMember.hasPermission(channel, p))
                {
                    terminate(event, String.format(botMissingPermMessage, client.getError(), p.getName(), "voice channel"), client);
                    return true;
                }
                continue;
            }

            if (selfMember == null || !selfMember.hasPermission(p))
            {
                terminate(event, String.format(botMissingPermMessage, client.getError(), p.getName(), "server"), client);
                return true;
            }
        }
        return false;
    }

    private boolean failsCooldownCheck(SlashCommandEvent event)
    {
        if(cooldown<=0 || isOwner(event, client))
            return false;

        String key = getCooldownKey(event);
        int remaining = client.getRemainingCooldown(key);
        if(remaining>0)
        {
            terminate(event, getCooldownError(event, remaining, client), client);
            return true;
        }

        client.applyCooldown(key, cooldown);
        return false;
    }

    private void executeAndNotify(SlashCommandEvent event)
    {
        try {
            execute(event);
        } catch(Throwable t) {
            if(client.getListener() != null)
            {
                client.getListener().onSlashCommandException(event, this, t);
                return;
            }
            throw t;
        }

        if(client.getListener() != null)
            client.getListener().onCompletedSlashCommand(event, this);
    }

    
    public boolean isOwner(SlashCommandEvent event, CommandClient client)
    {
        if(event.getUser().getId().equals(client.getOwnerId()))
            return true;
        if(client.getCoOwnerIds()==null)
            return false;
        for(String id : client.getCoOwnerIds())
            if(id.equals(event.getUser().getId()))
                return true;
        return false;
    }

    
    @Deprecated
    @ForRemoval(deadline = "2.0.0")
    public CommandClient getClient()
    {
        return client;
    }

    
    public SubcommandGroupData getSubcommandGroup()
    {
        return subcommandGroup;
    }

    
    public List<OptionData> getOptions()
    {
        return options;
    }

    
    public CommandData buildCommandData()
    {
        SlashCommandData data = Commands.slash(getName(), getHelp());
        addOptions(data, getOptions());
        applyLocalizations(data, getNameLocalization(), getDescriptionLocalization());
        addChildren(data);
        applyDefaultPermissions(data);

        return data;
    }

    private void addOptions(SlashCommandData data, List<OptionData> optionData)
    {
        if (!optionData.isEmpty())
            data.addOptions(optionData);
    }

    private void applyLocalizations(SlashCommandData data, Map<DiscordLocale, String> names, Map<DiscordLocale, String> descriptions)
    {
        if (!names.isEmpty())
            data.setNameLocalizations(names);
        if (!descriptions.isEmpty())
            data.setDescriptionLocalizations(descriptions);
    }

    private void applyLocalizations(SubcommandData data, Map<DiscordLocale, String> names, Map<DiscordLocale, String> descriptions)
    {
        if (!names.isEmpty())
            data.setNameLocalizations(names);
        if (!descriptions.isEmpty())
            data.setDescriptionLocalizations(descriptions);
    }

    private void addChildren(SlashCommandData data)
    {
        if (children.length == 0)
            return;

        Map<String, SubcommandGroupData> groupedSubcommands = new HashMap<>();
        for (SlashCommand child : children)
        {
            SubcommandData childData = toSubcommandData(child);
            SubcommandGroupData group = child.getSubcommandGroup();
            if (group == null)
            {
                data.addSubcommands(childData);
                continue;
            }

            SubcommandGroupData groupData = groupedSubcommands.getOrDefault(group.getName(), group);
            groupedSubcommands.put(group.getName(), groupData.addSubcommands(childData));
        }

        if (!groupedSubcommands.isEmpty())
            data.addSubcommandGroups(groupedSubcommands.values());
    }

    private SubcommandData toSubcommandData(SlashCommand child)
    {
        SubcommandData childData = new SubcommandData(child.getName(), child.getHelp());
        if (!child.getOptions().isEmpty())
            childData.addOptions(child.getOptions());
        applyLocalizations(childData, child.getNameLocalization(), child.getDescriptionLocalization());
        return childData;
    }

    private void applyDefaultPermissions(SlashCommandData data)
    {
        if (this.getUserPermissions() == null)
        {
            data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
            return;
        }
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(this.getUserPermissions()));
    }

    
    public SlashCommand[] getChildren()
    {
        return children;
    }

    private void terminate(SlashCommandEvent event, String message, CommandClient client)
    {
        if(message!=null)
            event.reply(message).setEphemeral(true).queue();
        if(client.getListener()!=null)
            client.getListener().onTerminatedSlashCommand(event, this);
    }

    
    public String getCooldownKey(SlashCommandEvent event)
    {
        switch (cooldownScope)
        {
            case USER:         return cooldownScope.genKey(name,event.getUser().getIdLong());
            case USER_GUILD:   return event.getGuild()!=null ? cooldownScope.genKey(name,event.getUser().getIdLong(),event.getGuild().getIdLong()) :
                    CooldownScope.USER_CHANNEL.genKey(name,event.getUser().getIdLong(), event.getChannel().getIdLong());
            case USER_CHANNEL: return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getChannel().getIdLong());
            case GUILD:        return event.getGuild()!=null ? cooldownScope.genKey(name,event.getGuild().getIdLong()) :
                    CooldownScope.CHANNEL.genKey(name,event.getChannel().getIdLong());
            case CHANNEL:      return cooldownScope.genKey(name,event.getChannel().getIdLong());
            case SHARD:
                event.getJDA().getShardInfo();
                return cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
            case USER_SHARD:
                event.getJDA().getShardInfo();
                return cooldownScope.genKey(name,event.getUser().getIdLong(),event.getJDA().getShardInfo().getShardId());
            case GLOBAL:       return cooldownScope.genKey(name, 0);
            default:           return "";
        }
    }

    
    public String getCooldownError(SlashCommandEvent event, int remaining, CommandClient client)
    {
        if(remaining<=0)
            return null;
        String front = client.getWarning()+" That command is on cooldown for "+remaining+" more seconds";
        if(cooldownScope.equals(CooldownScope.USER))
            return front+"!";
        else if(cooldownScope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
            return front+" "+ CooldownScope.USER_CHANNEL.errorSpecification+"!";
        else if(cooldownScope.equals(CooldownScope.GUILD) && event.getGuild()==null)
            return front+" "+ CooldownScope.CHANNEL.errorSpecification+"!";
        else
            return front+" "+cooldownScope.errorSpecification+"!";
    }

    
    public Map<DiscordLocale, String> getNameLocalization() {
        return nameLocalization;
    }

    
    public Map<DiscordLocale, String> getDescriptionLocalization() {
        return descriptionLocalization;
    }
}
