package me.dankofuk.discord.listeners;

import me.dankofuk.KushStaffUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class DiscordChat2Game extends ListenerAdapter {
    private final KushStaffUtils main;
    public FileConfiguration config;

    public DiscordChat2Game(KushStaffUtils main, FileConfiguration config) {
        this.main = main;
        this.config = KushStaffUtils.getInstance().getConfig();

    }

    public void onMessageReceived(MessageReceivedEvent event) {
        if (!config.getBoolean("discord2game.enabled"))
            return;
        if (!event.getChannel().getId().equals(config.getString("discord2game.channelId")))
            return;
        if (!event.getAuthor().isBot()) {
            String messageContent = event.getMessage().getContentDisplay();
            String template = config.getString("discord2game.message");
            if (template == null) {
                return;
            }
            String formattedMessage = template.replace("%author%", event.getAuthor().getName()).replace("%message%", messageContent);
            formattedMessage = ChatColor.translateAlternateColorCodes('&', formattedMessage);
            if (config.getBoolean("discord2game.roleIdRequired") && (config.getString("discord2game.roleId") == null || config.getString("discord2game.roleId").isEmpty() || !userHasRequiredRole(event))) {
                sendNoPermissionEmbed(event);
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers())
                player.sendMessage(formattedMessage);
        }
    }

    private boolean userHasRequiredRole(MessageReceivedEvent event) {
        // getMember() is null for webhook/system messages and outside a guild context.
        if (event.getMember() == null) {
            return false;
        }
        String roleId = config.getString("discord2game.roleId");
        return event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(roleId));
    }

    private void sendNoPermissionEmbed(MessageReceivedEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.RED);
        embedBuilder.setColor(Color.RED);
        embedBuilder.setTitle("Error #NotDankEnough");
        embedBuilder.setDescription(">  `You lack the required permissions to type in game chat from discord!`");
        event.getMessage().replyEmbeds(embedBuilder.build(), new net.dv8tion.jda.api.entities.MessageEmbed[0]).queue(reply -> reply.delete().queueAfter(6L, TimeUnit.SECONDS));
    }
}