package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.commons.utils.FinderUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UserInfo extends SlashCommand {
    Logger log = LoggerFactory.getLogger("UserInfo");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public UserInfo() {
        this.name = "userinfo";
        this.help = "Displays information about the specified user";
        this.arguments = "<user>";
        this.guildOnly = true;
        this.category = new Category("General");

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.USER, "user", "User", true));
        this.options = options;

    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Member memb = event.getOption("user").getAsMember();

        event.replyEmbeds(buildUserInfoEmbed(memb).build()).queue();
    }

    @Override
    public void execute(CommandEvent event) {
        Member memb;

        if (event.getArgs().length() > 0) {
            try {

                if (event.getMessage().getReferencedMessage().getMentions().getMembers().size() != 0) {
                    memb = event.getMessage().getReferencedMessage().getMentions().getMembers().get(0);
                } else {
                    memb = FinderUtil.findMembers(event.getArgs(), event.getGuild()).get(0);
                }
            } catch (Exception e) {
                event.reply("User \"" + event.getArgs() + "\" not found.");
                return;
            }
        } else {
            memb = event.getMember();
        }

        event.getChannel().sendMessageEmbeds(buildUserInfoEmbed(memb).build()).queue();
    }

    private EmbedBuilder buildUserInfoEmbed(Member memb) {
        EmbedBuilder eb = new EmbedBuilder().setColor(memb.getColor());
        String name = memb.getEffectiveName();
        String tag = "#" + memb.getUser().getDiscriminator();
        String guildJoinDate = memb.getTimeJoined().format(DATE_FORMATTER);
        String discordJoinedDate = memb.getUser().getTimeCreated().format(DATE_FORMATTER);
        String id = memb.getUser().getId();
        String status = memb.getOnlineStatus().getKey().replace("offline", ":x: Offline").replace("dnd", ":red_circle: Do not disturb").replace("idle", "Idle").replace("online", ":white_check_mark: Online");
        String avatar = memb.getUser().getAvatarUrl();

        log.debug("\nUsername: " + memb.getEffectiveName() + "\n"
                + "Tag: " + memb.getUser().getDiscriminator() + "\n"
                + "Guild join date: " + memb.getUser().getTimeCreated().format(DATE_FORMATTER) + "\n"
                + "User ID: " + memb.getUser().getId() + "\n"
                + "Online status: " + memb.getOnlineStatus());

        String game;
        try {
            game = memb.getActivities().toString();
        } catch (Exception e) {
            game = "-/-";
        }

        String roles = formatRoles(memb);
        if (avatar == null) {
            avatar = "No avatar";
        }

        eb.setAuthor(memb.getUser().getName() + tag + "'s user info", null, null)
                .addField(":pencil2: Name/Nickname", "**" + name + "**", true)
                .addField(":link: DiscordTag", "**" + tag + "**", true)
                .addField(":1234: ID", "**" + id + "**", true)
                .addBlankField(false)
                .addField(":signal_strength: Current status", "**" + status + "**", true)
                .addField(":video_game: Playing", "**" + game + "**", true)
                .addField(":tools: Roles", "**" + roles + "**", true)
                .addBlankField(false)
                .addField(":inbox_tray: Server join date", "**" + guildJoinDate + "**", true)
                .addField(":beginner: Account created on", "**" + discordJoinedDate + "**", true)
                .addBlankField(false)
                .addField(":frame_photo: Avatar URL", avatar, false);

        if (!"No avatar".equals(avatar)) {
            eb.setAuthor(memb.getUser().getName() + tag + "'s user info", null, avatar);
        }
        return eb;
    }

    private String formatRoles(Member member) {
        StringBuilder rolesBuilder = new StringBuilder();
        for (Role role : member.getRoles()) {
            rolesBuilder.append(role.getName()).append(", ");
        }
        if (rolesBuilder.length() == 0) {
            return "No roles in this server";
        }
        rolesBuilder.setLength(rolesBuilder.length() - 2);
        return rolesBuilder.toString();
    }
}
