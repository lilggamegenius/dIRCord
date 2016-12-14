package com.LilG;

import com.LilG.utils.LilGUtil;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.awt.*;

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
                    int ircColorCode = ColorMap.valueOf(event.getMember().getColor());
                    if (ircColorCode < 0) {
                        ircColorCode = LilGUtil.hash(event.getMember().getEffectiveName(), 12) + 2;
                    }
                    color = colorCode + String.format("%02d", ircColorCode);
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

    enum ColorMap {
        turqoise(10, new Color(26, 188, 156)),
        darkTurqoise(10, new Color(17, 128, 106)),
        green(9, new Color(46, 204, 113)),
        darkGreen(3, new Color(31, 139, 76)),
        blue(10, new Color(52, 152, 219)),
        darkBlue(2, new Color(32, 102, 148)),
        purple(13, new Color(155, 89, 182)),
        darkPurple(6, new Color(113, 54, 138)),
        pink(13, new Color(233, 30, 99)),
        darkPink(6, new Color(173, 20, 87)),
        yellow(8, new Color(241, 196, 15)),
        darkYellow(8, new Color(194, 124, 14)),
        orange(7, new Color(230, 126, 34)),
        darkOrange(7, new Color(168, 67, 0)),
        red(4, new Color(231, 76, 60)),
        darkRed(5, new Color(153, 45, 34)),
        lightGray(0, new Color(149, 165, 166)),
        gray(15, new Color(151, 156, 159)),
        darkGray(14, new Color(96, 125, 139)),
        darkerGray(1, new Color(84, 110, 122));

        byte ircColor;
        Color color;

        ColorMap(int ircColor, Color color) {
            this.ircColor = (byte) ircColor;
            this.color = color;
        }

        static int valueOf(Color color) {
            for (ColorMap map : ColorMap.values()) {
                if (map.color.equals(color)) {
                    return map.ircColor;
                }
            }
            return -1;
        }
    }
}
