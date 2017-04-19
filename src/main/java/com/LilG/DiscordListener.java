package com.LilG;
import ch.qos.logback.classic.Logger;
import com.LilG.utils.LilGUtil;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.pircbotx.Channel;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;

import static com.LilG.Bridge.formatString;
import static com.LilG.utils.LilGUtil.startsWithAny;

/**
 * Created by lil-g on 12/12/16.
 */
public class DiscordListener extends ListenerAdapter {
	private final static char colorCode = '\u0003';
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(DiscordListener.class);
	private final byte configID;
	public volatile boolean ready;

	DiscordListener(byte configID) {
		this.configID = configID;
	}

	public boolean handleCommand(GuildMessageReceivedEvent event) {
		String[] message = LilGUtil.splitMessage(event.getMessage().getRawContent());
		if (message == null || message[0] == null) {
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

	@Override
	public void onReady(ReadyEvent event) {
		ready = true;
		Main.config[configID].ircListener.fillChannelMap();
	}

	public Channel getIRCChannel(GuildMessageReceivedEvent event) {
		return Main.config[configID].channelMapObj.get(event.getChannel());
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
				LOGGER.error("Error receiving message", e);
			}
			LOGGER.info(String.format("#%s: <%s!%s@%s> %s", event.getChannel().getName(), discordNick, discordUsername, discordHostmask, event.getMessage().getRawContent()));

			if (event.getMember().equals(event.getGuild().getSelfMember()) ||
					handleCommand(event)) {
				return;
			}
			String color = "";
			if (Main.config[configID].ircNickColor) {
				int ircColorCode = ColorMap.valueOf(event.getMember().getColor());
				if (ircColorCode < 0) {
					ircColorCode = LilGUtil.hash(event.getMember().getEffectiveName(), 12) + 2;
				}
				color = colorCode + String.format("%02d", ircColorCode);
			}
			String message = event.getMessage().getContent();
			Channel channel = getIRCChannel(event);
			if (channel == null) {
				return;
			}
			if (message.length() != 0) {
				if (startsWithAny(message, Main.config[configID].commandCharacters.toArray(new String[]{}))) {
					channel.send().message(
							String.format("\u001DCommand Sent by\u001D \u0002%s%s%c\u0002",
									color,
									event.getMember().getEffectiveName(),
									colorCode
							)
					);
					channel.send().message(
							formatString(event.getMessage().getContent())
					);
				} else {
					channel.send().message(
							String.format("<%s%s%c> %s",
									color,
									event.getMember().getEffectiveName(),
									colorCode,
									formatString(event.getMessage().getContent())
							)
					);
				}
			}
			List<Message.Attachment> attachments;
			if ((attachments = event.getMessage().getAttachments()).size() != 0) {
				StringBuilder embedMessage = new StringBuilder(String.format("Attachments from <%s%s%c>:",
						color,
						event.getMember().getEffectiveName(),
						colorCode)
				);
				for (Message.Attachment attachment : attachments) {
					embedMessage.append(" ").append(attachment.getUrl());
				}
				channel.send().message(embedMessage.toString());
			}
		} catch (Exception e) {
			LOGGER.error("Error in DiscordListener\n", e);
		}
		Main.lastActivity = System.currentTimeMillis();
	}

    /*public void onUserOnlineStatusUpdate(UserOnlineStatusUpdateEvent event) {
        if (event.getUser().equals(jda.getSelfUser())) {
            return;
        }
        Member member = event.getGuild().getMember(event.getUser());
        for (String discordChannels : Main.config[configID].channelMapping.keySet()) {
            for(TextChannel textChannel : event.getGuild().getTextChannels()) {
                if (discordChannels.substring(1, discordChannels.length()).equals(textChannel.getName())) {
                    String color = "";
                    if (Main.config[configID].ircNickColor) {
                        int ircColorCode = ColorMap.valueOf(member.getColor());
                        if (ircColorCode < 0) {
                            ircColorCode = LilGUtil.hash(member.getEffectiveName(), 12) + 2;
                        }
                        color = colorCode + String.format("%02d", ircColorCode);
                    }

                    Main.pircBotX.send()
                            .message(Main.config[configID]
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

		if (event.getMember().equals(event.getGuild().getSelfMember())) {
			return;
		}
		for (TextChannel textChannel : event.getGuild().getTextChannels()) {
			Channel channel = Main.config[configID].channelMapObj.get(textChannel);
			if (channel == null) {
				continue;
			}
			String color = "";
			String secondColor = "";

			String prevNick = event.getPrevNick();
			String newNick = event.getNewNick();
			String username = event.getMember().getUser().getName();
			if (prevNick == null) {
				prevNick = username;
			} else if (newNick == null) {
				newNick = username;
			}
			if (Main.config[configID].ircNickColor) {
				boolean usingDiscordColor = true;
				int ircColorCode = ColorMap.valueOf(event.getMember().getColor());
				if (ircColorCode < 0) {
					ircColorCode = LilGUtil.hash(prevNick, 12) + 2;
					usingDiscordColor = false;
				}
				color = colorCode + String.format("%02d", ircColorCode);
				if (!usingDiscordColor) {
					secondColor = colorCode + String.format("%02d", LilGUtil.hash(newNick, 12) + 2);
				}
			}

			channel.send().message(String.format("\u001D*%s%s%c\u001D* %s%s%s%c",
					color,
					prevNick,
					colorCode,
					"Has changed nick to ",
					secondColor,
					newNick,
					colorCode
					)
			)
			;
		}
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
