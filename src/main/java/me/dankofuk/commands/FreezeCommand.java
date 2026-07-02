package me.dankofuk.commands;


import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
public class FreezeCommand implements CommandExecutor, Listener {
    public FileConfiguration config;
    private final Map<Player, Location> frozenPlayers = new HashMap<>();
    private final Map<Player, BukkitTask> frozenPlayerTasks = new HashMap<>();
    public List<String> freezeMessages = new ArrayList<>();
    public String noPermissionMessage;
    public String playerNotFoundMessage;
    public String cannotFreezeOpPlayerMessage;
    public String cannotFreezeSelfMessage;
    public String freezeSuccessMessage;
    public String unfreezeSuccessMessage;
    public String setUnfreezeSuccessMessage;
    public String frozenGUILore;
    public String frozenGUIBarrierName;
    public String frozenGUITitle;
    public String cannotUseEnderpearlsOrChorusFruit;
    public String cannotChat;
    public String cannotUseCommands;
    public String cannotPlaceBlocks;
    public String cannotBreakBlocks;
    public String discordServerMessage;
    public Plugin plugin;
    public String logoutCommand;
    private static me.dankofuk.utils.ColorUtils ColorUtils;
    // Constructor to set default messages
    public FreezeCommand(FileConfiguration config, Plugin plugin) {
        this.plugin = plugin;
        this.config = config;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("commandlogger.freeze.use")) {
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(noPermissionMessage));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes("&cUsage: /freeze <player>"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(playerNotFoundMessage));
            return true;
        }
        if (target.isOp()) {
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotFreezeOpPlayerMessage));
            return true;
        }
        if (sender instanceof Player && target.equals(sender)) {
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotFreezeSelfMessage));
            return true;
        }
        if (frozenPlayers.containsKey(target)) {
            // Player is already frozen, unfreeze them and remove them from the list of frozen players
            unfreezePlayer(target);
            // Use literal placeholder replacement: the config messages contain %player% (not %s),
            // so String.format would throw UnknownFormatConversionException on the '%p'.
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(
                    unfreezeSuccessMessage.replace("%player%", target.getName())));
        } else {
            // Player is not frozen, freeze them
            freezePlayer(target);
            sender.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(
                    freezeSuccessMessage.replace("%player%", target.getName())));
        }
        return true;
    }
    private void freezePlayer(Player player) {
        // Save player location to teleport them back later
        frozenPlayers.put(player, player.getLocation());
        // Add blindness, freeze and jump boost effects (resolved by name for cross-version support)
        addEffect(player, me.dankofuk.utils.Compat.BLINDNESS, 0);
        addEffect(player, me.dankofuk.utils.Compat.SLOWNESS, 255);
        addEffect(player, me.dankofuk.utils.Compat.JUMP_BOOST, -10);
        // Open the frozen GUI
        openFrozenGUI(player);
        // Send the freeze message to the player (one configured line at a time)
        for (String line : freezeMessages) {
            player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(line));
        }
        // Start task to check for movement and teleport player back
        BukkitTask task = new BukkitRunnable() {
            final Location previousLocation = player.getLocation();
            @Override
            public void run() {
                if (!player.getLocation().equals(previousLocation)) {
                    player.teleport(previousLocation);
                }
                if (!player.isOnline()) {
                    unfreezePlayer(player);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), logoutCommand.replace("%player%", player.getName()));
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        frozenPlayerTasks.put(player, task);
    }
    private void unfreezePlayer(Player player) {
        // Close the frozen GUI
        player.closeInventory();
        // Cancel the task checking for movement
        BukkitTask task = frozenPlayerTasks.get(player);
        if (task != null) {
            task.cancel();
        }
        // Teleport player back to their previous location
        Location previousLocation = frozenPlayers.get(player);
        if (previousLocation != null) {
            player.teleport(previousLocation);
        }
        // Remove blindness and freeze effects
        removeEffect(player, me.dankofuk.utils.Compat.BLINDNESS);
        removeEffect(player, me.dankofuk.utils.Compat.SLOWNESS);
        removeEffect(player, me.dankofuk.utils.Compat.JUMP_BOOST);
        // Remove player from list of frozen players
        frozenPlayers.remove(player);
    }
    private void addEffect(Player player, PotionEffectType type, int amplifier) {
        if (type != null) {
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false));
        }
    }

    private void removeEffect(Player player, PotionEffectType type) {
        if (type != null) {
            player.removePotionEffect(type);
        }
    }

    private void openFrozenGUI(Player player) {
        if (frozenPlayers.containsKey(player) && config != null && !player.getOpenInventory().getTitle().equals(me.dankofuk.utils.ColorUtils.translateColorCodes(frozenGUITitle))) {
            Inventory gui = Bukkit.createInventory(player, 9, me.dankofuk.utils.ColorUtils.translateColorCodes(frozenGUITitle));
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta barrierMeta = barrier.getItemMeta();
            Objects.requireNonNull(barrierMeta).setDisplayName(me.dankofuk.utils.ColorUtils.translateColorCodes(frozenGUIBarrierName));
            barrierMeta.setLore(Collections.singletonList(me.dankofuk.utils.ColorUtils.translateColorCodes(frozenGUILore)));
            barrier.setItemMeta(barrierMeta);
            gui.setItem(4, barrier);
            player.openInventory(gui);
        }
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
            if (event.getRawSlot() == 4) {
                player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(discordServerMessage));
            }
        }
        if (event.getInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            openFrozenGUI(player); // Re-open the GUI if player tries to close it while still frozen
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            assert to != null;
            if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
                // Player moved horizontally, teleport them back to their previous location
                player.teleport(frozenPlayers.get(player));
            } else if (from.getBlockY() != to.getBlockY()) {
                // Player moved vertically, cancel the effect that caused the movement
                removeEffect(player, me.dankofuk.utils.Compat.JUMP_BOOST);
            }
        }
    }
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
            player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotUseCommands));
        }
    }
    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
            player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotChat));
        }
    }
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
            player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotPlaceBlocks));
        }
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            event.setCancelled(true);
            player.sendMessage(me.dankofuk.utils.ColorUtils.translateColorCodes(cannotBreakBlocks));
        }
    }
    // Removes Freezing effect if logged out (Should ban if logging out while frozen if using the default config)
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player)) {
            unfreezePlayer(player);
        }
    }
    public void setFreezeMessages(List<String> freezeMessages) {
        this.freezeMessages = freezeMessages;
    }
    public void setNoPermissionMessage(String noPermissionMessage) {
        this.noPermissionMessage = noPermissionMessage;
    }
    public void setPlayerNotFoundMessage(String playerNotFoundMessage) {
        this.playerNotFoundMessage = playerNotFoundMessage;
    }
    public void setCannotFreezeOpPlayerMessage(String cannotFreezeOpPlayerMessage) {
        this.cannotFreezeOpPlayerMessage = cannotFreezeOpPlayerMessage;
    }
    public void setCannotFreezeSelfMessage(String cannotFreezeSelfMessage) {
        this.cannotFreezeSelfMessage = cannotFreezeSelfMessage;
    }
    public void setFreezeSuccessMessage(String freezeSuccessMessage) {
        this.freezeSuccessMessage = freezeSuccessMessage;
    }
    public void setUnfreezeSuccessMessage(String unfreezeSuccessMessage) {
        this.unfreezeSuccessMessage = unfreezeSuccessMessage;
    }
    public void setFrozenGUILore(String frozenGUILore) {
        this.frozenGUILore = frozenGUILore;
    }
    public void setFrozenGUIBarrierName(String frozenGUIBarrierName) {
        this.frozenGUIBarrierName = frozenGUIBarrierName;
    }
    public void setFrozenGUITitle(String frozenGUITitle) {
        this.frozenGUITitle = frozenGUITitle;
    }
    public void setCannotUseEnderpearlsOrChorusFruit(String cannotUseEnderpearlsOrChorusFruit) {
        this.cannotUseEnderpearlsOrChorusFruit = cannotUseEnderpearlsOrChorusFruit;
    }
    public void setCannotChat(String cannotChat) {
        this.cannotChat = cannotChat;
    }
    public void setCannotUseCommands(String cannotUseCommands) {
        this.cannotUseCommands = cannotUseCommands;
    }
    public void setCannotPlaceBlocks(String cannotPlaceBlocks) {
        this.cannotPlaceBlocks = cannotPlaceBlocks;
    }
    public void setCannotBreakBlocks(String cannotBreakBlocks) {
        this.cannotBreakBlocks = cannotBreakBlocks;
    }
    public void setDiscordServerMessage(String discordServerMessage) {
        this.discordServerMessage = discordServerMessage;
    }
    public void setLogoutCommand(String logoutCommand) {
        this.logoutCommand = logoutCommand;
    }
}