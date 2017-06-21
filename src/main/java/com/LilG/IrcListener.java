package com.LilG;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.LilG.utils.LilGUtil;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSortedSet;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericChannelEvent;
import org.slf4j.LoggerFactory;

import static com.LilG.Bridge.formatString;
import static com.LilG.utils.LilGUtil.startsWithAny;

/**
 * Created by lil-g on 12/12/16.
 */
public class IrcListener extends ListenerAdapter {
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(IrcListener.class);
    private final byte configID;
    public volatile boolean ready;

    IrcListener(byte configID) {
        this.configID = configID;
    }

	public static String getUserSymbol(MessageEvent event) {
		return getUserSymbol(event, event.getChannel(), event.getUser());
	}

	public static String getUserSymbol(MessageEvent event, User user) {
		return getUserSymbol(event, event.getChannel(), user);
	}

	public static String getUserSymbol(MessageEvent event, Channel channel) {
		return getUserSymbol(event, channel, event.getUser());
	}

	public static String getUserSymbol(MessageEvent event, Channel channel, User user) {
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

	private static String formatName(User user) {
		return formatName(user, false);
	}

	private static String formatName(User user, boolean useHostmask) {
		String ret = user.getNick();
		if (useHostmask) {
			ret = user.getHostmask();
		}
		if (user.isIrcop()) {
			return "__" + ret + "__";
		}
		return ret;
	}

	public boolean handleCommand(MessageEvent event) {
		String[] message = LilGUtil.splitMessage(event.getMessage());
		if (message.length > 0) {
			if (message[0].startsWith(event.getBot().getNick())) {
				if (getDiscordChannel(event) != null) {
					Bridge.handleCommand(message, event, configID, true);
					return true;
				}
			}
		}
		return false;
	}

    @Override
    public void onEvent(Event event) throws Exception {
        super.onEvent(event);
        fillChannelMap();
    }

    public void onConnect(ConnectEvent event) {
        Main.config[configID].pircBotX = event.getBot();
        for (String string : Main.config[configID].autoSendCommands) {
            event.getBot().sendRaw().rawLine(string);
        }
        ready = true;
    }

    @Override
    public void onMessage(MessageEvent event) {
	    try {
		    Main.config[configID].pircBotX = event.getBot();
		    String message = event.getMessage();
		    TextChannel channel = getDiscordChannel(event);
		    for (int tries = 0; channel == null; tries++) {
			    if (tries > 10) {
				    event.respond("Failed sending message to discord, tell Lil-G about it");
				    return;
			    }
			    channel = getDiscordChannel(event);
		    }
		    if (startsWithAny(message, Main.config[configID].commandCharacters.toArray(new String[]{}))) {
			    TextChannel finalChannel = channel;
			    channel.sendMessage(
					    String.format("_Command Sent by_ `%s%s`",
							    getUserSymbol(event),
							    formatName(event.getUser(), true)
					    )
			    ).queue(
					    message1 -> finalChannel.sendMessage(formatString(finalChannel, message)).queue()
			    );
		    } else {
			    if (!handleCommand(event)) {
				    channel.sendMessage(
						    String.format("**<%s%s>** %s",
								    getUserSymbol(event),
								    formatName(event.getUser()),
								    formatString(channel, message)
						    )
				    ).queue();
			    }
		    }
	    } catch (Exception e) {
		    LOGGER.error("Error in IrcListener\n", e);
	    }
        Main.lastActivity = System.currentTimeMillis();
    }

    public void onNotice(NoticeEvent event) {
        String message = event.getMessage();
        //noinspection ConstantConditions
        if (event.getUser() == null) {
            return;
        }
        if (message.contains("\u0001AVATAR")) {
            event.getUser().send().notice("\u0001AVATAR " + Main.config[configID].jda.getSelfUser().getAvatarUrl() + "\u0001");
        }
    }

    @Override
    public void onJoin(JoinEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        formatName(event.getUser()),
                        "Joined"
                )
        ).queue();
    }

    @Override
    public void onPart(PartEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _Left_ Reason: %s",
                        formatName(event.getUser()),
                        event.getReason()
                )
        ).queue();
    }

    @Override
    public void onKick(KickEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** Kicked _%s_: %s",
                        formatName(event.getUser()),
                        formatName(event.getRecipient()),
                        event.getReason()
                )
        ).queue();
    }

    @Override
    public void onAction(ActionEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        formatName(event.getUser()),
                        event.getMessage()
                )
        ).queue();
    }

    @Override
    public void onMode(ModeEvent event) {
        getDiscordChannel(event).sendMessage(
                String.format("**\\*%s\\*** _%s_",
                        formatName(event.getUser()),
                        "Set mode " + event.getMode()
                )
        ).queue();
    }

    @Override
    public void onQuit(QuitEvent event) {
        LOGGER.setLevel(Level.ALL);
        for (Channel channel : event.getUser().getChannels()) {
            TextChannel textChannel = Main.config[configID].channelMapObj.inverse().get(channel);
            textChannel.sendMessage(
                    String.format("**\\*%s\\*** _%s_",
                            formatName(event.getUser()),
		                    "has quit: " + event.getReason()
				                    .replace("http://www.mibbit.com", "<http://www.mibbit.com>")
				                    .replace("http://www.androirc.com/", "<http://www.androirc.com/>")
                    )
            ).queue();
        }
    }

    @Override
    public void onTopic(TopicEvent event) {
        TextChannel channel = getDiscordChannel(event);
        if (channel == null) return; //only possible if IRC-OP sajoins bot to another channel
	    channel.sendMessage(String.format("%s has changed topic to: `%s`", event.getUser().getHostmask(), event.getTopic())).queue();
    }

    @Override
    public void onNickChange(NickChangeEvent event) throws Exception {
	    //noinspection ConstantConditions
	    for (Channel channel : event.getUser().getChannels()) {
            TextChannel textChannel = Main.config[configID].channelMapObj.inverse().get(channel);
            if (textChannel == null) {
                continue;
            }
            String oldNick, newNick;
            oldNick = event.getOldNick();
            newNick = event.getNewNick();
            if (event.getUser().isIrcop()) {
                oldNick = "__" + oldNick + "__";
                newNick = "__" + newNick + "__";
            }
            textChannel.sendMessage(
                    String.format("**\\*%s\\*** is now known as _%s_",
                            oldNick,
                            newNick
                    )
            ).queue();
        }
    }

    public TextChannel getDiscordChannel(GenericChannelEvent event) {
        return Main.config[configID].channelMapObj.inverse().get(event.getChannel());
    }

    public void fillChannelMap() {
        if (Main.config[configID].pircBotX == null || Main.config[configID].pircBotX.getUserBot().getChannels().size() == 0 || !ready) {
            return;
        }
	    if (Main.config[configID].jda == null) {
		    LOGGER.warn("JDA is null");
		    return;
	    }
        BiMap<String, String> ircDiscordChanMap = ((BiMap<String, String>) Main.config[configID].channelMapping).inverse();
        for (Channel channel : Main.config[configID].pircBotX.getUserBot().getChannels()) {
            for (Guild guild : Main.config[configID].jda.getGuilds()) {
                for (TextChannel textChannel : guild.getTextChannels()) {
                    if (ircDiscordChanMap.get(channel.getName()).equals("#" + textChannel.getName())) {
                        Main.config[configID].channelMapObj.put(textChannel, channel);
                    }
                }
            }
        }
	    LOGGER.info("Filled channel map");
    }
}
