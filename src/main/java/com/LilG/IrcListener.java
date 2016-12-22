package com.LilG;

import com.google.common.collect.ImmutableSortedSet;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericChannelEvent;

import java.util.List;

import static com.LilG.utils.LilGUtil.startsWithAny;

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
        String message = event.getMessage();
        TextChannel channel = getDiscordChannel(event);

        if (startsWithAny(message, Main.config[configID].commandCharacters.toArray(new String[]{}))) {
            channel.sendMessage(
                    String.format("_Command Sent by_ `%s%s`",
                            getUserSymbol(event),
                            event.getUser().getHostmask()
                    )
            ).queue(
                    message1 -> channel.sendMessage(formatString(channel, message)).queue()
            );
        } else {
            channel.sendMessage(
                    String.format("**<%s%s>** %s",
                            getUserSymbol(event),
                            event.getUser().getNick(),
                            formatString(channel, message)
                    )
            ).queue();
        }
    }

    public void onNotice(NoticeEvent event) {
        String message = event.getMessage();
        //noinspection ConstantConditions
        if (event.getUser() == null) {
            return;
        }
        if (message.contains("\u0001AVATAR")) {
            event.getUser().send().notice("\u0001AVATAR " + Main.jda.getSelfUser().getAvatarUrl() + "\u0001");
        }
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

    public String getUserSymbol(MessageEvent event) {
        return getUserSymbol(event, event.getChannel(), event.getUser());
    }

    public String getUserSymbol(MessageEvent event, User user) {
        return getUserSymbol(event, event.getChannel(), user);
    }

    public String getUserSymbol(MessageEvent event, Channel channel) {
        return getUserSymbol(event, channel, event.getUser());
    }

    public String getUserSymbol(MessageEvent event, Channel channel, User user) {
        ImmutableSortedSet<UserLevel> userLevels = user.getUserLevels(channel);
        UserLevel topUserLevel = null;
        for (UserLevel userLevel : userLevels) {
            if (topUserLevel != null) {
                if (topUserLevel.ordinal() < userLevel.ordinal()) {
                    topUserLevel = userLevel;
                }
            } else {
                topUserLevel = userLevel;
            }
        }
        if (topUserLevel == null) {
            return "";
        }
        return topUserLevel.getSymbol();
    }

    private String formatString(TextChannel channel, String strToFormat) {
        final char underline = '\u001F';
        final char italics = '\u001D';
        final char bold = '\u0002';
        int underlineCount = StringUtils.countMatches(strToFormat, underline);
        int italicsCount = StringUtils.countMatches(strToFormat, italics);
        int boldCount = StringUtils.countMatches(strToFormat, bold);
        if (underlineCount != 0) {
            strToFormat = strToFormat.replace(underline + "", "__");
            if (underlineCount % 2 != 0) {
                strToFormat += "__";
            }
        }
        if (italicsCount != 0) {
            strToFormat = strToFormat.replace(italics + "", "_");
            if (italicsCount % 2 != 0) {
                strToFormat += "_";
            }
        }
        if (boldCount != 0) {
            strToFormat = strToFormat.replace(bold + "", "**");
            if (boldCount % 2 != 0) {
                strToFormat += "**";
            }
        }
        if (strToFormat.contains("@")) {
            String strLower = strToFormat.toLowerCase();
            for (Member member : channel.getMembers()) {
                String memberName = member.getEffectiveName().toLowerCase();
                while (strLower.contains(memberName)) {
                    int index = strLower.indexOf(memberName);
                    strToFormat = strToFormat.substring(0, index - 1) +
                            member.getAsMention() +
                            strToFormat.substring(index + memberName.length());
                    strLower = strToFormat.toLowerCase();
                }
            }
        }
        return strToFormat;
    }
}
