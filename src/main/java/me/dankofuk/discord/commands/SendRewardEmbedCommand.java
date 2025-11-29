package me.dankofuk.discord.commands;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.discord.DiscordBot;
import me.dankofuk.discord.syncing.SyncStorage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class SendRewardEmbedCommand extends ListenerAdapter {
    private final FileConfiguration config;
    public DiscordBot discordBot;
    public final SyncStorage syncStorage;
    public Map<Long, Long> lastClaimedTimes = new HashMap<>();
    private final Map<String, Boolean> interactionHandled = new HashMap<>();

    public SendRewardEmbedCommand(DiscordBot discordBot, FileConfiguration config, SyncStorage syncStorage) {
        this.discordBot = discordBot;
        this.config = KushStaffUtils.getInstance().syncingConfig;
        this.syncStorage = new SyncStorage(
                KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.URL"),
                KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.USERNAME"),
                KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.PASSWORD")
        );
    }

    private KushStaffUtils getPlugin() {
        return KushStaffUtils.getInstance();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        boolean hasPermission = Objects.requireNonNull(event.getMember()).getRoles().stream()
                .anyMatch(role -> role.getId().equals(discordBot.getAdminRoleID()));

        if (!hasPermission) {
            EmbedBuilder noPerms = new EmbedBuilder();
            noPerms.setColor(Color.RED);
            noPerms.setTitle("Error #NotDankEnough");
            noPerms.setDescription(">  `You lack the required permissions for this command!`");
            noPerms.setFooter(OffsetDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
            event.replyEmbeds(noPerms.build()).queue();
            return;
        }
        if (event.getName().equals("sendrewardpanel")) {
            String channelId = Objects.requireNonNull(event.getOption("channel")).getAsString();
            MessageChannel channel = discordBot.jda.getChannelById(MessageChannel.class, channelId);
            if (channel == null) {
                event.reply("Invalid channel!").setEphemeral(true).queue();
                return;
            }

            String title = config.getString("REWARD-EMBED.TITLE");
            List<String> descriptionLines = config.getStringList("REWARD-EMBED.DESCRIPTION");
            String description = String.join("\n", descriptionLines);
            String thumbnail = config.getString("REWARD-EMBED.THUMBNAIL-URL");
            Color color = Color.decode(Objects.requireNonNull(config.getString("REWARD-EMBED.COLOR")));

            EmbedBuilder embedBuilder = new EmbedBuilder();
            embedBuilder.setTitle(title);
            embedBuilder.setDescription(description);
            embedBuilder.setColor(color);
            embedBuilder.setTimestamp(OffsetDateTime.now());
            embedBuilder.setThumbnail(thumbnail);

            List<Button> buttons = new ArrayList<>();
            Objects.requireNonNull(config.getConfigurationSection("BUTTONS")).getKeys(false).forEach(buttonKey -> {
                String label = config.getString("BUTTONS." + buttonKey + ".MESSAGE");
                buttons.add(Button.primary(buttonKey, Objects.requireNonNull(label)));
            });

            channel.sendMessageEmbeds(embedBuilder.build())
                    .setActionRow(buttons)
                    .queue();
            event.reply("Reward embed with buttons sent to channel.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent vEvent) {
        if (vEvent.isAcknowledged()) {
            Bukkit.getLogger().info("Ignoring already acknowledged interaction.");
            return;
        }
        String buttonId = vEvent.getComponentId();
        long discordId = vEvent.getUser().getIdLong();
        long currentTime = System.currentTimeMillis();

        // Generate a unique interaction ID
        String interactionId = vEvent.getId() + buttonId;

        // Check if the interaction has already been handled
        if (interactionHandled.getOrDefault(interactionId, false)) {
            Bukkit.getLogger().info("Ignoring duplicate interaction: " + interactionId);
            return;
        }

        // Mark interaction as being handled
        interactionHandled.put(interactionId, true);

        // Async part: DB lookup / role check / permission / cooldown
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            Bukkit.getLogger().info("Handling button interaction asynchronously...");

            if (buttonId.startsWith("send_reward_")) {
                String rewardType = buttonId.substring("send_reward_".length());
                vEvent.reply("You clicked the button for reward type: " + rewardType)
                        .setEphemeral(true)
                        .queue();
            } else {
                String requiredRoleId = config.getString("BUTTONS." + buttonId + ".REQUIRED-ROLE-ID");
                long interval = config.getLong("BUTTONS." + buttonId + ".REWARD-INTERVAL") * 1000;

                if (canClaimReward(discordId, currentTime, interval) &&
                        hasRequiredRole(discordId, requiredRoleId)) {

                    UUID minecraftUuid = syncStorage.getMinecraftUuid(discordId);

                    if (minecraftUuid != null) {
                        // Schedule reward-giving on the main server thread
                        Bukkit.getScheduler().runTask(getPlugin(), () -> giveReward(minecraftUuid, buttonId));
                        lastClaimedTimes.put(discordId, currentTime);
                        vEvent.reply("You have claimed your reward!").setEphemeral(true).queue();
                    } else {
                        vEvent.getChannel().sendMessage("You need to sync with Minecraft to claim the reward.").queue();
                    }

                } else {
                    long lastClaimedTime = lastClaimedTimes.getOrDefault(discordId, 0L);
                    long timeUntilNextClaim = (lastClaimedTime + config.getLong("REWARD-INTERVAL") * 1000) - currentTime;
                    vEvent.reply("You can claim your next reward in " + (timeUntilNextClaim / 1000) + " seconds.")
                            .setEphemeral(true)
                            .queue();
                    Bukkit.getLogger().info("Interaction handling complete.");
                }
            }

            // Reset the interaction flag after a short delay (on main thread)
            Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
                interactionHandled.put(interactionId, false);
                Bukkit.getLogger().info("Interaction handling complete.");
            }, 20L);  // 1 second delay (20 ticks)

        });
        vEvent.deferEdit().queue();
    }

    private boolean canClaimReward(long discordId, long currentTime, long interval) {
        long last = lastClaimedTimes.getOrDefault(discordId, 0L);
        return currentTime - last >= interval;
    }

    private boolean hasRequiredRole(long discordId, String requiredRoleId) {
        Guild guild = discordBot.getJda().getGuilds().get(0);
        Member member = guild.getMemberById(discordId);
        return member != null && member.getRoles().stream()
                .anyMatch(role -> role.getId().equals(requiredRoleId));
    }

    private void giveReward(UUID minecraftUuid, String buttonId) {
        Player player = Bukkit.getPlayer(minecraftUuid);
        if (player != null && player.isOnline()) {
            List<String> commands = config.getStringList("BUTTONS." + buttonId + ".REWARDS");
            for (String command : commands) {
                if (command != null && !command.isEmpty()) {
                    try {
                        String processedCommand = command.replace("%player%", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                    } catch (Exception e) {
                        getPlugin().getLogger().severe("Error executing command: " + command);
                        e.printStackTrace();
                    }
                }
            }
            player.sendMessage(ChatColor.GREEN + "You have received your reward!");
        }
    }
}
