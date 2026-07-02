package me.dankofuk.sync;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * In-game {@code /sync <code>} command. Redeems a code generated from the Discord sync panel and,
 * on success, links the player's account (granting Discord roles + running the configured commands
 * via {@link SyncService}).
 */
public class SyncCommand implements CommandExecutor {

    private String msg(String path, String def) {
        String value = KushStaffUtils.getInstance().syncingConfig.getString(path, def);
        return ColorUtils.translateColorCodes(value != null ? value : def);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can sync an account.");
            return true;
        }
        Player player = (Player) sender;

        SyncService service = KushStaffUtils.getInstance().syncService;
        if (service == null) {
            player.sendMessage(msg("MESSAGES.INVALID-CODE-MESSAGE",
                    "&cSyncing is not enabled on this server."));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(msg("MESSAGES.COMMAND-USAGE", "&cUsage: /sync <code>"));
            return true;
        }

        SyncService.Result result = service.redeem(args[0], player);
        switch (result) {
            case SUCCESS:
                player.sendMessage(msg("MESSAGES.SYNCED-SUCCESSFULLY-MESSAGE",
                        "&aYou have been successfully synced!"));
                break;
            case ALREADY_SYNCED:
            case DISCORD_ALREADY_SYNCED:
                player.sendMessage(msg("MESSAGES.ALREADY-SYNCED-MESSAGE",
                        "&cYou have already been synced!"));
                break;
            case INVALID_CODE:
            default:
                player.sendMessage(msg("MESSAGES.INVALID-CODE-MESSAGE",
                        "&cInvalid or expired code!"));
                break;
        }
        return true;
    }
}
