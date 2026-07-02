package me.dankofuk.sync;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Flat-file persistence for Discord&lt;-&gt;Minecraft account links and per-button reward cooldowns.
 *
 * <p>Data is stored in {@code data.yml} in the plugin folder. All public methods are synchronized
 * because links are created on the Bukkit main thread ({@code /sync}) while cooldown reads/writes
 * happen on JDA gateway threads (button clicks).</p>
 */
public class SyncStorage {

    private final Plugin plugin;
    private final File file;
    private FileConfiguration data;

    // In-memory indexes for fast lookups; the source of truth is still persisted to data.yml.
    private final Map<Long, UUID> discordToUuid = new HashMap<>();
    private final Map<UUID, Long> uuidToDiscord = new HashMap<>();
    private final Map<Long, String> discordToName = new HashMap<>();

    public SyncStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private synchronized void load() {
        if (!file.exists()) {
            try {
                if (plugin.getDataFolder().mkdirs() || plugin.getDataFolder().exists()) {
                    file.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        discordToUuid.clear();
        uuidToDiscord.clear();
        discordToName.clear();

        ConfigurationSection links = data.getConfigurationSection("links");
        if (links != null) {
            for (String key : links.getKeys(false)) {
                try {
                    long discordId = Long.parseLong(key);
                    String uuidStr = links.getString(key + ".uuid");
                    String name = links.getString(key + ".name", "");
                    if (uuidStr != null) {
                        UUID uuid = UUID.fromString(uuidStr);
                        discordToUuid.put(discordId, uuid);
                        uuidToDiscord.put(uuid, discordId);
                        discordToName.put(discordId, name);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed entries rather than failing to load the whole file.
                }
            }
        }
    }

    private synchronized void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }

    // --- Account links -------------------------------------------------------------------------

    public synchronized void link(long discordId, UUID uuid, String name) {
        discordToUuid.put(discordId, uuid);
        uuidToDiscord.put(uuid, discordId);
        discordToName.put(discordId, name);
        data.set("links." + discordId + ".uuid", uuid.toString());
        data.set("links." + discordId + ".name", name);
        save();
    }

    /**
     * Removes a link by Discord id.
     *
     * @return the Minecraft UUID that was linked, or {@code null} if the id was not synced
     */
    public synchronized UUID unlinkByDiscord(long discordId) {
        UUID uuid = discordToUuid.remove(discordId);
        discordToName.remove(discordId);
        if (uuid != null) {
            uuidToDiscord.remove(uuid);
        }
        data.set("links." + discordId, null);
        data.set("cooldowns." + discordId, null);
        save();
        return uuid;
    }

    public synchronized boolean isDiscordSynced(long discordId) {
        return discordToUuid.containsKey(discordId);
    }

    public synchronized boolean isUuidSynced(UUID uuid) {
        return uuidToDiscord.containsKey(uuid);
    }

    public synchronized UUID getUuid(long discordId) {
        return discordToUuid.get(discordId);
    }

    public synchronized Long getDiscordId(UUID uuid) {
        return uuidToDiscord.get(uuid);
    }

    public synchronized String getName(long discordId) {
        return discordToName.get(discordId);
    }

    // --- Reward cooldowns ----------------------------------------------------------------------

    /**
     * @return epoch millis of the last claim for this discord id + button, or 0 if never claimed
     */
    public synchronized long getCooldown(long discordId, String buttonId) {
        return data.getLong("cooldowns." + discordId + "." + buttonId, 0L);
    }

    public synchronized void setCooldown(long discordId, String buttonId, long epochMillis) {
        data.set("cooldowns." + discordId + "." + buttonId, epochMillis);
        save();
    }
}
