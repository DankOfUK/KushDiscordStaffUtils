# KushStaffUtils

A configurable staff-utility and Discord-integration plugin for Spigot/Paper servers. It bridges
your Minecraft server and a Discord bot/webhooks — logging, moderation tools, account syncing, and
timed rewards — all driven by config.

> Made by **Exotic Development** · Author: **DankOfUK**

- Spigot: https://www.spigotmc.org/resources/kushstaffutils-1-8-1-19-fully-configurable.107607
- Discord: http://discord.gg/2xYgHUfubM

---

## Compatibility

| | |
|---|---|
| **Minecraft** | Spigot / Paper / Purpur **1.8 → 1.21.x** (single jar) |
| **Java** | Compiled to **Java 8** bytecode (runs on legacy and modern servers) |
| **Optional hooks** | PlaceholderAPI, Vault, LiteBans, AdvancedBan, InteractiveChat, AntiCheatReplay |

Version-specific API (e.g. renamed potion effects) is resolved at runtime, so the same jar works
across the whole range. `api-version` is intentionally left out of `plugin.yml` to preserve 1.8
loading.

---

## Features

**Moderation**
- **Freeze** — freezes a player (blindness + slowness, movement lock, freeze GUI, block/chat/command
  restrictions), auto-punish on logout.
- **Player reports** (`/report`) — sends reports to Discord.
- **Faction strikes** (`/strike`) — strike a group/island/faction.
- **Bug reports** (`/bug`) and **suggestions** (`/suggestion`) posted to Discord.

**Logging**
- **Per-user command logging** to files, viewable in-game (`/viewlogs`) with clickable pagination.
- **Command logger** — logs player commands to Discord (with ignore/whitelist lists).
- **Chat webhook** — mirrors in-game chat to a Discord webhook.
- **Join/Leave logger** — posts join/leave messages to a webhook (PlaceholderAPI-aware).
- **Creative logging** — logs item drops and creative middle-click grabs.
- **Ban-plugin logging** — pushes **LiteBans** and **AdvancedBan** ban/tempban/ipban/mute/warn/kick
  events to Discord webhooks.
- **Start/Stop logger** — announces server start/shutdown in Discord.
- **Factions Top announcer** — periodic FTop announcements.

**Discord bot** (JDA)
- Slash commands (see below), a Discord→game chat bridge, and server-control from Discord.

**Account Sync** *(new)* — link a Discord account to a Minecraft account via a one-time code, granting
Discord roles and running in-game commands. See [Account Sync](#account-sync).

**Timed Rewards** *(new)* — the bot posts a reward panel with claim buttons; synced players claim
in-game rewards on a per-reward cooldown. See [Timed Rewards](#timed-rewards).

---

## In-game commands & permissions

| Command | Description | Permission |
|---|---|---|
| `/stafflogger reload` | Reload the plugin config | `commandlogger.reload` |
| `/viewlogs <player> [page]` | View a player's logged commands | `commandlogger.viewlogs.use` |
| `/freeze <player>` | Freeze / unfreeze a player | `commandlogger.freeze.use` |
| `/report <player> <reason>` | Report a player to Discord | `commandlogger.report.use` |
| `/strike ...` (alias `/strikes`) | Strike a group/island/faction | `commandlogger.strike.use` |
| `/bug <message>` | Report a bug to Discord | `commandlogger.bug.use` |
| `/suggestion <text>` (alias `/suggest`) | Submit a suggestion | `commandlogger.suggest.use` |
| `/sync <code>` | Link your account with a Discord code | *(none — any player)* |

**Passive permission nodes**
- `commandlogger.log` — this player's commands are logged to Discord.
- `commandlogger.bypass` — exclude this player from command logging.
- `commandlogger.creative-logging.log` — this player's creative drops/grabs are logged.

---

## Discord slash commands

Registered **per-guild** (available instantly). Admin-only commands require the role in
`bot.adminRoleID`, or Discord **Administrator** permission if that's unset.

| Command | Description | Admin |
|---|---|---|
| `/help` | List the bot's commands | |
| `/online` | List online players | |
| `/serverinfo` | Server/guild info | |
| `/ftop` | Post FTop data | |
| `/logs <user>` | Fetch a player's logs | |
| `/avatar <user>` | Show a user's avatar | |
| `/command <command>` | Run a console command on the server | ✅ |
| `/sendsyncpanel <channel>` | Post the account-sync panel | ✅ |
| `/sendrewardpanel <channel>` | Post the reward panel | ✅ |
| `/unsync <user>` | Remove a user's account link | ✅ |

---

## Account Sync

**Flow**
1. An admin runs `/sendsyncpanel #channel` → the bot posts an embed with a button.
2. A user clicks the button → receives a one-time code (valid for `CODE-EXPIRY-MINUTES`).
3. In-game they run `/sync <code>` → their accounts are linked, they receive the configured Discord
   roles, and the configured in-game commands run.
4. `/unsync <user>` (admin) removes the link and strips the sync roles.

**Config** (`syncing.yml`)
```yaml
SYNC-PANEL:
  ROLE-GIVEN-ON-SYNC:        # Discord role IDs granted on sync
    - "111111111111111111"
  EMBED-MESSAGE:             # panel embed body (one entry per line)
    - "Kush Rewards - Syncing Panel"
    - "Click to start the sync process!"
  THUMBNAIL-URL: "https://example.com/image.png"
  BUTTON-MESSAGE: "Click here!"
  SENT-MESSAGE: "Sync Panel sent to %channel%"
  INVALID-CHANNEL-MESSAGE: "Invalid Channel!"
  CODE-EXPIRY-MINUTES: 5     # how long a code stays valid
  COMMANDS-ON-SYNC:          # console commands run on sync (%player%, %uuid%)
    - "lp user %player% parent addtemp synced 30d"

MESSAGES:                    # in-game /sync messages (support & and &#RRGGBB colors)
  COMMAND-USAGE: "&cUse /sync <code>!"
  INVALID-CODE-MESSAGE: "&cYour code is invalid or expired!"
  ALREADY-SYNCED-MESSAGE: "&cYou have already been synced!"
  SYNCED-SUCCESSFULLY-MESSAGE: "&aYou have been successfully synced!"
```

---

## Timed Rewards

**Flow**
1. The bot posts the reward panel (auto every `POST-INTERVAL` minutes if `AUTO-POST: true`, or on
   demand via `/sendrewardpanel #channel`) with one claim button per entry in `BUTTONS`.
2. A user clicks a reward button. The plugin checks: **synced → has the required role → online
   in-game → off cooldown**, then runs that reward's commands.
3. Each reward can be claimed once per its `REWARD-INTERVAL` (**seconds**).

**Config** (`syncing.yml`)
```yaml
REWARD-PANEL:
  CHANNEL-ID: "123456789012345678"   # channel for the auto-post
  POST-INTERVAL: 30                  # minutes between auto-posts
  AUTO-POST: false                   # enable the recurring auto-post

REWARD-EMBED:
  TITLE: "Rewards"
  DESCRIPTION:
    - "Claim your rewards below!"
  THUMBNAIL-URL: "https://example.com/image.png"
  COLOR: "#FFA500"

BUTTONS:
  button1:
    REQUIRED-ROLE-ID: "111111111111111111"  # role required to claim
    REWARDS:                                 # console commands (%player%, %uuid%)
      - "give %player% diamond 5"
    REWARD-INTERVAL: 120                     # cooldown in SECONDS
    MESSAGE: "Claim Diamonds"                # button label
```

> Rewards require the player to be **online** (so item commands succeed) and **synced** (so the bot
> knows which player to reward).

---

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Main config — feature toggles, bot settings, webhooks, messages |
| `messages.yml` | Reload / no-permission messages |
| `discord-bot.yml` | Bot-only settings (e.g. FTop command role) |
| `syncing.yml` | Account-sync and reward settings |
| `data.yml` | *(auto-generated)* account links + reward cooldowns |

Key `config.yml` settings for Discord features: `bot.enabled`, `bot.discord_token`,
`bot.adminRoleID`. Reload most settings with `/stafflogger reload`.

> Note: the `MYSQL` and `SYNC-RANKS` sections in `syncing.yml` are unused — storage is the
> flat-file `data.yml`.

---

## Soft dependencies

`PlaceholderAPI`, `Vault`, `LiteBans`, `AdvancedBan`, `InteractiveChat`, `AntiCheatReplay`. All are
optional; the related features simply stay disabled if the plugin isn't present. (Group placeholders
for chat logging require a Vault-compatible permissions plugin such as LuckPerms.)

---

## Building

Requires JDK 8+ and Maven. Produces a shaded jar (bundles JDA) targeting Java 8.

```bash
mvn clean package
```

Output: `target/KushStaffUtils-<version>.jar`.

---

## Credits

- **Author:** DankOfUK — Exotic Development
- Built with [JDA](https://github.com/discord-jda/JDA), bStats, and the Spigot/Bukkit API.
