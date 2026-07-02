package me.dankofuk.sync;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.discord.DiscordBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.UUID;

/**
 * Single JDA listener handling both the account-sync and timed-reward Discord interactions:
 * the {@code /sendsyncpanel}, {@code /sendrewardpanel} and {@code /unsync} slash commands, plus the
 * sync-start and reward-claim buttons.
 */
public class SyncRewardListener extends ListenerAdapter {

    public static final String SYNC_BUTTON_ID = "kush_sync_start";

    private final DiscordBot bot;

    public SyncRewardListener(DiscordBot bot) {
        this.bot = bot;
    }

    private KushStaffUtils plugin() {
        return KushStaffUtils.getInstance();
    }

    private FileConfiguration cfg() {
        return plugin().syncingConfig;
    }

    private boolean isAdmin(Member member) {
        if (member == null) {
            return false;
        }
        String adminRoleId = bot.getAdminRoleID();
        if (adminRoleId == null || adminRoleId.isEmpty()) {
            return member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR);
        }
        return member.getRoles().stream().anyMatch(r -> r.getId().equals(adminRoleId));
    }

    private TextChannel resolveTextChannel(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("channel");
        if (option == null) {
            return null;
        }
        GuildChannel channel = option.getAsChannel();
        if (channel.getType() != ChannelType.TEXT) {
            return null;
        }
        return (TextChannel) channel;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "sendsyncpanel":
                handleSendSyncPanel(event);
                break;
            case "sendrewardpanel":
                handleSendRewardPanel(event);
                break;
            case "unsync":
                handleUnsync(event);
                break;
            default:
                break;
        }
    }

    private void handleSendSyncPanel(SlashCommandInteractionEvent event) {
        if (!isAdmin(event.getMember())) {
            event.reply("You lack permission to use this command.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = resolveTextChannel(event);
        if (channel == null) {
            event.reply(cfg().getString("SYNC-PANEL.INVALID-CHANNEL-MESSAGE", "Invalid channel!"))
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(String.join("\n", cfg().getStringList("SYNC-PANEL.EMBED-MESSAGE")));
        String thumb = cfg().getString("SYNC-PANEL.THUMBNAIL-URL");
        if (thumb != null && !thumb.isEmpty()) {
            embed.setThumbnail(thumb);
        }
        embed.setColor(new Color(88, 101, 242)); // blurple
        Button button = Button.primary(SYNC_BUTTON_ID,
                cfg().getString("SYNC-PANEL.BUTTON-MESSAGE", "Click here!"));

        channel.sendMessageEmbeds(embed.build()).setActionRow(button).queue();
        event.reply(cfg().getString("SYNC-PANEL.SENT-MESSAGE", "Sync panel sent to %channel%")
                .replace("%channel%", channel.getAsMention())).setEphemeral(true).queue();
    }

    private void handleSendRewardPanel(SlashCommandInteractionEvent event) {
        if (!isAdmin(event.getMember())) {
            event.reply("You lack permission to use this command.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = resolveTextChannel(event);
        if (channel == null) {
            event.reply("Invalid channel!").setEphemeral(true).queue();
            return;
        }
        RewardService rewards = plugin().rewardService;
        if (rewards == null) {
            event.reply("Rewards are not enabled on this server.").setEphemeral(true).queue();
            return;
        }
        rewards.postPanel(channel);
        event.reply("Reward panel sent to " + channel.getAsMention()).setEphemeral(true).queue();
    }

    private void handleUnsync(SlashCommandInteractionEvent event) {
        if (!isAdmin(event.getMember())) {
            event.reply("You lack permission to use this command.").setEphemeral(true).queue();
            return;
        }
        SyncService sync = plugin().syncService;
        OptionMapping userOption = event.getOption("user");
        if (sync == null || userOption == null || event.getGuild() == null) {
            event.reply("Could not process unsync.").setEphemeral(true).queue();
            return;
        }
        long targetId = userOption.getAsUser().getIdLong();
        UUID removed = sync.unsync(targetId, event.getGuild().getIdLong());
        if (removed == null) {
            event.reply("That user is not synced.").setEphemeral(true).queue();
        } else {
            event.reply("Unsynced <@" + targetId + "> (was " + removed + ").").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (SYNC_BUTTON_ID.equals(id)) {
            handleSyncButton(event);
        } else if (id.startsWith(RewardService.BUTTON_PREFIX)) {
            handleRewardButton(event, id.substring(RewardService.BUTTON_PREFIX.length()));
        }
    }

    private void handleSyncButton(ButtonInteractionEvent event) {
        SyncService sync = plugin().syncService;
        if (sync == null || event.getGuild() == null) {
            event.reply("Syncing is not available right now.").setEphemeral(true).queue();
            return;
        }
        if (sync.getStorage().isDiscordSynced(event.getUser().getIdLong())) {
            event.reply("Your Discord account is already synced.").setEphemeral(true).queue();
            return;
        }
        String code = sync.generateCode(event.getUser().getIdLong(), event.getGuild().getIdLong());
        long minutes = cfg().getLong("SYNC-PANEL.CODE-EXPIRY-MINUTES", 5L);
        event.reply("Your sync code is **" + code + "**\nRun `/sync " + code
                + "` in-game within " + minutes + " minutes to link your account.")
                .setEphemeral(true).queue();
    }

    private void handleRewardButton(ButtonInteractionEvent event, String buttonId) {
        RewardService rewards = plugin().rewardService;
        if (rewards == null) {
            event.reply("Rewards are not available right now.").setEphemeral(true).queue();
            return;
        }
        RewardService.ClaimResult result = rewards.claim(buttonId, event.getUser().getIdLong(), event.getMember());
        String reply;
        switch (result.type) {
            case SUCCESS:
                reply = "Reward claimed! Check in-game.";
                break;
            case NOT_SYNCED:
                reply = "You must sync your account first. Use the sync panel.";
                break;
            case NO_ROLE:
                reply = "You don't have the required role to claim this reward.";
                break;
            case OFFLINE:
                reply = "You must be online in-game to claim this reward.";
                break;
            case ON_COOLDOWN:
                reply = "You've already claimed this. Try again in " + formatDuration(result.secondsRemaining) + ".";
                break;
            case UNKNOWN_BUTTON:
            default:
                reply = "That reward no longer exists.";
                break;
        }
        event.reply(reply).setEphemeral(true).queue();
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }
}
