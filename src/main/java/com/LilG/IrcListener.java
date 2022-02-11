package com.LilG;

import ch.qos.logback.classic.Logger;
import com.LilG.Config.Configuration;
import com.LilG.Config.IRCChannelConfiguration;
import com.LilG.utils.LilGUtil;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableSortedSet;
import net.dv8tion.jda.api.entities.TextChannel;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.WaitForQueue;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.GenericChannelEvent;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.LilG.Bridge.formatString;
import static com.LilG.Main.errorMsg;
import static com.LilG.utils.LilGUtil.matchHostMask;
import static com.LilG.utils.LilGUtil.startsWithAny;

/**
 * Created by lil-g on 12/12/16.
 */
public class IrcListener extends ListenerAdapter {
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(IrcListener.class);
	private final byte configID;
	private volatile boolean ready;

	IrcListener(byte configID) {
		this.configID = configID;
	}

	private static String getUserSymbol(MessageEvent event) {
		return getUserSymbol(event.getChannel(), event.getUser());
	}

	public static String getUserSymbol(MessageEvent event, User user) {
		return getUserSymbol(event.getChannel(), user);
	}

	public static String getUserSymbol(MessageEvent event, Channel channel) {
		return getUserSymbol(channel, event.getUser());
	}

	private static String getUserSymbol(Channel channel, User user) {
		if (channel == null) throw new NullPointerException("Channel");
		if (user == null) throw new NullPointerException("User");
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

	private boolean handleCommand(MessageEvent event) {
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

	public void onConnect(ConnectEvent event) {
		config().pircBotX = event.getBot();
		for (String string : config().autoSendCommands) {
			event.getBot().sendRaw().rawLine(string);
		}
		ready = true;
		fillChannelMap();
	}

	@Override
	public void onMessage(MessageEvent event) {
		Skip:
		try {
			config().pircBotX = event.getBot();
			String message = event.getMessage();
			TextChannel channel = getDiscordChannel(event);
			User user = event.getUser();
			if (user == null) break Skip;
			for (int tries = 0; channel == null; tries++) {
				if (tries > 10) {
					event.respond("Failed sending message to discord" + errorMsg);
					return;
				}
				Thread.sleep(1000);
				channel = getDiscordChannel(event, true);
			}
			IRCChannelConfiguration configuration = channelConfig(event);
			if (configuration != null && startsWithAny(message, configuration.getCommmandCharacters())) {
				TextChannel finalChannel = channel;
				channel.sendMessage(
						String.format("_Command Sent by_ `%s%s`",
								getUserSymbol(event),
								formatName(user, true)
						)
				).queue(
						message1 -> finalChannel.sendMessage(formatString(finalChannel, message)).queue()
				);
			} else {
				if (!handleCommand(event)) {
					if (configuration != null) {
						for (String hostmask : configuration.ignoreUserMessageIf.keySet()) {
							if (matchHostMask(user.getHostmask(), hostmask)) {
								if (message.contains(configuration.ignoreUserMessageIf.get(hostmask))) {
									break Skip;
								}
							}
						}
					}

					channel.sendMessage(
							String.format("**<%s%s>** %s",
									getUserSymbol(event),
									formatName(user),
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
		User user = event.getUser();
		if (user == null) return;
		if (message.contains("\u0001AVATAR")) {
			user.send().notice("\u0001AVATAR " + config().jda.getSelfUser().getAvatarUrl() + "\u0001");
		}
		//fillChannelMap();
	}

	@Override
	public void onJoin(JoinEvent event) {
		IRCChannelConfiguration configuration = channelConfig(event.getChannel().getName());
		getSpamList(event.getChannel());
		if (configuration == null) {
			configuration = new IRCChannelConfiguration();
			config().channelOptions.IRC.put(event.getChannel().getName(), configuration);
		}
		if (configuration.joins)
			getDiscordChannel(event).sendMessage(
					String.format("**\\*%s\\*** _%s_",
							formatName(event.getUser()),
							"Joined"
					)
			).queue();
		fillChannelMap();
	}

	@Override
	public void onPart(PartEvent event) {
		IRCChannelConfiguration configuration = channelConfig(event.getChannel().getName());
		if (configuration.parts)
			getDiscordChannel(event).sendMessage(
					String.format("**\\*%s\\*** _Left_ Reason: %s",
							formatName(event.getUser()),
							event.getReason()
					)
			).queue();
		fillChannelMap();
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
		fillChannelMap();
	}

	@Override
	public void onAction(ActionEvent event) {
		getDiscordChannel(event).sendMessage(
				String.format("**\\*%s\\*** _%s_",
						formatName(event.getUser()),
						event.getMessage()
				)
		).queue();
		//fillChannelMap();
	}

	@Override
	public void onMode(ModeEvent event) {
		if (event.getMode().contains("g")) {
			getSpamList(event.getChannel());
		}
		IRCChannelConfiguration configuration = channelConfig(event.getChannel().getName());
		if (configuration.modes) {
			getDiscordChannel(event).sendMessage(
					String.format("**\\*%s\\*** _%s_",
							formatName(event.getUser()),
							"Set mode " + event.getMode()
					)
			).queue();
		}
		//fillChannelMap();
	}

	@Override
	public void onQuit(QuitEvent event) {
		User user = event.getUser();
		for (Channel channel : user.getChannels()) {
			IRCChannelConfiguration configuration = channelConfig(channel.getName());
			if (!configuration.quits) continue;
			TextChannel textChannel = config().channelMapObj.inverse().get(channel);
			textChannel.sendMessage(
					String.format("**\\*%s\\*** _%s_",
							formatName(user),
							"has quit: " + event.getReason()
									.replace("http://www.mibbit.com", "<http://www.mibbit.com>")
									.replace("http://www.androirc.com/", "<http://www.androirc.com/>")
					)
			).queue();
		}
		fillChannelMap();
	}

	@Override
	public void onTopic(TopicEvent event) {
		TextChannel channel = getDiscordChannel(event);
		if (channel == null) return; //only possible if IRC-OP sajoins bot to another channel
		Date time = new Date(event.getDate() * (event.isChanged() ? 1 : 1000));
		SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy h:mm:ss a Z");
		String formattedTime = format.format(time);
		if (event.isChanged()) {
			channel.sendMessage(String.format("%s has changed topic to: `%s` at %s", event.getUser().getHostmask(), event.getTopic(), formattedTime)).queue();
		} else {
			channel.sendMessage(String.format("Current Topic: `%s` set by %s at %s", event.getTopic(), event.getUser().getHostmask(), formattedTime)).queue();
		}
		//fillChannelMap();
	}

	@Override
	public void onNickChange(NickChangeEvent event) {
		User user = event.getUser();
		//noinspection ConstantConditions
		for (Channel channel : user.getChannels()) {
			IRCChannelConfiguration configuration = channelConfig(channel.getName());
			if (!configuration.nicks) continue;
			TextChannel textChannel = config().channelMapObj.inverse().get(channel);
			if (textChannel == null) {
				continue;
			}
			String oldNick, newNick;
			oldNick = event.getOldNick();
			newNick = event.getNewNick();
			if (user.isIrcop()) {
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
		//fillChannelMap();
	}

	private TextChannel getDiscordChannel(GenericChannelEvent event) {
		return getDiscordChannel(event, false);
	}

	private TextChannel getDiscordChannel(GenericChannelEvent event, boolean forceFill) {
		if (forceFill || config().channelMapObj.isEmpty()) fillChannelMap();
		return config().channelMapObj.inverse().get(event.getChannel());
	}

	void fillChannelMap() {
		if (config().pircBotX == null ||
				config().pircBotX.getUserBot().getChannels().size() == 0 ||
				!ready) {
			return;
		}
		if (config().jda == null) {
			LOGGER.warn("JDA is null");
			return;
		}
		BiMap<String, String> ircDiscordChanMap = ((BiMap<String, String>) config().channelMapping).inverse();
		for (Channel channel : config().pircBotX.getUserBot().getChannels()) {
			config().channelMapObj.put(
					config().jda.getTextChannelById(
							ircDiscordChanMap.get(
									channel.getName()
							)
					), channel
			);
		}
		LOGGER.info("Filled channel map");
	}

	private void getSpamList(final Channel channel) {
		new Thread(() -> {
			WaitForQueue queue = new WaitForQueue(channel.getBot());
			//Infinite loop since we might recieve messages that aren't WaitTest's.
			channel.send().setMode("+g");
			try {
				List<String> spamFilterList = config().channelOptions.IRC.get(channel.getName()).spamFilterList;
				spamFilterList.clear();
				while (true) {
					//Use the waitFor() method to wait for a ServerResponseEvent.
					//This will block (wait) until a ServerResponseEvent comes in, ignoring
					//everything else
					ServerResponseEvent currentEvent = queue.waitFor(ServerResponseEvent.class);
					//Check if this message is the "ping" command
					if (currentEvent.getCode() == 941) {
						spamFilterList.add(currentEvent.getParsedResponse().get(2));
					} else if (currentEvent.getCode() == 940) {
						LOGGER.trace("End of channel spam Filter list");
						queue.close();
						//Very important that we end the infinite loop or else the test
						//will continue forever!
						return;
					}
				}
			} catch (InterruptedException e) {
				LOGGER.warn("Getting spam filter list interrupted", e);
			}
		}).start();
	}

	private Configuration config() {
		return Main.config[configID];
	}

	private IRCChannelConfiguration channelConfig(MessageEvent event) {
		return channelConfig(event.getChannel().getName());
	}

	private IRCChannelConfiguration channelConfig(String channel) {
		return config().channelOptions.IRC.get(channel);
	}
}
