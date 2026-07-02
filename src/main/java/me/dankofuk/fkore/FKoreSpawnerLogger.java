package me.dankofuk.fkore;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.dankofuk.KushStaffUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// Import the specific FactionsKore events here.
// Adjust the package path if FactionsKore uses a different one.
import com.golfing8.kore.event.StackedSpawnerAddEvent;
import com.golfing8.kore.event.StackedSpawnerRemoveEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

class SpawnerLogger implements Listener {
    private final KushStaffUtils instance;

    public SpawnerLogger(KushStaffUtils instance) {
        this.instance = instance;
    }

    @EventHandler
    public void onSpawnerPlace(StackedSpawnerAddEvent event) {
        // NOTE: Adjust these getter methods if FactionsKore uses different names (e.g. getSpawnerAmount())
        Player player = event.getPlayer();
        int amount = event.getAmountToAdd();
        EntityType spawnerType = event.getStackedSpawner().getSpawnedType();
        Location loc = event.getStackedSpawner().getLocation();

        if (player != null) {
            String worldName = Objects.requireNonNull(loc.getWorld()).getName();

            KushStaffUtils.getInstance().getLogger().info(player.getName() + " placed " + amount + " " + spawnerType + " spawner(s).");
            sendWebhookToDiscord(player.getName(), "Placed", spawnerType, amount, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), worldName);
        }
    }

    @EventHandler
    public void onSpawnerRemove(StackedSpawnerRemoveEvent event) {
        // NOTE: Adjust these getter methods if FactionsKore uses different names
        Player player = event.getPlayer();
        int amount = event.getAmountToRemove();
        EntityType spawnerType = event.getStackedSpawner().getSpawnedType();
        Location loc = event.getStackedSpawner().getLocation();

        if (player != null) {
            String worldName = Objects.requireNonNull(loc.getWorld()).getName();

            KushStaffUtils.getInstance().getLogger().info(player.getName() + " removed/broke " + amount + " " + spawnerType + " spawner(s).");
            sendWebhookToDiscord(player.getName(), "Removed", spawnerType, amount, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), worldName);
        }
    }

    private void sendWebhookToDiscord(String playerName, String action, EntityType spawnerType, int amount, int xLocation, int yLocation, int zLocation, String worldName) {
        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(Objects.requireNonNull(KushStaffUtils.getInstance().getConfig().getString("SPAWNER-LOGGER.webhookUrl")));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "SpawnerLogger");
                connection.setDoOutput(true);

                JsonObject json = new JsonObject();
                json.addProperty("username", "Spawner Logger");

                JsonArray embeds = new JsonArray();
                JsonObject embed = new JsonObject();

                // Convert EntityType to a String (e.g., "COW" or "ZOMBIE")
                String typeString = (spawnerType != null) ? spawnerType.name() : "Unknown";

                // Format the description using the config
                // FIXED: Changed 'SpawnerType' to 'typeString' to match the variable and handle the object type
                String description = Objects.requireNonNull(KushStaffUtils.getInstance().getConfig().getString("SPAWNER-LOGGER.messageFormat"))
                        .replace("%player%", playerName)
                        .replace("%action%", action)
                        .replace("%type%", typeString)
                        .replace("%amount%", String.valueOf(amount))
                        .replace("%xLocation%", String.valueOf(xLocation))
                        .replace("%yLocation%", String.valueOf(yLocation))
                        .replace("%zLocation%", String.valueOf(zLocation))
                        .replace("%worldName%", worldName);

                embed.addProperty("description", description);
                embed.addProperty("title", KushStaffUtils.getInstance().getConfig().getString("SPAWNER-LOGGER.titleFormat"));
                embed.addProperty("color", KushStaffUtils.getInstance().getConfig().getInt("SPAWNER-LOGGER.embedColor"));

                embeds.add(embed);
                json.add("embeds", embeds);

                String message = (new Gson()).toJson(json);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(message.getBytes());
                }

                connection.connect();
                int responseCode = connection.getResponseCode();

                if (responseCode < 200 || responseCode >= 300) {
                    Bukkit.getLogger().warning("[SpawnerLogger] Failed to send webhook! HTTP Response Code: " + responseCode);
                }

            } catch (MalformedURLException | ProtocolException e) {
                Bukkit.getLogger().warning("[SpawnerLogger] Invalid webhook URL or protocol specified in config.");
            } catch (IOException e) {
                Bukkit.getLogger().warning("[SpawnerLogger] Error sending message to Discord webhook.");
            }
        });
    }
}