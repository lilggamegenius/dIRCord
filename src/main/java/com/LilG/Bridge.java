package com.LilG;

import ch.qos.logback.classic.Logger;
import com.LilG.utils.LilGUtil;
import com.google.common.collect.ImmutableSortedSet;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.Gist;
import org.eclipse.egit.github.core.GistFile;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.GistService;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.UserLevel;
import org.pircbotx.hooks.events.MessageEvent;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ggonz on 4/4/2017.
 */
class Bridge {
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(Bridge.class);
	private static final String escapePrefix = "@!";
	private static final Map<String, String> languages = initLangMap();

	private static TextChannel getDiscordChannel(byte configID, MessageEvent event) {
		return Main.config[configID].channelMapObj.inverse().get(event.getChannel());
	}

	private static Channel getIRCChannel(byte configID, GuildMessageReceivedEvent event) {
		return Main.config[configID].channelMapObj.get(event.getChannel());
	}

	private static boolean checkPerm(byte configID, MessageEvent event) {
		return checkPerm(configID, event, UserLevel.HALFOP);
	}

	private static boolean checkPerm(byte configID, GuildMessageReceivedEvent event) {
		return checkPerm(configID, event, Permission.MESSAGE_MANAGE);
	}

	private static boolean checkPerm(byte configID, MessageEvent event, UserLevel userLevelToCheck) {
		if (LilGUtil.matchHostMask(event.getUserHostmask().getHostmask(), Main.config[configID].IRCBotOwnerHostmask)) {
			//return true;
		}
		UserLevel userLevel = getUserLevel(event.getChannel().getUserLevels(event.getUser()));
		return userLevel.ordinal() >= userLevelToCheck.ordinal();
	}

	private static boolean checkPerm(byte configID, GuildMessageReceivedEvent event, Permission permission) {
		if (event.getAuthor().getId().equals(Main.config[configID].DiscordBotOwnerID)) {
			//return true;
		}
		return event.getMember().hasPermission(permission);
	}

	static void handleCommand(String[] message, Object event, byte configID, boolean IRC) {
		String command;
		String[] args = {};
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
				event.getChannel().sendMessage(String.format("%s: %s", event.getMember().getAsMention(), message)).complete();
			} else {
				event.getChannel().sendMessage(message).complete();
			}
		}
	}

	private static String argJoiner(String[] args) throws ArrayIndexOutOfBoundsException {
		return argJoiner(args, 0);
	}

	private static String argJoiner(String[] args, int argToStartFrom) throws ArrayIndexOutOfBoundsException {
		if (args.length - 1 == argToStartFrom) {
			return args[argToStartFrom];
		}
		StringBuilder strToReturn = new StringBuilder();
		for (int length = args.length; length > argToStartFrom; argToStartFrom++) {
			strToReturn.append(args[argToStartFrom]).append(" ");
		}
		LOGGER.debug("Argument joined to: " + strToReturn);
		if (strToReturn.length() == 0) {
			return strToReturn.toString();
		} else {
			return strToReturn.substring(0, strToReturn.length() - 1);
		}
	}

	private static UserLevel getUserLevel(ImmutableSortedSet<UserLevel> levels) {
		int ret = 0;
		if (levels == null || levels.isEmpty()) {
			return null;
		}
		for (UserLevel level : levels) {
			int levelNum = level.ordinal();
			ret = Math.max(ret, levelNum);
		}

		if (ret == 0) {
			return null;
		}
		return UserLevel.values()[ret - 1];
	}

	static String formatString(TextChannel channel, String strToFormat) {
		final char underline = '\u001F';
		final char italics = '\u001D';
		final char bold = '\u0002';
		final char color = '\u0003';
		final char reverse = '\u0016';
		int reverseCount = StringUtils.countMatches(strToFormat, reverse);
		int underlineCount = StringUtils.countMatches(strToFormat, underline);
		int italicsCount = StringUtils.countMatches(strToFormat, italics);
		int boldCount = StringUtils.countMatches(strToFormat, bold);
		if (reverseCount != 0) {
			strToFormat = strToFormat.replace(reverse, '`');
			if (reverseCount % 2 != 0) {
				strToFormat += '`';
			}
		}
		if (underlineCount != 0) {
			strToFormat = strToFormat.replace(underline + "", "__");
			if (underlineCount % 2 != 0) {
				strToFormat += "__";
			}
		}
		if (italicsCount != 0) {
			strToFormat = strToFormat.replace(italics, '_');
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
			strToFormat = strToFormat.replace("@here", "`@here`");
			if (strToFormat.contains(escapePrefix)) {
				String[] message = LilGUtil.splitMessage(strToFormat, false);
				for (int i = 0; i < message.length; i++) {
					if (!message[i].startsWith(escapePrefix)) continue;
					message[i] = message[i].substring(escapePrefix.length());
					switch (message[i]) {
						case "last":
							message[i] = Main.LastUserToSpeak.get(channel).getAsMention();
					}
				}
				strToFormat = argJoiner(message);
			}
			Map<String, Member> names = new HashMap<>();
			for (Member member : channel.getMembers()) {
				names.put(member.getUser().getName(), member);
				if (member.getNickname() != null) {
					names.put(member.getNickname(), member);
				}
			}
			//String strLower = strToFormat.toLowerCase();
			//boolean usesNick;
			String[] message = strToFormat.split(" ");
			int score;
			for (String aMessage : message) {
				if (!aMessage.startsWith("@")) continue;
				ExtractedResult result1 = FuzzySearch.extractOne(aMessage.substring(1), names.keySet());
				score = result1.getScore();
				score -= Math.abs(aMessage.replace(" ", "").length() - result1.getString().replace(" ", "").length()) * 4;
				LOGGER.debug(String.format("Found Name %s from %s with score %d. Mention %s",
						result1.getString(),
						aMessage,
						score,
						names.get(result1.getString()).getAsMention()
				));
				if (score < 75) {
					LOGGER.debug("Ignoring mention");
					continue;
				}
				strToFormat = strToFormat.replace(aMessage, names.get(result1.getString()).getAsMention());
			}
			/*
			for (Member member : channel.getMembers()) {
				String memberName = member.getEffectiveName().toLowerCase();
				String userName = member.getUser().getName().toLowerCase();
				while (strLower.contains("@" + memberName) || strLower.contains("@" + userName)) {
					usesNick = true;
					int index = strLower.indexOf(memberName);
					if (index == -1) {
						index = strLower.indexOf(userName);
						usesNick = false;
					}
					strToFormat = strToFormat.substring(0, index - 1) +
							member.getAsMention() +
							strToFormat.substring(index + (usesNick ? memberName : userName).length());
					strLower = strToFormat.toLowerCase();
				}
			}
			*/
		}
		if (strToFormat.contains(color + "")) {
			strToFormat = strToFormat.replaceAll(color + "[0-9]{1,2},[0-9]{1,2}", "").replaceAll(color + "[0-9]{1,2}", "");
			strToFormat = strToFormat.replace(color + "", "");
		}

		return strToFormat;
	}

	private static void handleCommand(String command, String[] args, Object eventObj, byte configID, boolean IRC) { // if IRC is true, then command called from IRC
		switch (command.toLowerCase()) {
			case "help": {
				if (args.length > 0) {
					switch (args[0].toLowerCase()) {
						case "help": {
							sendMessage(eventObj, ">_>", IRC);
						}
						break;
						case "whois": {
							sendMessage(eventObj, "This command tells you info about a user from the other side of the bridge. The only argument is the name of the user", IRC);
						}
						break;
						case "ison": {
							sendMessage(eventObj, "This command tells you if a user on the other side of the bridge is online. The only argument is the name of the user", IRC);
						}
						break;
					}
				} else {
					sendMessage(eventObj, "Run of the mill help command, for help with a command, just use the command name as the argument. List of commands [whois, ison]", IRC);
				}
			}
			return;
			case "whois": {
				if (args.length > 0) {
					String name = argJoiner(args, 0);
					if (IRC) {
						MessageEvent event = (MessageEvent) eventObj;
						if (event.getUser() == null) throw new NullPointerException("event.getUser()");
						List<Member> members = getDiscordChannel(configID, event).getGuild().getMembersByEffectiveName(name, true);
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
							List<Activity> activityList = member.getActivities();
							if (!activityList.isEmpty()) {
								streaming = false;
								game = ""; // Make java's linter happy
								for (Activity activity :
										activityList) {
									streaming = activity.getType() != Activity.ActivityType.DEFAULT;
									game = streaming ? activity.getName() : activity.getUrl();
								}
							} else {
								streaming = false;
								game = "nothing";
							}
							joinDate = member.getTimeJoined().toLocalDateTime().toString();
							registerDate = member.getUser().getTimeCreated().toLocalDateTime().toString();
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
						GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
						String nick, username, hostname;
						String hostmask, realName, awayMsg, server;
						boolean away;
						for (User user : getIRCChannel(configID, event).getUsers()) {
							nick = user.getNick();
							username = user.getLogin();
							hostname = user.getHostname();
							if (!(LilGUtil.equalsAnyIgnoreCase(name, nick, username, hostname) || LilGUtil.startsWithAny(name, nick, username, hostname)))
								continue;
							realName = user.getRealName();
							hostmask = user.getHostmask();
							away = user.isAway();
							awayMsg = user.getAwayMessage();
							server = user.getServer();
							StringBuilder channelsBuilder = new StringBuilder();
							boolean first = true;
							for (Channel channel : user.getChannels()) {
								UserLevel userLevel = getUserLevel(channel.getUserLevels(user));
								if (!first) {
									channelsBuilder.append(", ");
								}
								if (userLevel == null) {
									channelsBuilder.append(channel.getName());
								} else {
									channelsBuilder.append(userLevel.getSymbol()).append(channel.getName());
								}
								first = false;
							}
							sendMessage(eventObj, String.format(
									"```\n" +
											"%s is %s\n" +
											"%1$s's real name: %s\n" +
											"%s" +
											"%1$s's channels: %s\n" +
											"%1$s's server: %s\n" +
											"```", nick, hostmask, realName, away ? nick + " Is away: " + awayMsg : "", channelsBuilder, server
							), false);
							break;
						}
					}
				} else {
					sendMessage(eventObj, "Missing argument", IRC);
				}
			}
			return;
			case "ison": {
				if (args.length > 0) {
					String name = argJoiner(args, 0);
					if (IRC) {
						MessageEvent event = (MessageEvent) eventObj;
						List<Member> members = getDiscordChannel(configID, event).getGuild().getMembersByEffectiveName(name, true);
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
						GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
						String nick, username, hostname;
						User user = null;
						for (User curUser : getIRCChannel(configID, event).getUsers()) {
							nick = curUser.getNick();
							username = curUser.getLogin();
							hostname = curUser.getHostname();
							if (LilGUtil.equalsAnyIgnoreCase(name, nick, username, hostname) || LilGUtil.startsWithAny(name, nick, username, hostname)) {
								user = curUser;
								break;
							}
						}
						if (user != null) {
							sendMessage(eventObj, user.getNick() + " Is online", false);
						} else {
							sendMessage(eventObj, name + " Is not online", false);
						}
					}
				}
			}
			return;
			case "topic": {
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
					sendMessage(eventObj, String.format("Topic: \"%s\" set by %s at %s", event.getChannel().getTopic(), event.getChannel().getTopicSetter(), event.getChannel().getTopicTimestamp()), true);
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
					sendMessage(eventObj, String.format("Topic: %s", event.getChannel().getTopic()), false);
				}
			}
			return;
			case "rehash": {
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
					if (checkPerm(configID, event)) {
						Main.rehash();
						if (event.getUser() == null) throw new NullPointerException("event.getUser()");
						event.getUser().send().notice("Rehash complete");
					}
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
					if (checkPerm(configID, event)) {
						Main.rehash();
						event.getAuthor().openPrivateChannel().queue(s -> s.sendMessage("Rehash complete").queue());
					}
				}
			}
			return;
			case "prune": {
				TextChannel channel;
				List<Message> messages;
				String pattern = args[0];
				String user = "*";
				//String pruner;
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
					if (checkPerm(configID, event)) {
						channel = getDiscordChannel(configID, event);
						//pruner = event.getUser().getNick();
					} else {
						return;
					}
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
					if (checkPerm(configID, event)) {
						channel = event.getChannel();
						//pruner = event.getMember().getEffectiveName();
					} else {
						return;
					}
				}

				messages = channel.getHistory().retrievePast(100).complete();
				if (LilGUtil.equalsAnyIgnoreCase(pattern, "-u", "--user")) {
					if (args.length < 3) {
						if (IRC) {
							((MessageEvent) eventObj).respond("Missing args");
						} else {
							channel.sendMessage("Missing args").queue();
						}
						return;
					}
					user = args[1];
					pattern = args[2];
				}

				for (Message message : messages) {
					if (message.getMember() != channel.getGuild().getSelfMember()) continue;
					if (LilGUtil.wildCardMatch(message.getContentStripped().replace(DiscordListener.zeroWidthSpace + "", ""),
							"<*" + user + "> " + pattern)) {
						message.delete()/*.reason("Pruned by " + pruner + " on " + (IRC ? "IRC" : "Discord"))*/.queue();
					}
				}
			}
			break;
			case "ban": {
				String name = argJoiner(args, 0);
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
					if (!checkPerm(configID, event)) return;

					Guild guild = getDiscordChannel(configID, event).getGuild();
					List<Member> members = guild.getMembersByEffectiveName(name, true);
					if (!members.isEmpty()) {
						if (members.size() == 1) {
							members.get(0).ban(0, "Banned by " + event.getUserHostmask()).queue();
						} else {
							event.respond(String.format("Found multiple users: %s", members));
						}
					} else {
						event.respond(String.format("No one with the name \"%s\" was found", name));
					}
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
					if (!checkPerm(configID, event)) return;
				}
			}
			break;
			case "permission": {
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
					UserLevel userLevel = getUserLevel(event.getUser().getUserLevels(event.getChannel()));
					for (UserLevel level : UserLevel.values()) {

					}
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
				}
			}
			break;
		}
	}

	/* command template
				if (IRC) {
					MessageEvent event = (MessageEvent) eventObj;
				} else {
					GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) eventObj;
				}
	 */

	static String formatString(String message) {
		final char underline = '\u001F';
		final char italics = '\u001D';
		final char bold = '\u0002';
		final char reverse = '\u0016';
		message = message.replace("%", "%%");

		boolean supportCodeBlocks = false;
		GitHubClient client = new GitHubClient();
		if (Main.config[0].GithubGistOAuthToken != null) {
			supportCodeBlocks = true;
			client.setOAuth2Token(Main.config[0].GithubGistOAuthToken);
		} else if (Main.config[0].GithubCreds != null && Main.config[0].GithubCreds.length >= 2) {
			supportCodeBlocks = true;
			client.setCredentials(Main.config[0].GithubCreds[0], Main.config[0].GithubCreds[1]);
		}

		if (supportCodeBlocks) {
			String[] codeblocks = StringUtils.substringsBetween(message, "```", "```");
			if (codeblocks != null) {
				String lang = "";
				Gist gist = new Gist().setDescription("Discord code block");
				Map<String, GistFile> files = new HashMap<>();
				for (int i = 0; i < codeblocks.length; i++) {
					final String codeblock = codeblocks[i];
					int newlinePos = 0;
					if (codeblock.contains("\n")) {
						newlinePos = codeblock.indexOf('\n');
						lang = codeblock.substring(0, newlinePos);
					}
					if (languages.containsKey(lang.toLowerCase())) {
						GistFile file = new GistFile().setContent(codeblock.substring(newlinePos + 1));
						files.put(String.format("Block%d.%s", i, languages.get(lang)), file);
					} else {
						GistFile file = new GistFile().setContent(codeblock);
						files.put(String.format("Block%d.txt", i), file);
					}
				}

				try {
					gist.setFiles(files);
					gist = new GistService(client).createGist(gist);
					String url = gist.getHtmlUrl();
					byte index = 0;
					for (GistFile file : gist.getFiles().values()) {
						message = message.replace(
								String.format("```%s```", codeblocks[index]),
								String.format("%s#file-block%d-%s", url, index++,
										file.getFilename().substring(
												file.getFilename().lastIndexOf('.')
										)
								)
						);
					}
				} catch (IOException e) {
					LOGGER.error("Problem uploading gist", e);
				}
			}
		}

		// find links
		String[] parts = LilGUtil.extractUrls(message).toArray(new String[0]);
		for (String url : parts) {
			message = message.replace(url, "%s");
		}

		int inlineCodeCount = StringUtils.countMatches(message, "`");
		if (inlineCodeCount > 1) {
			if (inlineCodeCount % 2 != 0) {
				for (int count = 0; count < inlineCodeCount; count++) {
					message = message.replace('`', reverse);
				}
			} else {
				message = message.replace('`', reverse);
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
					message = message.replace('_', italics);
				}
			} else {
				if (italicsCount == 2) {
					message = '\0' + message; //.replace("_", "");
				} // else
				message = message.replace('_', italics);
			}
		}
		italicsCount = StringUtils.countMatches(message, "*");
		if (italicsCount > 1) {
			if (italicsCount % 2 != 0) {
				for (int count = 0; count < italicsCount; count++) {
					message = message.replace('*', italics);
				}
			} else {
				message = message.replace('*', italics);
			}
		}
		message = message
				.replace('\u0007', '\u2407')
				.replace("\n", " \u2424")
				.replace('\r', '\u240d');
		return String.format(message, (Object[]) parts);
	}

	private static Map<String, String> initLangMap() {
		Map<String, String> map = new HashMap<>();
		map.put("java", "java");
		map.put("cpp", "cpp");
		map.put("c", "c");
		map.put("csharp", "cs");
		map.put("d", "d");
		return map;
	}

}
