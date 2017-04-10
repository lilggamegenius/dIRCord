package com.LilG;

import ch.qos.logback.classic.Logger;
import net.dv8tion.jda.core.OnlineStatus;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.hooks.events.MessageEvent;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Created by ggonz on 4/4/2017.
 */
public class Bridge {
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(Bridge.class);

	public static void handleCommand(String command, String[] args, Object eventObj, byte configID, boolean IRC) { // if IRC is true, then command called from IRC
		switch (command.toLowerCase()) {
			case "whois": {
				if (args.length > 0) {
					String name = argJoiner(args, 0);
					if (IRC) {
						MessageEvent event = (MessageEvent) eventObj;
						List<Member> members = Main.config[configID].ircListener.getDiscordChannel(event).getGuild().getMembersByEffectiveName(name, true);
						if (!members.isEmpty()) {
							String nickname, username, ID, status, avatar, game, joinDate, registerDate, roles, permissions;
							boolean streaming;
							Member member = members.get(0);
							nickname = member.getEffectiveName();
							username = member.getUser().getName();
							ID = member.getUser().getId();
							status = member.getOnlineStatus().getKey();
							if (status.equals("dnd")) {
								status = "Do not disturb";
							}
							avatar = member.getUser().getAvatarUrl();
							Game gameObj = member.getGame();
							if (gameObj != null) {
								streaming = gameObj.getType() != Game.GameType.DEFAULT;
								game = streaming ? member.getGame().getName() : member.getGame().getUrl();
							} else {
								streaming = false;
								game = "nothing";
							}
							joinDate = member.getJoinDate().toLocalDateTime().toString();
							registerDate = member.getUser().getCreationTime().toLocalDateTime().toString();
							StringBuilder rolesBuilder = new StringBuilder();
							boolean first = true;
							for (Role role : member.getRoles()) {
								if (!first) {
									rolesBuilder.append(", ");
								} else {
									first = false;
								}
								rolesBuilder.append(role.getName());
							}
							roles = rolesBuilder.toString();
							StringBuilder permissionsBuilder = new StringBuilder();
							first = true;
							for (Permission permission : member.getPermissions()) {
								if (!first) {
									permissionsBuilder.append(", ");
								} else {
									first = false;
								}
								permissionsBuilder.append(permission.getName());
							}
							permissions = permissionsBuilder.toString();
							event.respond(String.format("%s is %s!%s@%s   Status: %s   Currently %s %s",
									name,
									nickname,
									username,
									ID,
									status,
									streaming ? "streaming" : "playing",
									game));
							event.respond(String.format("Registered: %s   Joined: %s   Avatar: %s ",
									registerDate,
									joinDate,
									avatar));
							event.getUser().send().notice(String.format(
									"Roles: [%s] Permissions: [%s]",
									roles,
									permissions
							));
						} else {
							event.respond(String.format("No one with the name \"%s\" was found", name));
						}
					} else {
						sendMessage(eventObj, "Currently unimplemented", IRC); // todo
					}
				} else {
					sendMessage(eventObj, "Missing argument", IRC);
				}
			}
			break;
			case "ison": {
				if (args.length > 0) {
					String name = argJoiner(args, 0);
					if (IRC) {
						MessageEvent event = (MessageEvent) eventObj;
						List<Member> members = Main.config[configID].ircListener.getDiscordChannel(event).getGuild().getMembersByEffectiveName(name, true);
						if (!members.isEmpty()) {
							String nickname;
							Member member = members.get(0);
							nickname = member.getEffectiveName();
							boolean online = member.getOnlineStatus() != OnlineStatus.OFFLINE;
							event.respond(String.format("%s is %s",
									nickname,
									online ? "online" : "offline"));
						} else {
							event.respond(String.format("No one with the name \"%s\" was found", name));
						}
					} else {
						sendMessage(eventObj, "Currently unimplemented", IRC); // todo
					}
				}
			}
			break;
		}
	}

	public static void handleCommand(String[] message, Object event, byte configID, boolean IRC) {
		String command, args[] = {};
		command = message[1];
		if (message.length > 2) {
			args = new String[message.length - 2];
			try {
				System.arraycopy(message, 2, args, 0, message.length - 2);
			} catch (Exception e) {
				LOGGER.error("array copy error", e);
			}
		}
		handleCommand(command, args, event, configID, IRC);
	}

	private static void sendMessage(Object eventObj, String message, boolean IRC) {
		sendMessage(eventObj, message, IRC, true);
	}

	private static void sendMessage(Object eventObj, String message, boolean IRC, boolean highlight) {
		if (IRC) {
			MessageEvent event = (MessageEvent) eventObj;
			if (highlight) {
				event.respond(message);
			} else {
				event.respondWith(message);
			}
		} else {
			GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
			if (highlight) {
				event.getChannel().sendMessage("%s: %s", event.getMember().getAsMention(), message).complete();
			} else {
				event.getChannel().sendMessage(message).complete();
			}
		}
	}

	private static String argJoiner(String[] args, int argToStartFrom) throws ArrayIndexOutOfBoundsException {
		if (args.length - 1 == argToStartFrom) {
			return args[argToStartFrom];
		}
		String strToReturn = "";
		for (int length = args.length; length > argToStartFrom; argToStartFrom++) {
			strToReturn += args[argToStartFrom] + " ";
		}
		LOGGER.debug("Argument joined to: " + strToReturn);
		if (strToReturn.isEmpty()) {
			return strToReturn;
		} else {
			return strToReturn.substring(0, strToReturn.length() - 1);
		}
	}


	public static String formatString(TextChannel channel, String strToFormat) {
		final char underline = '\u001F';
		final char italics = '\u001D';
		final char bold = '\u0002';
		final char color = '\u0003';
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
			strToFormat = strToFormat.replace("@everyone", "`@everyone`");
			String strLower = strToFormat.toLowerCase();
			for (Member member : channel.getMembers()) {
				String memberName = member.getEffectiveName().toLowerCase();
				while (strLower.contains("@" + memberName)) {
					int index = strLower.indexOf(memberName);
					strToFormat = strToFormat.substring(0, index - 1) +
							member.getAsMention() +
							strToFormat.substring(index + memberName.length());
					strLower = strToFormat.toLowerCase();
				}
			}
		}
		if (strToFormat.contains(color + "")) {
			strToFormat = strToFormat.replaceAll(color + "[0-9]{2}", "");
		}

		return strToFormat;
	}

	public static String formatString(String message) {
		final char underline = '\u001F';
		final char italics = '\u001D';
		final char bold = '\u0002';
		// find links
		String[] parts = message.split("\\s+");
		for (int i = 0, partsLength = parts.length; i < partsLength; i++) {
			String item = parts[i];
			try {
				new URL(item);
				// If possible then replace with anchor...
				message = message.replace(parts[i], "%" + (i + 1) + "$s");
			} catch (MalformedURLException ignored) {
			}
		}

		int underlineCount = StringUtils.countMatches(message, "__");
		if (underlineCount > 1) {
			if (underlineCount % 2 != 0) {
				for (int count = 0; count < underlineCount; count++) {
					message = message.replace("__", underline + "");
				}
			} else {
				message = message.replace("__", underline + "");
			}
		}
		int boldCount = StringUtils.countMatches(message, "**");
		if (boldCount > 1) {
			if (boldCount % 2 != 0) {
				for (int count = 0; count < boldCount; count++) {
					message = message.replace("**", bold + "");
				}
			} else {
				message = message.replace("**", bold + "");
			}
		}
		int italicsCount = StringUtils.countMatches(message, "_");
		if (italicsCount > 1) {
			if (italicsCount % 2 != 0) {
				for (int count = 0; count < italicsCount; count++) {
					message = message.replace("_", italics + "");
				}
			} else {
				message = message.replace("_", italics + "");
			}
		}
		italicsCount = StringUtils.countMatches(message, "*");
		if (italicsCount > 1) {
			if (italicsCount % 2 != 0) {
				for (int count = 0; count < italicsCount; count++) {
					message = message.replace("*", italics + "");
				}
			} else {
				message = message.replace("*", italics + "");
			}
		}
		message = message
				.replace('\u0007', '␇')
				.replace('\n', '␤')
				.replace('\r', '␍');
		return String.format(message, (Object[]) parts);
	}
}
