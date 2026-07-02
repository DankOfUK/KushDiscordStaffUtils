package me.dankofuk.discord.verify;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.discord.DiscordBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.configuration.Configuration;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Verify panel: {@code /sendverifypanel <channel>} posts an embed with a button; clicking it grants
 * a configurable "verified" Discord role. Driven by the {@code verify_panel.*} keys in config.yml.
 */
public class SendVerifyPanel extends ListenerAdapter {

    public static final String VERIFY_BUTTON_ID = "verify_button";

    public KushStaffUtils instance;
    public DiscordBot discordBot;

    public SendVerifyPanel(DiscordBot discordBot, KushStaffUtils instance) {
        this.discordBot = discordBot;
        this.instance = instance;
    }

    private boolean isAdmin(Member member) {
        if (member == null) {
            return false;
        }
        String adminRoleId = discordBot.getAdminRoleID();
        if (adminRoleId == null || adminRoleId.isEmpty()) {
            return member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR);
        }
        return member.getRoles().stream().anyMatch(role -> role.getId().equals(adminRoleId));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // Gate on the command name FIRST so this listener never responds to other commands.
        if (!event.getName().equals("sendverifypanel")) {
            return;
        }

        if (!isAdmin(event.getMember())) {
            EmbedBuilder noPerms = new EmbedBuilder();
            noPerms.setColor(Color.RED);
            noPerms.setTitle("Error #NotDankEnough");
            noPerms.setDescription(">  `You lack the required permissions for this command!`");
            noPerms.setFooter(OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
            event.replyEmbeds(noPerms.build()).setEphemeral(true).queue();
            return;
        }

        Configuration config = KushStaffUtils.getInstance().getConfig();
        OptionMapping channelOption = event.getOption("channel");
        MessageChannel channel = channelOption == null ? null
                : discordBot.getJda().getChannelById(MessageChannel.class, channelOption.getAsString());

        if (channel == null) {
            event.reply("Invalid channel.").setEphemeral(true).queue();
            return;
        }

        List<String> embedMessageList = config.getStringList("verify_panel.embedMessage");
        String embedMessage = embedMessageList.isEmpty()
                ? "Click below to verify!" : String.join("\n", embedMessageList);
        String buttonMessage = config.getString("verify_panel.buttonMessage", "Verify");
        String sentMessage = config.getString("verify_panel.sentMessage", "Verify panel sent!");

        EmbedBuilder panel = new EmbedBuilder();
        panel.setDescription(embedMessage);
        panel.setColor(new Color(88, 101, 242)); // blurple
        String thumb = config.getString("verify_panel.embedThumbnail");
        if (thumb != null && thumb.startsWith("http")) { // ignore the "link-here" placeholder
            panel.setThumbnail(thumb);
        }

        channel.sendMessageEmbeds(panel.build())
                .setActionRow(Button.primary(VERIFY_BUTTON_ID, buttonMessage))
                .queue();
        event.reply(sentMessage).setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.getComponentId().equals(VERIFY_BUTTON_ID)) {
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        Configuration config = KushStaffUtils.getInstance().getConfig();
        String roleId = config.getString("verify_panel.giveRoleOnClicks");
        Role role = roleId == null ? null : guild.getRoleById(roleId);
        if (role == null) {
            event.reply("Verify role not found (tell an administrator).").setEphemeral(true).queue();
            return;
        }
        guild.addRoleToMember(UserSnowflake.fromId(event.getUser().getId()), role)
                .reason("KushStaffUtils verify panel")
                .queue(
                        success -> event.reply(config.getString("verify_panel.roleGivenMessage",
                                "You have been verified!")).setEphemeral(true).queue(),
                        error -> event.reply("Could not give the verify role: " + error.getMessage())
                                .setEphemeral(true).queue());
    }
}
