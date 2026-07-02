package me.dankofuk.sync;

import me.dankofuk.KushStaffUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Timed reward feature. Posts a reward panel with a claim button per configured reward to a Discord
 * channel (on a timer and/or on demand), and processes claims: verifies the clicker is synced, has
 * the required role, is online, and is off cooldown, then runs the reward commands in-game.
 */
public class RewardService {

    public static final String BUTTON_PREFIX = "kush_reward_";

    private final KushStaffUtils plugin;
    private final SyncStorage storage;

    public RewardService(KushStaffUtils plugin, SyncStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    private FileConfiguration cfg() {
        return plugin.syncingConfig;
    }

    // --- Panel building ------------------------------------------------------------------------

    public List<String> getButtonIds() {
        ConfigurationSection section = cfg().getConfigurationSection("BUTTONS");
        if (section == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public MessageEmbed buildPanelEmbed() {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(cfg().getString("REWARD-EMBED.TITLE", "Rewards"));
        List<String> description = cfg().getStringList("REWARD-EMBED.DESCRIPTION");
        if (!description.isEmpty()) {
            embed.setDescription(String.join("\n", description));
        }
        String thumb = cfg().getString("REWARD-EMBED.THUMBNAIL-URL");
        if (thumb != null && !thumb.isEmpty()) {
            embed.setThumbnail(thumb);
        }
        embed.setColor(parseColor(cfg().getString("REWARD-EMBED.COLOR", "#FFA500")));
        return embed.build();
    }

    public List<ActionRow> buildButtonRows() {
        List<Button> buttons = new ArrayList<>();
        for (String id : getButtonIds()) {
            String label = cfg().getString("BUTTONS." + id + ".MESSAGE", id);
            if (label.length() > 80) {
                label = label.substring(0, 80);
            }
            buttons.add(Button.success(BUTTON_PREFIX + id, label));
        }
        // Discord allows up to 5 buttons per action row and 5 rows per message.
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size() && rows.size() < 5; i += 5) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
        }
        return rows;
    }

    public void postPanel(TextChannel channel) {
        if (channel == null) {
            return;
        }
        List<ActionRow> rows = buildButtonRows();
        if (rows.isEmpty()) {
            channel.sendMessageEmbeds(buildPanelEmbed()).queue();
        } else {
            channel.sendMessageEmbeds(buildPanelEmbed()).setComponents(rows).queue();
        }
    }

    // --- Claiming ------------------------------------------------------------------------------

    public enum ClaimType {
        UNKNOWN_BUTTON,
        NOT_SYNCED,
        NO_ROLE,
        OFFLINE,
        ON_COOLDOWN,
        SUCCESS
    }

    /** Outcome of a claim attempt, including remaining cooldown seconds when relevant. */
    public static class ClaimResult {
        public final ClaimType type;
        public final long secondsRemaining;

        ClaimResult(ClaimType type, long secondsRemaining) {
            this.type = type;
            this.secondsRemaining = secondsRemaining;
        }
    }

    /**
     * Validates and (on success) executes a reward claim. Callable from a JDA thread; the reward
     * commands are dispatched on the Bukkit main thread.
     *
     * @param buttonId the reward id (component id minus {@link #BUTTON_PREFIX})
     * @param discordId the clicking user's id
     * @param member the clicking member (for role checks); may be null outside a guild
     */
    public ClaimResult claim(String buttonId, long discordId, Member member) {
        ConfigurationSection button = cfg().getConfigurationSection("BUTTONS." + buttonId);
        if (button == null) {
            return new ClaimResult(ClaimType.UNKNOWN_BUTTON, 0);
        }

        UUID uuid = storage.getUuid(discordId);
        if (uuid == null) {
            return new ClaimResult(ClaimType.NOT_SYNCED, 0);
        }

        String requiredRole = button.getString("REQUIRED-ROLE-ID", "");
        if (requiredRole != null && !requiredRole.isEmpty()) {
            boolean hasRole = member != null && member.getRoles().stream()
                    .anyMatch(role -> role.getId().equals(requiredRole));
            if (!hasRole) {
                return new ClaimResult(ClaimType.NO_ROLE, 0);
            }
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return new ClaimResult(ClaimType.OFFLINE, 0);
        }

        long intervalSeconds = button.getLong("REWARD-INTERVAL", 60L);
        long last = storage.getCooldown(discordId, buttonId);
        long now = System.currentTimeMillis();
        long readyAt = last + intervalSeconds * 1000L;
        if (now < readyAt) {
            return new ClaimResult(ClaimType.ON_COOLDOWN, (readyAt - now + 999) / 1000L);
        }

        // Passed all checks: record the cooldown and run the reward commands on the main thread.
        storage.setCooldown(discordId, buttonId, now);
        List<String> rewards = button.getStringList("REWARDS");
        String playerName = player.getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String reward : rewards) {
                if (reward == null || reward.trim().isEmpty()) {
                    continue;
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        reward.replace("%player%", playerName).replace("%uuid%", uuid.toString()));
            }
        });
        return new ClaimResult(ClaimType.SUCCESS, 0);
    }

    private Color parseColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return Color.ORANGE;
        }
    }
}
