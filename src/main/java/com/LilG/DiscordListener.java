package com.LilG;

import ch.qos.logback.classic.Logger;
import com.LilG.Config.Configuration;
import com.LilG.Config.DiscordChannelConfiguration;
import com.LilG.utils.LilGUtil;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.channel.text.GenericTextChannelEvent;
import net.dv8tion.jda.core.events.channel.text.update.TextChannelUpdateTopicEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.pircbotx.Channel;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.Map;

import static com.LilG.Bridge.formatString;
import static com.LilG.Main.errorMsg;
import static com.LilG.utils.LilGUtil.startsWithAny;

/**
 * Created by lil-g on 12/12/16.
 */
public class DiscordListener extends ListenerAdapter {
	private final static char colorCode = '\u0003';
	private final static char zeroWidthSpace = '\u200B';
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(DiscordListener.class);
	private final byte configID;
	public volatile boolean ready;

	DiscordListener(byte configID) {
		this.configID = configID;
	}

	private String formatMember(Member user) {
		return formatMember(user, FormatAs.effectiveName);
	}

	private String formatMember(Member user, FormatAs format) {
		switch (format) {
			case effectiveName:
				return formatMember(user, (String) null);
			case NickName:
				return formatMember(user, user.getNickname());
			case Username:
				return formatMember(user, user.getUser().getName());
			case ID:
				return formatMember(user, user.getUser().getId()); // ????
		}
		return "";
	}

	private String formatMember(Member user, String override) {
		if (override == null) {
			override = user.getEffectiveName();
		}
		String color = "";
		if (config().ircNickColor) {
			int ircColorCode = ColorMap.valueOf(user.getColor());
			if (ircColorCode < 0) {
				ircColorCode = LilGUtil.hash(user.getEffectiveName(), 12) + 2;
			}
			color = colorCode + String.format("%02d", ircColorCode);
		}
		String nameWithSpace = String.valueOf(override.charAt(0)) + zeroWidthSpace + override.substring(1);
		return String.format("%s%s%c", color, nameWithSpace, colorCode);
	}

	private Channel getIRCChannel(GuildMessageReceivedEvent event) {
		return config().channelMapObj.get(event.getChannel());
	}

	@Override
	public void onReady(ReadyEvent event) {
		ready = true;
		config().ircListener.fillChannelMap();
		Map<String, DiscordChannelConfiguration> configs = config().channelOptions.Discord;
		for (TextChannel channel : config().channelMapObj.keySet()) {
			if (!configs.containsKey("#" + channel.getName())) {
				configs.put("#" + channel.getName(), new DiscordChannelConfiguration());
			}
		}
	}

	private Channel getIRCChannel(GenericTextChannelEvent event) {
		return config().channelMapObj.get(event.getChannel());
	}

	private boolean handleCommand(GuildMessageReceivedEvent event) {
		String[] message = LilGUtil.splitMessage(event.getMessage().getRawContent());
		if (message.length == 0 || message[0].isEmpty()) {
			return false;
		}
		if (message[0].startsWith(event.getGuild().getSelfMember().getEffectiveName()) ||
				message[0].startsWith(event.getGuild().getSelfMember().getAsMention())
				) {
			if (getIRCChannel(event) != null) {
				Bridge.handleCommand(message, event, configID, false);
				return true;
			}
		}
		return false;
	}

	public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
		try {
			String discordNick = "Error nick";
			String discordUsername = "Error UserName";
			String discordHostmask = "Error Hostmask";
			try {
				discordNick = event.getMember().getEffectiveName();
				discordUsername = event.getMember().getUser().getName();
				discordHostmask = event.getMember().getUser().getId();
			} catch (NullPointerException e) {
				discordNick = event.getAuthor().getName();
				discordUsername = discordNick;
				discordHostmask = event.getAuthor().getId();
			} catch (Exception e) {
				LOGGER.error("Error receiving message" + errorMsg, e);
			}
			LOGGER.info(String.format("#%s: <%s!%s@%s> %s", event.getChannel().getName(), discordNick, discordUsername, discordHostmask, event.getMessage().getRawContent()));

			if (event.getAuthor().isFake() ||
					event.getMember().equals(event.getGuild().getSelfMember()) ||
					handleCommand(event)) {
				return;
			}
			String message = event.getMessage().getContent();
			Channel channel = getIRCChannel(event);
			if (channel == null) {
				return;
			}
			if (message.length() != 0) {
				DiscordChannelConfiguration configuration = channelConfig(event);
				if (configuration != null && startsWithAny(message, configuration.getCommmandCharacters())) {
					channel.send().message(
							String.format("\u001DCommand Sent by\u001D \u0002%s\u0002",
									formatMember(event.getMember())
							)
					);
					channel.send().message(
							formatString(event.getMessage().getContent())
					);
				} else {
					channel.send().message(
							String.format("<%s> %s",
									formatMember(event.getMember()),
									formatString(event.getMessage().getContent())
							)
					);
				}
			}
			List<Message.Attachment> attachments;
			if ((attachments = event.getMessage().getAttachments()).size() != 0) {
				StringBuilder embedMessage = new StringBuilder(String.format("Attachments from <%s>:",
						formatMember(event.getMember()))
				);
				for (Message.Attachment attachment : attachments) {
					embedMessage.append(" ").append(attachment.getUrl());
				}
				channel.send().message(embedMessage.toString());
			}
			Main.LastUserToSpeak.put(event.getChannel(), event.getMember());
		} catch (Exception e) {
			LOGGER.error("Error in DiscordListener" + errorMsg, e);
		}
		Main.lastActivity = System.currentTimeMillis();
	}

	@Override
	public void onTextChannelUpdateTopic(TextChannelUpdateTopicEvent event) {
		Channel channel = getIRCChannel(event);
		if (channel == null) {
			return;
		}
		// Afaik Discord doesn't have info for who changed a topic
		channel.send().message(String.format("%s has changed topic to: %s", "A user", event.getChannel().getTopic()));
	}

    /*public void onUserOnlineStatusUpdate(UserOnlineStatusUpdateEvent event) {
        if (event.getUser().equals(jda.getSelfUser())) {
            return;
        }
        Member member = event.getGuild().getMember(event.getUser());
        for (String discordChannels : config().channelMapping.keySet()) {
            for(TextChannel textChannel : event.getGuild().getTextChannels()) {
                if (discordChannels.substring(1, discordChannels.length()).equals(textChannel.getName())) {
                    String color = "";
                    if (config().ircNickColor) {
                        int ircColorCode = ColorMap.valueOf(member.getColor());
                        if (ircColorCode < 0) {
                            ircColorCode = LilGUtil.hash(member.getEffectiveName(), 12) + 2;
                        }
                        color = colorCode + String.format("%02d", ircColorCode);
                    }

                    Main.pircBotX.send()
                            .message(config()
                                            .channelMapping
                                            .get(discordChannels),
                                    String.format("\\*%s%s%c\\* %s",
                                            color,
                                            prevNick,
                                            colorCode,
                                            "Has changed nick to " + newNick
                                    )
                            )
                    ;
                }
            }
        }
    }*/

	public void onGuildMemberNickChange(GuildMemberNickChangeEvent event) {
		Member user = event.getMember();
		if (user.equals(event.getGuild().getSelfMember())) {
			return;
		}
		for (TextChannel textChannel : event.getGuild().getTextChannels()) {
			Channel channel = config().channelMapObj.get(textChannel);
			if (channel == null) {
				continue;
			}

			String prevNick = event.getPrevNick();
			String newNick = event.getNewNick();
			String username = event.getMember().getUser().getName();
			if (prevNick == null) {
				prevNick = username;
			} else if (newNick == null) {
				newNick = username;
			}
			channel.send().message(String.format("\u001D*%s\u001D* %s%s",
					formatMember(user, prevNick),
					"Has changed nick to ",
					formatMember(user, newNick)
					)
			)
			;
		}
	}

	private Configuration config() {
		return Main.config[configID];
	}

	private DiscordChannelConfiguration channelConfig(GuildMessageReceivedEvent event) {
		return channelConfig(event.getChannel().getName());
	}

	private DiscordChannelConfiguration channelConfig(String channel) {
		return config().channelOptions.Discord.get("#" + channel);
	}

	enum FormatAs {
		effectiveName, NickName, Username, ID
	}

	enum ColorMap {
		turquoise(10, new Color(26, 188, 156)),
		darkTurquoise(10, new Color(17, 128, 106)),
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
