package me.dankofuk.discord.commands;

import me.dankofuk.discord.DiscordBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class HelpCommand extends ListenerAdapter {
    public DiscordBot discordBot;

    public HelpCommand(DiscordBot discordBot) {
        this.discordBot = discordBot;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("help")) {

                EmbedBuilder helpEmbed = new EmbedBuilder();
                helpEmbed.setColor(Color.RED);
                helpEmbed.setTitle("__`Help Page 1/1`__");
                helpEmbed.setDescription("Command List");

                helpEmbed.addField("/help", "Shows this menu", false);
                helpEmbed.addField("/online", "Shows the players online", true);
                helpEmbed.addField("/serverinfo", "Shows information about this server", true);
                helpEmbed.addField("/ftop", "Posts the FTop data", true);
                helpEmbed.addField("/command [command]", "Runs a console command on the server (admin)", true);
                helpEmbed.addField("/logs [user]", "Shows the log file for the user selected (admin)", true);
                helpEmbed.addField("/avatar [user]", "Shows the avatar for the user selected", true);
                helpEmbed.addField("/sendverifypanel [channel]", "Sends the verify panel to a channel (admin)", true);
                helpEmbed.addField("/sendsyncpanel [channel]", "Sends the account-sync panel to a channel (admin)", true);
                helpEmbed.addField("/sendrewardpanel [channel]", "Sends the reward panel to a channel (admin)", true);
                helpEmbed.addField("/unsync [user]", "Removes a user's account link (admin)", true);

                helpEmbed.setFooter("Help Page 1/1 - Made by Exotic Development");
                event.replyEmbeds(helpEmbed.build()).setEphemeral(true).queue();
        }
    }
}
