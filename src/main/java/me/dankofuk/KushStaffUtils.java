package me.dankofuk;

import com.golfing8.kore.FactionsKore;
import com.golfing8.kore.feature.PrinterFeature;
import com.golfing8.kore.topexpansion.feature.FactionsTopFeature;
import me.dankofuk.commands.CommandLogViewer;
import me.dankofuk.commands.FreezeCommand;
import me.dankofuk.commands.StaffUtilsCommand;
import me.dankofuk.discord.DiscordBot;
import me.dankofuk.discord.commands.botRequiredCommands.BugCommand;
import me.dankofuk.discord.commands.botRequiredCommands.ReportCommand;
import me.dankofuk.discord.commands.botRequiredCommands.SuggestionCommand;
import me.dankofuk.discord.listeners.ChatWebhook;
import me.dankofuk.discord.listeners.CommandLogger;
import me.dankofuk.discord.listeners.StartStopLogger;
import me.dankofuk.commands.SyncGameCommand;
import me.dankofuk.discord.syncing.SyncStorage;
import me.dankofuk.factions.FactionStrike;
import me.dankofuk.factions.FactionsTopAnnouncer;
import me.dankofuk.fkore.FKoreEnterPrinterLogger;
import me.dankofuk.fkore.FKoreFactionOvertakeLogger;
import me.dankofuk.fkore.FKoreLeavePrinterLogger;
import me.dankofuk.loggers.advancedbans.*;
import me.dankofuk.loggers.creative.CreativeDropLogger;
import me.dankofuk.loggers.creative.CreativeMiddleClickLogger;
import me.dankofuk.loggers.litebans.listeners.LBBanListener;
import me.dankofuk.loggers.litebans.listeners.LBKickListener;
import me.dankofuk.loggers.litebans.listeners.LBMuteListener;
import me.dankofuk.loggers.litebans.listeners.LBWarnListener;
import me.dankofuk.loggers.players.FileCommandLogger;
import me.dankofuk.loggers.players.JoinLeaveLogger;
import me.dankofuk.utils.Metrics;
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class KushStaffUtils extends JavaPlugin implements Listener {
    private static String logsFolder;
    private static KushStaffUtils instance;
    private static FactionsKore factionsKore;
    private JDA jda;
    private Plugin plugin;

    public FileConfiguration config;
    public FileConfiguration messagesConfig;
    public FileConfiguration syncingConfig;
    public FileConfiguration discordBotConfig;

    public StartStopLogger startStopLogger;
    public CommandLogger commandLogger;
    public JoinLeaveLogger joinLeaveLogger;
    public FileCommandLogger fileCommandLogger;
    public ReportCommand reportCommand;
    public String reportSentMessage;
    public BugCommand bugCommand;
    public SuggestionCommand suggestionCommand;
    public DiscordBot discordBot;
    public FactionStrike factionStrike;
    public FactionsTopAnnouncer factionsTopAnnouncer;
    public ChatWebhook chatWebhook;
    public CreativeMiddleClickLogger creativeLogger;
    public CreativeDropLogger creativeDropLogger;
    public CommandLogViewer commandLogViewer;
    public StaffUtilsCommand staffUtilsCommand;
    public FreezeCommand freezeCommand;
    // LiteBans
    public LBBanListener bansListener;
    public LBMuteListener lbMuteListener;
    public LBWarnListener lbWarnListener;
    public LBKickListener lbKickListener;
    // AdvancedBans
    public ABanListener aBanListener;
    public ATempBanListener aTempBanListener;
    public AIPBanListener aIPBanListener;
    public AWarnListener aWarnListener;
    public AKickListener aKickListener;
    public AMuteListener aMuteListener;
    // Syncing
    public SyncStorage syncStorage;
    // FKore
    public FKoreLeavePrinterLogger printerLeaveLogger;
    public FKoreEnterPrinterLogger printerEnterLogger;
    public FKoreFactionOvertakeLogger overtakeLogger;
    public FactionsKore fkore;
    public PrinterFeature printerFeature;
    public FactionsTopFeature factionsTopFeature;
    public void onEnable() {
        // Loading configuration
        FileConfiguration config = getConfig();
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        messagesConfig = loadMessagesConfig();
        syncingConfig = loadSyncingConfig();
        discordBotConfig = loadBotConfig();
        setDefaultMessages();
        setBotMessages();
        setSyncingConfig();

        // Instance
        instance = this;

        // PAPI/Vault Checker
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin placeholderAPI = pluginManager.getPlugin("PlaceholderAPI");
        if (placeholderAPI == null)
            getLogger().warning("PlaceholderAPI is not installed or enabled. Some placeholders may not work.");
        Plugin vault = pluginManager.getPlugin("Vault");
        if (vault == null)
            getLogger().warning("Vault is not installed or enabled. Some functionality may be limited.");

        // bStats
        int pluginId = 18185;
        me.dankofuk.utils.Metrics metrics = new Metrics(this, pluginId);

        // Features
        if (config.getBoolean("bot.enabled")) {
            if ("false".equals(KushStaffUtils.getInstance().getConfig().getString("bot.discord_token")) || Objects.requireNonNull(KushStaffUtils.getInstance().getConfig().getString("bot.discord_token")).isEmpty()) {
                getLogger().warning("[Discord Bot] No bot token found. Bot initialization skipped.");
                return;
            }
            this.discordBot = new DiscordBot(this, config, syncStorage);
            try {
                this.discordBot.start();
                getLogger().warning("[Discord Bot] Starting Discord Bot...");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            getLogger().warning("[Discord Bot] Bot is disabled. Skipping initialization...");
        }
        this.commandLogViewer = new CommandLogViewer(getDataFolder().getPath() + File.separator + "logs", 15);
        Objects.requireNonNull(getCommand("viewlogs")).setExecutor(commandLogViewer);


        // FileCommandLogger (Logging Folder)
        if (!config.getBoolean("per-user-logging.enabled")) {
            getLogger().warning("Per User Logging - [Not Enabled]");
        } else {
            String logsFolder = (new File(getDataFolder(), "logs")).getPath();
            this.fileCommandLogger = new FileCommandLogger(logsFolder);
            getServer().getPluginManager().registerEvents(fileCommandLogger, this);
            getLogger().warning("Per User Logging - [Enabled]");
        }
        // Chat Webhook (Webhook)
        if (!config.getBoolean("chatwebhook.enabled")) {
            getLogger().warning("Chat Logger - [Not Enabled]");
        } else {
            this.chatWebhook = new ChatWebhook(config);
            getServer().getPluginManager().registerEvents(chatWebhook, this);
            getLogger().warning("Chat Logger - [Enabled]");
        }
        // Start/Stop Logger (Discord Bot Feature)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Start/Stop Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("serverstatus.enabled")) {
            getLogger().warning("Start/Stop Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.startStopLogger = new StartStopLogger(discordBot);
            startStopLogger.sendStatusUpdateMessage(true);
            getLogger().warning("Start/Stop Logger - [Enabled]");
        }
        // Factions/Skyblock Top Announcer (Webhook)
        if (!config.getBoolean("announcer.enabled")) {
            getLogger().warning("Factions Top Announcer - [Not Enabled]");
        } else {
            this.factionsTopAnnouncer = new FactionsTopAnnouncer(config, this);
            Bukkit.getPluginManager().registerEvents(factionsTopAnnouncer, this);
            getLogger().warning("Factions Top Announcer - [Enabled]");
        }
        // Freeze Command
        this.freezeCommand = new FreezeCommand(config, this);
        this.freezeCommand.setFreezeMessages(getConfig().getStringList("freeze.freezeMessages"));
        this.freezeCommand.setNoPermissionMessage(getConfig().getString("freeze.noPermissionMessage"));
        this.freezeCommand.setPlayerNotFoundMessage(getConfig().getString("freeze.playerNotFoundMessage"));
        this.freezeCommand.setCannotFreezeOpPlayerMessage(getConfig().getString("freeze.cannotFreezeOpPlayerMessage"));
        this.freezeCommand.setCannotFreezeSelfMessage(getConfig().getString("freeze.cannotFreezeSelfMessage"));
        this.freezeCommand.setFreezeSuccessMessage(getConfig().getString("freeze.freezeSuccessMessage"));
        this.freezeCommand.setUnfreezeSuccessMessage(getConfig().getString("freeze.unfreezeSuccessMessage"));
        this.freezeCommand.setFrozenGUITitle(getConfig().getString("freeze.frozenGUITitle"));
        this.freezeCommand.setFrozenGUIBarrierName(getConfig().getString("freeze.frozenGUIBarrierName"));
        this.freezeCommand.setFrozenGUILore(getConfig().getString("freeze.frozenGUILore"));
        this.freezeCommand.setCannotUseEnderpearlsOrChorusFruit(getConfig().getString("freeze.cannotUseEnderpearlsOrChorusFruit"));
        this.freezeCommand.setCannotChat(getConfig().getString("freeze.cannotChat"));
        this.freezeCommand.setCannotUseCommands(getConfig().getString("freeze.cannotUseCommands"));
        this.freezeCommand.setCannotPlaceBlocks(getConfig().getString("freeze.cannotPlaceBlocks"));
        this.freezeCommand.setCannotBreakBlocks(getConfig().getString("freeze.cannotBreakBlocks"));
        this.freezeCommand.setDiscordServerMessage(getConfig().getString("freeze.discordServerMessage"));
        this.freezeCommand.setLogoutCommand(getConfig().getString("freeze.logOutCommand"));
        getCommand("freeze").setExecutor(this.freezeCommand);
        getServer().getPluginManager().registerEvents(this.freezeCommand, this);
        // Player Report Command (Webhook + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Player Reporting Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("report.enabled")) {
            getLogger().warning("Player Reporting Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.reportCommand = new ReportCommand(this, discordBot);
            Objects.requireNonNull(getCommand("report")).setExecutor(this.reportCommand);
            Bukkit.getPluginManager().registerEvents(reportCommand, this);
            getLogger().warning("Player Reporting Command - [Enabled]");
        }
        // Strike Command (Webhook + Command)
        if (!config.getBoolean("strike.enabled")) {
            getLogger().warning("Strike Command - [Not Enabled]");
        } else {
            this.factionStrike = new FactionStrike(config, this);
            Objects.requireNonNull(getCommand("strike")).setExecutor(this.factionStrike);
            getLogger().warning("Strike Command - [Enabled]");
        }
        // Bug Report Command (Webhook + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Bug Report Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("bug_report.enabled")) {
            getLogger().warning("Bug Report Command - [Not Enabled]");
        } else {
            this.bugCommand = new BugCommand(this, discordBot, config);
            getServer().getPluginManager().registerEvents(this.bugCommand, this);
            Objects.requireNonNull(getCommand("bug")).setExecutor(this.bugCommand);
            getLogger().warning("Bug Command - [Enabled]");
        }
        // Join Leave Logger (Webhooks)
        if (!config.getBoolean("player_leave_join_logger.enabled")) {
            getLogger().warning("Player Join Leave Logger - [Not Enabled]");
        } else {
            this.joinLeaveLogger = new JoinLeaveLogger(config);
            Bukkit.getServer().getPluginManager().registerEvents(this.joinLeaveLogger, this);
            getLogger().warning("Player Join Leave Logger - [Enabled]");
        }
        // Suggestion Command (Discord Bot + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Suggestion Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("suggestion.enabled")) {
            getLogger().warning("Suggestion Command - [Not Enabled]");
        } else {
            this.suggestionCommand = new SuggestionCommand(this.discordBot, config);
            Objects.requireNonNull(getCommand("suggestion")).setExecutor(this.suggestionCommand);
            getServer().getPluginManager().registerEvents(this.suggestionCommand, this);
            getLogger().warning("Suggestion Command - [Enabled]");
        }
        // Command Logger (Discord Feature)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Command Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.commandLogger = new CommandLogger(this.discordBot);
            getServer().getPluginManager().registerEvents(this.commandLogger, this);
            getLogger().warning("Command Logger - [Enabled]");
        }

        // Creative Logging (Webhooks)
        if (!config.getBoolean("creative-logging.enabled")) {
            getLogger().warning("Creative Logging - [Not Enabled]");
        } else {
            this.creativeLogger = new CreativeMiddleClickLogger(this);
            this.creativeDropLogger = new CreativeDropLogger(this);
            getServer().getPluginManager().registerEvents(this.creativeLogger, this);
            getServer().getPluginManager().registerEvents(this.creativeDropLogger, this);
            getLogger().warning("Creative Logging - [Enabled]");
        }

        // LiteBans Logging (Webhooks)
        Plugin liteBans = pluginManager.getPlugin("LiteBans");
        if (liteBans == null) {
            getLogger().warning("LiteBans is not installed or enabled. This feature will not work!");
        } else if (!config.getBoolean("litebans.enabled")) {
            getLogger().warning("LiteBans Logging - [Not Enabled]");
        } else {
            this.lbKickListener = new LBKickListener(this);
            lbKickListener.registerEvents();
            this.lbWarnListener = new LBWarnListener(this);
            lbWarnListener.registerEvents();
            this.lbMuteListener = new LBMuteListener(this);
            lbMuteListener.registerEvents();
            this.bansListener = new LBBanListener(this);
            bansListener.registerEvents();
            getLogger().warning("LiteBans Logging - [Enabled]");
        }

        // Advanced Logging (Webhooks)
        Plugin advancedBans = pluginManager.getPlugin("AdvancedBans");
        if (advancedBans == null) {
            getLogger().warning("AdvancedBans is not installed or enabled. This feature will not work!");
        } else if (!config.getBoolean("advancedbans.enabled")) {
            getLogger().warning("AdvancedBans Logging - [Not Enabled]");
        } else {
            this.aMuteListener = new AMuteListener(this);
            getServer().getPluginManager().registerEvents(aMuteListener, this);
            this.aIPBanListener = new AIPBanListener(this);
            getServer().getPluginManager().registerEvents(aIPBanListener, this);
            this.aKickListener = new AKickListener(this);
            getServer().getPluginManager().registerEvents(aKickListener, this);
            this.aWarnListener = new AWarnListener(this);
            getServer().getPluginManager().registerEvents(aWarnListener, this);
            this.aTempBanListener = new ATempBanListener(this);
            getServer().getPluginManager().registerEvents(aTempBanListener, this);
            this.aBanListener = new ABanListener(this);
            getServer().getPluginManager().registerEvents(aBanListener, this);
            getLogger().warning("AdvancedBans Logging - [Enabled]");
        }

        if (!syncingConfig.getBoolean("enabled")) {
            getLogger().warning("Discord 2 Game Syncing - [Not Enabled]");
        } else {
            syncStorage = new SyncStorage(syncingConfig.getString("MYSQL.URL"), syncingConfig.getString("MYSQL.USERNAME"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.PASSWORD"));
            syncStorage.initDatabase();
            Objects.requireNonNull(getCommand("sync")).setExecutor(new SyncGameCommand(discordBot, KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.URL"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.USERNAME"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.PASSWORD")));
            getLogger().warning("Discord 2 Game Syncing - [Enabled]");
        }
        //Plugin factionsKore = pluginManager.getPlugin("FactionsKore");
        //if (factionsKore == null) {
        //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
        //} else if (!config.getBoolean("PRINTER-LOGGER.enabled")) {
        //    getLogger().info("FKore Leave Printer Logger - [Not Enabled]");
        //} else {
        //    PrinterFeature printerKore = FactionsKore.get().getFeature(PrinterFeature.class);
        //    printerLeaveLogger = new FKoreLeavePrinterLogger(printerKore, this);
        //    getServer().getPluginManager().registerEvents(printerLeaveLogger, this);
        //    getLogger().info("FKore Leave Printer Logger - [Enabled]");
        //}
        //if (factionsKore == null) {
        //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
        //} else if (!config.getBoolean("PRINTER-LOGGER.enabled")) {
        //    getLogger().info("FKore Enter Printer Logger - [Not Enabled]");
        //} else {
        //    PrinterFeature printerKore = FactionsKore.get().getFeature(PrinterFeature.class);
        //    printerEnterLogger = new FKoreEnterPrinterLogger(printerKore, this);
        //    getServer().getPluginManager().registerEvents(printerEnterLogger, this);
        //    getLogger().info("FKore Enter Printer Logger - [Enabled]");
        //}
        //if (factionsKore == null) {
        //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
        //} else if (!config.getBoolean("FKORE-OVERTAKE-LOGGER.enabled")) {
        //    getLogger().info("FKore FTop Overtake Logger - [Not Enabled]");
        //} else {
        //    FactionsTopFeature factionsTopFeature = FactionsKore.get().getFeature(FactionsTopFeature.class);
        //    overtakeLogger = new FKoreFactionOvertakeLogger(factionsTopFeature, this);
        //    getServer().getPluginManager().registerEvents(overtakeLogger, this);
        //    getLogger().info("FKore FTop Overtake Logger - [Enabled]");
        //}
        this.staffUtilsCommand = new StaffUtilsCommand();
        Objects.requireNonNull(getCommand("stafflogger")).setExecutor(this.staffUtilsCommand);
        new ThreadPoolExecutor(5, 10, 1L, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        Bukkit.getConsoleSender().sendMessage("[KushStaffUtils] Plugin has been enabled");
    }

    public void onDisable() {
        FileConfiguration config = getConfig();
        boolean discordBotEnabled = config.getBoolean("bot.enabled");
        if (!syncingConfig.getBoolean("enabled")) {
            return;
        } else {
            syncStorage.closeConnection();
        }
        if (discordBotEnabled) {
            this.discordBot.stop();
            getLogger().warning("[Discord Bot] Bot has been disabled!");
        } else {
            getLogger().warning("[Discord Bot] Bot is disabled, won't stop.");
        }
        boolean factionsTopAnnouncer = config.getBoolean("announcer.enabled");
        if (factionsTopAnnouncer) {
            this.factionsTopAnnouncer.cancelAnnouncements();
        }
        boolean stopLogger = config.getBoolean("serverstatus.enabled");
        if (stopLogger) {
                startStopLogger.sendStatusUpdateMessage(false);
            }
        Bukkit.getConsoleSender().sendMessage("[KushStaffUtils] Plugin has been disabled!");
    }


    public void reloadConfigOptions() {
        reloadConfig();
        HandlerList.unregisterAll((Listener) this);
        FileConfiguration config = getConfig();
        loadMessagesConfig();
        // Discord Bot Stuff
        if (KushStaffUtils.getInstance().getConfig().getBoolean("bot.enabled")) {
            discordBot.reloadBot();
        }
        // Instance Reloads
        instance = this;

        if (!config.getBoolean("per-user-logging.enabled")) {
            getLogger().warning("Per User Logging - [Not Enabled]");
        } else {
            String logsFolder = (new File(getDataFolder(), "logs")).getPath();
            this.fileCommandLogger = new FileCommandLogger(logsFolder);
            getServer().getPluginManager().registerEvents(fileCommandLogger, this);
            getLogger().warning("Per User Logging - [Enabled]");
        }
        // Chat Webhook (Webhook)
        if (!config.getBoolean("chatwebhook.enabled")) {
            getLogger().warning("Chat Logger - [Not Enabled]");
        } else {
            this.chatWebhook = new ChatWebhook(config);
            getServer().getPluginManager().registerEvents(chatWebhook, this);
            getLogger().warning("Chat Logger - [Enabled]");
        }
        // Start/Stop Logger (Discord Bot Feature)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Start/Stop Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("serverstatus.enabled")) {
            getLogger().warning("Start/Stop Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.startStopLogger = new StartStopLogger(discordBot);
            startStopLogger.sendStatusUpdateMessage(true);
            getLogger().warning("Start/Stop Logger - [Enabled]");
        }
        // Factions/Skyblock Top Announcer (Webhook)
        if (!config.getBoolean("announcer.enabled")) {
            getLogger().warning("Factions Top Announcer - [Not Enabled]");
        } else {
            this.factionsTopAnnouncer = new FactionsTopAnnouncer(config, this);
            Bukkit.getPluginManager().registerEvents(factionsTopAnnouncer, this);
            getLogger().warning("Factions Top Announcer - [Enabled]");
        }
        // Player Report Command (Webhook + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Player Reporting Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("report.enabled")) {
            getLogger().warning("Player Reporting Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.reportCommand = new ReportCommand(this, discordBot);
            Objects.requireNonNull(getCommand("report")).setExecutor(this.reportCommand);
            Bukkit.getPluginManager().registerEvents(reportCommand, this);
            getLogger().warning("Player Reporting Command - [Enabled]");
        }
        // Strike Command (Webhook + Command)
        if (!config.getBoolean("strike.enabled")) {
            getLogger().warning("Strike Command - [Not Enabled]");
        } else {
            this.factionStrike = new FactionStrike(config, this);
            Objects.requireNonNull(getCommand("strike")).setExecutor(this.factionStrike);
            getLogger().warning("Strike Command - [Enabled]");
        }
        // Bug Report Command (Webhook + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Bug Report Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("bug_report.enabled")) {
            getLogger().warning("Bug Report Command - [Not Enabled]");
        } else {
            this.bugCommand = new BugCommand(this, discordBot, config);
            getServer().getPluginManager().registerEvents(this.bugCommand, this);
            Objects.requireNonNull(getCommand("bug")).setExecutor(this.bugCommand);
            getLogger().warning("Bug Command - [Enabled]");
        }
        // Join Leave Logger (Webhooks)
        if (!config.getBoolean("player_leave_join_logger.enabled")) {
            getLogger().warning("Player Join Leave Logger - [Not Enabled]");
        } else {
            this.joinLeaveLogger = new JoinLeaveLogger(config);
            Bukkit.getServer().getPluginManager().registerEvents(this.joinLeaveLogger, this);
            getLogger().warning("Player Join Leave Logger - [Enabled]");
        }
        // Suggestion Command (Discord Bot + Command)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Suggestion Command - [Not Enabled] - (Requires Discord Bot enabled)");
        } else if (!config.getBoolean("suggestion.enabled")) {
            getLogger().warning("Suggestion Command - [Not Enabled]");
        } else {
            this.suggestionCommand = new SuggestionCommand(this.discordBot, config);
            Objects.requireNonNull(getCommand("suggestion")).setExecutor(this.suggestionCommand);
            getServer().getPluginManager().registerEvents(this.suggestionCommand, this);
            getLogger().warning("Suggestion Command - [Enabled]");
        }
        // Command Logger (Discord Feature)
        if (!config.getBoolean("bot.enabled")) {
            getLogger().warning("Command Logger - [Not Enabled] - (Requires Discord Bot enabled)");
        } else {
            this.commandLogger = new CommandLogger(this.discordBot);
            getServer().getPluginManager().registerEvents(this.commandLogger, this);
            getLogger().warning("Command Logger - [Enabled]");
        }

        // Creative Logging (Webhooks)
        if (!config.getBoolean("creative-logging.enabled")) {
            getLogger().warning("Creative Logging - [Not Enabled]");
        } else {
            this.creativeLogger = new CreativeMiddleClickLogger(this);
            this.creativeDropLogger = new CreativeDropLogger(this);
            getServer().getPluginManager().registerEvents(this.creativeLogger, this);
            getServer().getPluginManager().registerEvents(this.creativeDropLogger, this);
            getLogger().warning("Creative Logging - [Enabled]");
        }

        // LiteBans Logging (Webhooks)
        Plugin liteBans = Bukkit.getPluginManager().getPlugin("LiteBans");
        if (liteBans == null) {
            getLogger().warning("LiteBans is not installed or enabled. This feature will not work!");
        } else if (!config.getBoolean("litebans.enabled")) {
            getLogger().warning("LiteBans Logging - [Not Enabled]");
        } else {
            this.lbKickListener = new LBKickListener(this);
            lbKickListener.registerEvents();
            this.lbWarnListener = new LBWarnListener(this);
            lbWarnListener.registerEvents();
            this.lbMuteListener = new LBMuteListener(this);
            lbMuteListener.registerEvents();
            this.bansListener = new LBBanListener(this);
            bansListener.registerEvents();
            getLogger().warning("LiteBans Logging - [Enabled]");
        }

        // Advanced Logging (Webhooks)
        Plugin advancedBans = Bukkit.getPluginManager().getPlugin("AdvancedBans");
        if (advancedBans == null) {
            getLogger().warning("AdvancedBans is not installed or enabled. This feature will not work!");
        } else if (!config.getBoolean("advancedbans.enabled")) {
            getLogger().warning("AdvancedBans Logging - [Not Enabled]");
        } else {
            this.aMuteListener = new AMuteListener(this);
            getServer().getPluginManager().registerEvents(aMuteListener, this);
            this.aIPBanListener = new AIPBanListener(this);
            getServer().getPluginManager().registerEvents(aIPBanListener, this);
            this.aKickListener = new AKickListener(this);
            getServer().getPluginManager().registerEvents(aKickListener, this);
            this.aWarnListener = new AWarnListener(this);
            getServer().getPluginManager().registerEvents(aWarnListener, this);
            this.aTempBanListener = new ATempBanListener(this);
            getServer().getPluginManager().registerEvents(aTempBanListener, this);
            this.aBanListener = new ABanListener(this);
            getServer().getPluginManager().registerEvents(aBanListener, this);
            getLogger().warning("AdvancedBans Logging - [Enabled]");
        }

        if (!syncingConfig.getBoolean("ena//////////////bled")) {
            getLogger().warning("Discord 2 Game Syncing - [Not Enabled]");
        } else {
            syncStorage = new SyncStorage(syncingConfig.getString("MYSQL.URL"), syncingConfig.getString("MYSQL.USERNAME"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.PASSWORD"));
            syncStorage.initDatabase();
            Objects.requireNonNull(getCommand("sync")).setExecutor(new SyncGameCommand(discordBot, KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.URL"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.USERNAME"), KushStaffUtils.getInstance().syncingConfig.getString("MYSQL.PASSWORD")));
            getLogger().warning("Discord 2 Game Syncing - [Enabled]");
        }
       //Plugin factionsKore = Bukkit.getPluginManager().getPlugin("FactionsKore");
       //if (factionsKore == null) {
       //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
       //} else if (!config.getBoolean("PRINTER-LOGGER.enabled")) {
       //    getLogger().info("FKore Leave Printer Logger - [Not Enabled]");
       //} else {
       //    PrinterFeature printerKore = FactionsKore.get().getFeature(PrinterFeature.class);
       //    printerLeaveLogger = new FKoreLeavePrinterLogger(printerKore, this);
       //    getServer().getPluginManager().registerEvents(printerLeaveLogger, this);
       //    getLogger().info("FKore Leave Printer Logger - [Enabled]");
       //}
       //if (factionsKore == null) {
       //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
       //} else if (!config.getBoolean("FKORE-OVERTAKE-LOGGER.enabled")) {
       //    getLogger().info("FKore FTop Overtake Logger - [Not Enabled]");
       //} else {
       //    FactionsTopFeature factionsTopFeature = FactionsKore.get().getFeature(FactionsTopFeature.class);
       //    overtakeLogger = new FKoreFactionOvertakeLogger(factionsTopFeature, this);
       //    getServer().getPluginManager().registerEvents(overtakeLogger, this);
       //    getLogger().info("FKore FTop Overtake Logger - [Enabled]");
       //}
       //if (factionsKore == null) {
       //    getLogger().warning("FactionsKore is not installed or enabled. This feature will not work!");
       //} else if (!config.getBoolean("PRINTER-LOGGER.enabled")) {
       //    getLogger().info("FKore Enter Printer Logger - [Not Enabled]");
       //} else {
       //    PrinterFeature printerKore = FactionsKore.get().getFeature(PrinterFeature.class);
       //    printerEnterLogger = new FKoreEnterPrinterLogger(printerKore, this);
       //    getServer().getPluginManager().registerEvents(printerEnterLogger, this);
       //    getLogger().info("FKore Enter Printer Logger - [Enabled]");
       //}
        Bukkit.getConsoleSender().sendMessage("[KushStaffUtils] Config options have been reloaded!");
    }

    private FileConfiguration loadSyncingConfig() {
        saveResource("syncing.yml", false);

        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "syncing.yml"));
    }

    private void setSyncingConfig() {
        syncingConfig.options().copyDefaults(true);
        saveSyncingConfig();

    }

    private void saveSyncingConfig() {
        try {
            syncingConfig.save(new File(getDataFolder(), "syncing.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private FileConfiguration loadMessagesConfig() {
        saveResource("messages.yml", false);

        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    private void setDefaultMessages() {
        messagesConfig.addDefault("reloadMessage", "&c[&c&ᴋᴜѕʜѕᴛᴀꜰꜰᴜᴛɪʟѕ&c] &8» &dThe config files have been reloaded!");
        messagesConfig.addDefault("noPermissionMessage", "&c[&c&ᴋᴜѕʜѕᴛᴀꜰꜰᴜᴛɪʟѕ&c] &8» &cYou do not have permission to &d/stafflogger&f!");

        messagesConfig.options().copyDefaults(true);
        saveMessagesConfig();
    }

    private void saveMessagesConfig() {
        try {
            messagesConfig.save(new File(getDataFolder(), "messages.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setBotMessages() {
        discordBotConfig.addDefault("bot.factionTopCommandRoleID", "faction-top-command-role-id");

        discordBotConfig.options().copyDefaults(true);
        saveBotConfig();
    }

    public void saveBotConfig() {
        try {
            discordBotConfig.save(new File(getDataFolder(), "discord-bot.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public FileConfiguration loadBotConfig() {
        saveResource("discord-bot.yml", false);

        return YamlConfiguration.loadConfiguration(new File(getDataFolder(), "discord-bot.yml"));
    }



    public static String getCommandLoggerFolder() {
        return logsFolder;
    }


    public static KushStaffUtils getInstance() {
        return instance;
    }

    public static FactionsKore getFKoreInstance() {
        return factionsKore;
    }
}
