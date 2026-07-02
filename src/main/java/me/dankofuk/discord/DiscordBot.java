package me.dankofuk.discord;

import me.dankofuk.KushStaffUtils;
import me.dankofuk.discord.commands.*;
import me.dankofuk.discord.listeners.CommandLogger;
import me.dankofuk.discord.listeners.DiscordChat2Game;
import me.dankofuk.discord.listeners.StartStopLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DiscordBot extends ListenerAdapter {
    public JDA jda;
    public Plugin botTask;
    public KushStaffUtils main;
    public FileConfiguration config;


    public DiscordBot(Plugin botTask, FileConfiguration config) {
        this.botTask = botTask;
        this.config = config;
    }

    public JDA getJda() {
        return this.jda;
    }

    public void start() throws InterruptedException {
        if (!KushStaffUtils.getInstance().getConfig().getBoolean("bot.enabled"))
            return;

        String activityTypeStr = KushStaffUtils.getInstance().getConfig().getString("bot.discord_activity_type");
        Activity.ActivityType activityType = getActivityType(activityTypeStr);

        if (activityType == null) {
            System.err.println("Invalid bot.discord_activity_type. Valid options are:");
            for (Activity.ActivityType validType : Activity.ActivityType.values()) {
                System.err.println(validType.name());
            }
            return;
        }

        this.jda = JDABuilder.createDefault(KushStaffUtils.getInstance().getConfig().getString("bot.discord_token"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.DIRECT_MESSAGE_REACTIONS,
                        GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .setActivity(Activity.of(activityType, Objects.requireNonNull(KushStaffUtils.getInstance().getConfig().getString("bot.discord_activity"))))
                .build()
                .awaitReady();


        // Register Events/Listeners
        this.jda.addEventListener(new HelpCommand(this));
        this.jda.addEventListener(new OnlinePlayersCommand(this));
        this.jda.addEventListener(new StartStopLogger(this));
        this.jda.addEventListener(new ConsoleCommand(this));
        this.jda.addEventListener(new HelpCommand(this));
        this.jda.addEventListener(new LogsCommand(this));
        this.jda.addEventListener(new CommandLogger(this));
        this.jda.addEventListener(new DiscordChat2Game(main, config));
        this.jda.addEventListener(new AvatarCommand());
        this.jda.addEventListener(new ServerInfoCommand());
        this.jda.addEventListener(new FTopCommand(this));
        this.jda.addEventListener(new me.dankofuk.sync.SyncRewardListener(this));
        //this.jda.addEventListener(new TicketSystem(this));
    }

    private Activity.ActivityType getActivityType(String activityTypeStr) {
        try {
            return Activity.ActivityType.valueOf(activityTypeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Builds the full slash-command list. Registered per-guild (instant) rather than globally
    // (which can take up to an hour to propagate) so commands are usable right after startup.
    private List<CommandData> buildCommands() {
        List<CommandData> commandsData = new ArrayList<>();
        commandsData.add(Commands.slash("help", "Shows the list of all commands in this bot."));
        commandsData.add(Commands.slash("online", "Lists Online Players."));
        commandsData.add(Commands.slash("serverinfo", "Guild Info for this server."));
        commandsData.add(Commands.slash("ftop", "Sends the FTop data to current server."));
        commandsData.add(Commands.slash("command", "Sends the command to the server.").addOption(OptionType.STRING, "command", "The command you want to send."));
        commandsData.add(Commands.slash("logs", "Gets the logs for the user you enter.").addOption(OptionType.STRING, "user", "The user you would like the logs for."));
        commandsData.add(Commands.slash("avatar", "Gets the avatar of a user.").addOption(OptionType.USER, "user", "The user that the avatar for."));
        commandsData.add(Commands.slash("sendsyncpanel", "Sends the sync panel to the channel you select").addOption(OptionType.CHANNEL, "channel", "The channel to send the panel to."));
        commandsData.add(Commands.slash("sendrewardpanel", "Sends the reward panel to the channel you select").addOption(OptionType.CHANNEL, "channel", "The channel to send the panel to."));
        commandsData.add(Commands.slash("unsync", "Unsync a user that has been synced").addOption(OptionType.USER, "user", "User you want to unsync"));
        return commandsData;
    }

    public void onGuildReady(@NotNull GuildReadyEvent event) {
        // Guild-scoped registration: available immediately in this guild.
        event.getGuild().updateCommands().addCommands(buildCommands()).queue(
                success -> KushStaffUtils.getInstance().getLogger()
                        .info("[Discord Bot] Registered " + success.size() + " slash commands in guild " + event.getGuild().getName()),
                error -> KushStaffUtils.getInstance().getLogger()
                        .warning("[Discord Bot] Failed to register slash commands: " + error.getMessage()));
    }

    public void onReady(@NotNull ReadyEvent event) {
        // Clear any stale GLOBAL commands registered by older versions so users don't see
        // duplicates alongside the per-guild commands above.
        event.getJDA().updateCommands().queue();
    }


    public void stop() {
        if (this.jda != null) {
            this.jda.shutdown();
            Bukkit.getScheduler().getPendingTasks().stream()
                    .filter(task -> (task.getOwner() == this.botTask))
                    .forEach(BukkitTask::cancel);
        }
    }

    public void accessConfigs() {
        String discordToken = KushStaffUtils.getInstance().getConfig().getString("bot.discord_token");
        boolean discordBotEnabled = KushStaffUtils.getInstance().getConfig().getBoolean("bot.enabled");
        String discordActivity = KushStaffUtils.getInstance().getConfig().getString("bot.discord_activity");
        String adminRoleId = KushStaffUtils.getInstance().getConfig().getString("bot.adminRoleID");
        // Discord Chat 2 Game Chat
        boolean enabled = KushStaffUtils.getInstance().getConfig().getBoolean("discord2game.enabled");
        String channelId = KushStaffUtils.getInstance().getConfig().getString("discord2game.channelId");
        String roleId = KushStaffUtils.getInstance().getConfig().getString("discord2game.roleId");
        boolean roleIdRequired = KushStaffUtils.getInstance().getConfig().getBoolean("discord2game.roleIdRequired");
        String format = KushStaffUtils.getInstance().getConfig().getString("discord2game.message");
        // Start/Stop Logger
        String ServerStatusChannelID = KushStaffUtils.getInstance().getConfig().getString("serverstatus.channelId");
        // Command Logger
        String serverName = KushStaffUtils.getInstance().getConfig().getString("commandlogger.server_name");
        List<String> messageFormats = KushStaffUtils.getInstance().getConfig().getStringList("commandlogger.message_formats");
        List<String> embedTitleFormats = KushStaffUtils.getInstance().getConfig().getStringList("commandlogger.embed_title_formats");
        List<String> ignoredCommands = KushStaffUtils.getInstance().getConfig().getStringList("commandlogger.ignored_commands");
        List<String> whitelistedCommands = KushStaffUtils.getInstance().getConfig().getStringList("commandlogger.whitelisted_commands");
        boolean logAsEmbed = KushStaffUtils.getInstance().getConfig().getBoolean("commandlogger.logAsEmbed");
        boolean whitelistEnabled = KushStaffUtils.getInstance().getConfig().getBoolean("commandlogger.whitelist_enabled");
        String logChannelId = KushStaffUtils.getInstance().getConfig().getString("commandlogger.channel_id");
        // Online Players
        String titleFormat = KushStaffUtils.getInstance().getConfig().getString("online_players.title");
        String footerFormat = KushStaffUtils.getInstance().getConfig().getString("online_players.footer");
        String listThumbnailUrl = KushStaffUtils.getInstance().getConfig().getString("online_players.thumbnailUrl");
        String noPlayersTitle = KushStaffUtils.getInstance().getConfig().getString("online_players.noPlayersTitle");
        boolean requireAdminRole = KushStaffUtils.getInstance().getConfig().getBoolean("online_players.requireAdminRole");

    }

    public void reloadBot() {
        stop();
        accessConfigs();
        if (KushStaffUtils.getInstance().getConfig().getBoolean("bot.enabled")) {
            if ("false".equals(KushStaffUtils.getInstance().getConfig().getString("bot.discord_token")) || Objects.requireNonNull(KushStaffUtils.getInstance().getConfig().getString("bot.discord_token")).isEmpty()) {
                Bukkit.getLogger().warning("[Discord Bot] No bot token found. Bot initialization skipped.");
                return;
            }
            try {
                start();
                Bukkit.getLogger().warning("[Discord Bot] Starting Discord Bot...");
            } catch (InterruptedException e) {
                Bukkit.getLogger().warning("[Discord Bot] Error starting bot.");
            }
        } else {
            Bukkit.getLogger().warning("[Discord Bot] Bot is disabled. Skipping initialization...");
        }
    }

    public Server getMinecraftServer() {
        return Bukkit.getServer();
    }

    public String getAdminRoleID() {
        return KushStaffUtils.getInstance().getConfig().getString("bot.adminRoleID");
    }

    public String getFactionTopCommandRoleID() {
        return KushStaffUtils.getInstance().discordBotConfig.getString("bot.factionTopCommandRoleID");
    }
}
