package com.LilG;

import com.LilG.utils.LilGUtil;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Created by lil-g on 12/12/16.
 */
public class DiscordListener extends ListenerAdapter {
    private final static char colorCode = '\u0003';
    private final byte configID;

    DiscordListener(byte configID) {
        this.configID = configID;
    }

    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getMember().equals(event.getGuild().getSelfMember())) {
            return;
        }
        for (String discordChannels : Main.config[configID].channelMapping.keySet()) {
            if (discordChannels.substring(1, discordChannels.length()).equals(event.getChannel().getName())) {
                String color = "";
                if (Main.config[configID].ircNickColor) {
                    color = colorCode + String.format("%02d", LilGUtil.hash(event.getMember().getEffectiveName(), 12) + 2);
                }
                Main.pircBotX.send()
                        .message(Main.config[configID]
                                        .channelMapping
                                        .get(discordChannels),
                                String.format("<%s%s%c> %s",
                                        color,
                                        event.getMember().getEffectiveName(),
                                        colorCode,
                                        event.getMessage().getContent()
                                                .replace('\u0007', '␇')
                                                .replace('\n', '␤')
                                                .replace('\r', '␍')
                                )
                        )
                ;
            }
        }
    }
}
