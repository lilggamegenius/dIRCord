package com.LilG;

import ch.qos.logback.classic.Logger;
import com.LilG.Config.Configuration;
import com.LilG.Config.DiscordChannelConfiguration;
import com.LilG.utils.LilGUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.channel.text.GenericTextChannelEvent;
import net.dv8tion.jda.api.events.channel.text.update.TextChannelUpdateTopicEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.pircbotx.Channel;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.LilG.Bridge.formatString;
import static com.LilG.Main.errorMsg;
import static com.LilG.utils.LilGUtil.startsWithAny;

/**
 * Created by lil-g on 12/12/16.
 */
public class DiscordListener extends ListenerAdapter {
	final static char zeroWidthSpace = '\u200B';
	private final static char colorCode = '\u0003';
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(DiscordListener.class);
	private final byte configID;
	private volatile boolean ready;

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
	public void onReady(@NotNull ReadyEvent event) {
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
		String[] message = LilGUtil.splitMessage(event.getMessage().getContentRaw());
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

	public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
		Main.lastActivity = System.currentTimeMillis();
		try {
			String discordNick = "Error nick";
			String discordUsername = "Error UserName";
			String discordHostmask = "Error Hostmask";
			Member member = event.getMember();
			try {
				if (member != null) {
					discordNick = member.getEffectiveName();
					discordUsername = member.getUser().getName();
					discordHostmask = member.getUser().getId();
				} else {
					discordUsername = discordNick = event.getAuthor().getName();
					discordHostmask = event.getAuthor().getId();
				}
			} catch (Exception e) {
				LOGGER.error("Error receiving message" + errorMsg, e);
			}
			LOGGER.info(String.format("(%s) #%s: <%s!%s@%s> %s", event.getGuild().getName(), event.getChannel().getName(), discordNick, discordUsername, discordHostmask, event.getMessage().getContentRaw()));

			if (Objects.equals(member, event.getGuild().getSelfMember()) ||
					handleCommand(event)) {
				return;
			}
			String message = event.getMessage().getContentDisplay();
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
							formatString(event.getMessage().getContentDisplay())
					);
				} else {
					String user = formatMember(event.getMember());
					String msg = formatString(event.getMessage().getContentDisplay());
					Map<String, String> autoBan = config().AutoBan;
					if (autoBan.keySet().size() != 0) {
						for (String match : autoBan.keySet()) {
							if (LilGUtil.wildCardMatch(msg, match)) {
								String reason = autoBan.get(match);
								event.getAuthor().openPrivateChannel().queue(
										author -> author.sendMessage("You were banned: " + reason)
												.queue(s -> event.getMember().ban(0, reason).queue())
								);
								event.getMessage().delete().reason(reason).queue();
								return;
							}
						}
					}
					List<String> spamFilterList = config().channelOptions.IRC.get(channel.getName()).spamFilterList;
					if (spamFilterList.size() != 0) {
						for (String match : spamFilterList) {
							if (LilGUtil.wildCardMatch(msg, match)) {
								event.getMessage().delete().reason("in spam filter list of " + channel.getName()).queue();
								return;
							}
						}
					}
					//:<hostmask> PRIVMSG #<channel> :<msg>\r\n
					int commandLen = (':' + channel.getBot().getUserBot().getHostmask() + " PRIVMSG " + channel.getName() + " :" + user).getBytes(StandardCharsets.UTF_8).length + 5; // Add 5 bytes for the extra chars added when formatted
					int msgLen = msg.getBytes(StandardCharsets.UTF_8).length;
					if ((commandLen + msgLen) > 512) {
						List<String> msgs = SplitMessageForIRC(msg, commandLen);
						for (String str : msgs) {
							channel.send().message(
									String.format("<%s> %s",
											user,
											str
									)
							);
						}
					} else {
						if (msg.startsWith("\0")) {
							channel.send().message(
									String.format("*%s* %s",
											user,
											msg.substring(1)
									)
							);
						} else {
							channel.send().message(
									String.format("<%s> %s",
											user,
											msg
									)
							);
						}
					}
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
	}

	List<String> SplitMessageForIRC(@NotNull String msg, int commandLen) {
		List<String> ret = new ArrayList<>();
		byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
		int curOffset = 0;
		int index = 512 - commandLen;
		while ((curOffset + index) < msgBytes.length) {
			while ((msgBytes[curOffset + index] & 0xC0) == 0x80)
				index--; // If we're in the middle of a byte sequence, go back until we're not
			if ((msgBytes[curOffset + index] & 0xC0) == 0xC0)
				index--; // Now go back past the start of the byte sequence. This should always be true if we were in the while loop before this
			byte[] msgSlice = new byte[index + 1];
			System.arraycopy(msgBytes, curOffset, msgSlice, 0, index + 1);
			ret.add(new String(msgSlice, StandardCharsets.UTF_8));
			curOffset += index + 1;
			index = 512 - commandLen;
		}

		int length = msgBytes.length - curOffset;
		byte[] msgSlice = new byte[length];
		System.arraycopy(msgBytes, curOffset, msgSlice, 0, length);
		ret.add(new String(msgSlice, StandardCharsets.UTF_8));
		return ret;
	}

	@Override
	public void onTextChannelUpdateTopic(@NotNull TextChannelUpdateTopicEvent event) {
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

	@Override
	public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
		Member user = event.getMember();
		Member self = event.getGuild().getSelfMember();
		boolean same = false;
		if (user.equals(self)) {
			return;
		}
		if (user.getEffectiveName().equalsIgnoreCase(self.getEffectiveName())) {
			if (self.hasPermission(Permission.NICKNAME_MANAGE) && self.canInteract(user)) {
				event.getGuild().modifyNickname(user, event.getOldNickname()).reason("Same nick as bridge bot").queue();
			} else {
				same = true;
				Objects.requireNonNull(event.getGuild().getDefaultChannel()).sendMessage(String.format(
						"User %s!%s@%s has the same name as the bridge bot",
						user.getEffectiveName(),
						user.getUser().getName(),
						user.getUser().getId()))
						.queue();
			}
		}
		Channel channel = null;
		TextChannel textChannel1 = null;
		for (TextChannel textChannel : event.getGuild().getTextChannels()) {
			channel = config().channelMapObj.get(textChannel);
			if (channel == null) {
				continue;
			}
			textChannel1 = textChannel;

			String prevNick = event.getOldNickname();
			String newNick = event.getNewNickname();
			String username = event.getMember().getUser().getName();
			if (prevNick == null) {
				prevNick = username;
			} else if (newNick == null) {
				newNick = username;
			}
			channel.send().message(String.format("\u001D*%s\u001D* Has changed nick to %s%s",
					formatMember(user, prevNick),
					formatMember(user, newNick),
					same ? " And now shares the name with the bridge bot" : ""
					)
			);
		}
		final Channel channel1 = channel;
		final TextChannel textChannel2 = textChannel1;
		String hostmask = user.getEffectiveName() + "!" + user.getUser().getName() + "@" + user.getUser().getId();
		for(String masksToBan : config().BanOnSight){
			if(LilGUtil.matchHostMask(hostmask, masksToBan)){
				user.ban(0, "Ban On Sight: " + masksToBan).queue(s -> {
							assert textChannel2 != null;
							textChannel2.sendMessage(String.format("User %s was banned due to being on Ban-On-Sight list", hostmask)).queue(d -> {
								if (channel1 != null) {
									channel1.send().message(String.format("\u001D*%s\u001D* was banned due to being on Ban-On-Sight list",
											formatMember(user, user.getEffectiveName())
											)
									);
								}
							});
						}
				);
				return;
			}
		}
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Member user = event.getMember();
		List<Channel> channels = new ArrayList<>();
		List<TextChannel> textChannels = new ArrayList<>();
		for (TextChannel textChannel : event.getGuild().getTextChannels()) {
			Channel tempChannel = config().channelMapObj.get(textChannel);
			if (tempChannel != null) {
				textChannels.add(textChannel);
				channels.add(tempChannel);
			}
		}
		if (channels.isEmpty()) {
			return;
		}
		final Channel channel1 = channels.get(0);
		final TextChannel textChannel = textChannels.get(0);
		String hostmask = user.getEffectiveName() + "!" + user.getUser().getName() + "@" + user.getUser().getId();
		String formattedName = formatMember(user, user.getEffectiveName());
		for (String masksToBan : config().BanOnSight) {
			if (LilGUtil.matchHostMask(hostmask, masksToBan)) {
				user.ban(0, "Ban On Sight: " + masksToBan).queue(s ->
						textChannel.sendMessage(String.format("Joining user %s was banned due to being on Ban-On-Sight list", hostmask)).queue(d ->
								channel1.send().message(
										String.format("\u001D*%s\u001D* was banned due to being on Ban-On-Sight list",
												formattedName
										)
								)
						)
				);
				return;
			}
		}
		for (int i = 0; i < textChannels.size(); i++) {
			channels.get(i).send().message(String.format("%s [%s] has Joined #%s",
					formattedName,
					user.getUser().getId(),
					textChannels.get(i).getName()
			));
		}
	}

	@Override
	public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
		Member user = event.getMember();
		if (user == null) return;
		List<Channel> channels = new ArrayList<>();
		List<TextChannel> textChannels = new ArrayList<>();
		for (TextChannel textChannel : event.getGuild().getTextChannels()) {
			Channel tempChannel = config().channelMapObj.get(textChannel);
			if (tempChannel != null) {
				textChannels.add(textChannel);
				channels.add(tempChannel);
			}
		}
		if (channels.isEmpty()) {
			return;
		}
		String formattedName = formatMember(user, user.getEffectiveName());
		for (int i = 0; i < textChannels.size(); i++) {
			channels.get(i).send().message(String.format("%s [%s] has Quit %s",
					formattedName,
					user.getUser().getId(),
					event.getGuild().getName()
			));
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

		final byte ircColor;
		final Color color;

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
