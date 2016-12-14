package com.LilG;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericChannelEvent;

import java.util.List;

/**
 * Created by lil-g on 12/12/16.
 */
public class IrcListener extends ListenerAdapter {
    private final byte configID;

    IrcListener(byte configID) {
        this.configID = configID;
    }

    public void onConnect(ConnectEvent event) {
        Main.pircBotX = event.getBot();
    }

    @Override
    public void onMessage(MessageEvent event) {
        Main.pircBotX = event.getBot();
        getDiscordChannel(event).sendMessage(
                String.format("**<%s>** %s",
                        event.getUser().getNick(),
                        event.getMessage()
                )
        ).queue();
    }

    @Override
    public void onJoin(JoinEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        event.getUser().getNick(),
                        "Joined"
                )
        ).queue();
    }

    @Override
    public void onPart(PartEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        event.getUser().getNick(),
                        "Left"
                )
        ).queue();
    }

    @Override
    public void onKick(KickEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        event.getUser().getNick(),
                        "Kicked"
                )
        ).queue();
    }

    @Override
    public void onAction(ActionEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        event.getUser().getNick(),
                        event.getMessage()
                )
        ).queue();
    }

    @Override
    public void onMode(ModeEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        event.getUser().getNick(),
                        "Set mode " + event.getMode()
                )
        ).queue();
    }

    @Override
    public void onQuit(QuitEvent event) {
        for (String DiscordChannels : Main.config[configID].channelMapping.keySet()) {
            for (Guild guild : Main.jda.getGuilds()) {
                List<TextChannel> channelList = guild.getTextChannelsByName(DiscordChannels.replace("#", ""), true);
                if (!channelList.isEmpty()) {
                    for (TextChannel channel : channelList) {
                        channel.sendMessage(
                                String.format("**\\*%s\\*** _%s_",
                                        event.getUser().getNick(),
                                        "has quit: " + event.getReason()
                                )
                        ).queue();
                    }
                }
            }
        }
    }

    @Override
    public void onTopic(TopicEvent event) {

    }

    public TextChannel getDiscordChannel(GenericChannelEvent event) {
        for (String DiscordChannels : Main.config[configID].channelMapping.keySet()) {
            if (Main.config[configID].channelMapping.get(DiscordChannels).equalsIgnoreCase(event.getChannel().getName())) {
                for (Guild guild : Main.jda.getGuilds()) {
                    List<TextChannel> channelList = guild.getTextChannelsByName(DiscordChannels.replace("#", ""), true);
                    if (!channelList.isEmpty()) {
                        return channelList.get(0);
                    }
                }
            }
        }
        return null;
    }
}
