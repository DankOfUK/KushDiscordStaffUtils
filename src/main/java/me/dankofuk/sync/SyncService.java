package me.dankofuk.sync;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.discord.DiscordBot;
import me.dankofuk.utils.CodeData;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the account-sync flow: generating one-time codes on the Discord side and, when a
 * player redeems one in-game, linking the accounts, granting the configured Discord roles and
 * running the configured in-game commands.
 */
public class SyncService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KushStaffUtils plugin;
    private final SyncStorage storage;
    private final DiscordBot bot;

    // code -> pending sync request
    private final ConcurrentHashMap<String, CodeData> pendingCodes = new ConcurrentHashMap<>();

    public SyncService(KushStaffUtils plugin, SyncStorage storage, DiscordBot bot) {
        this.plugin = plugin;
        this.storage = storage;
        this.bot = bot;
    }

    private FileConfiguration cfg() {
        return plugin.syncingConfig;
    }

    public SyncStorage getStorage() {
        return storage;
    }

    /**
     * Generates a fresh one-time code for the given Discord user/guild, replacing any existing
     * pending code for that user.
     */
    public String generateCode(long discordId, long guildId) {
        // Drop any previous pending code for this user so only the newest is valid.
        pendingCodes.values().removeIf(c -> c.getUserId() == discordId);

        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (pendingCodes.containsKey(code));

        long expiryMinutes = cfg().getLong("SYNC-PANEL.CODE-EXPIRY-MINUTES", 5L);
        long expiry = System.currentTimeMillis() + expiryMinutes * 60_000L;
        pendingCodes.put(code, new CodeData(discordId, guildId, code, expiry));
        return code;
    }

    /** Result of a redemption attempt. */
    public enum Result {
        INVALID_CODE,
        ALREADY_SYNCED,
        DISCORD_ALREADY_SYNCED,
        SUCCESS
    }

    /**
     * Attempts to redeem a code for the given player and, on success, links the accounts and
     * applies rewards. Must be called on the Bukkit main thread (dispatches commands).
     */
    public Result redeem(String code, Player player) {
        if (code == null) {
            return Result.INVALID_CODE;
        }
        CodeData data = pendingCodes.get(code.toUpperCase());
        if (data == null || data.isExpired()) {
            pendingCodes.remove(code.toUpperCase());
            return Result.INVALID_CODE;
        }
        if (storage.isUuidSynced(player.getUniqueId())) {
            return Result.ALREADY_SYNCED;
        }
        if (storage.isDiscordSynced(data.getUserId())) {
            return Result.DISCORD_ALREADY_SYNCED;
        }

        // Consume the code and persist the link.
        pendingCodes.remove(code.toUpperCase());
        storage.link(data.getUserId(), player.getUniqueId(), player.getName());

        grantDiscordRoles(data);
        runSyncCommands(player);
        return Result.SUCCESS;
    }

    private void grantDiscordRoles(CodeData data) {
        if (bot == null || bot.getJda() == null) {
            return;
        }
        Guild guild = bot.getJda().getGuildById(data.getGuildId());
        if (guild == null) {
            return;
        }
        List<String> roleIds = cfg().getStringList("SYNC-PANEL.ROLE-GIVEN-ON-SYNC");
        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                guild.addRoleToMember(UserSnowflake.fromId(data.getUserId()), role)
                        .reason("KushStaffUtils account sync")
                        .queue(null, err -> plugin.getLogger()
                                .warning("[Sync] Failed to add role " + roleId + ": " + err.getMessage()));
            }
        }
    }

    private void runSyncCommands(Player player) {
        List<String> commands = cfg().getStringList("SYNC-PANEL.COMMANDS-ON-SYNC");
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String finalCommand = command.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        }
    }

    /**
     * Removes a link by Discord id and strips the configured sync roles. Safe to call from any
     * thread; role removal is queued on JDA.
     *
     * @return the unlinked UUID, or null if the id was not synced
     */
    public UUID unsync(long discordId, long guildId) {
        UUID uuid = storage.unlinkByDiscord(discordId);
        if (uuid == null) {
            return null;
        }
        if (bot != null && bot.getJda() != null) {
            Guild guild = bot.getJda().getGuildById(guildId);
            if (guild != null) {
                for (String roleId : cfg().getStringList("SYNC-PANEL.ROLE-GIVEN-ON-SYNC")) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        guild.removeRoleFromMember(UserSnowflake.fromId(discordId), role)
                                .reason("KushStaffUtils account unsync").queue(null, err -> {});
                    }
                }
            }
        }
        return uuid;
    }
}
