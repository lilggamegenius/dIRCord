package com.LilG.Config;

import com.LilG.DiscordListener;
import com.LilG.IrcListener;
import com.google.common.collect.HashBiMap;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lil-g on 12/12/16.
 */

public class Configuration {
	public String nickname = "dIRCord"; //<Missing nick in config>
	public String userName = nickname;
	public String realName = nickname + " " + userName;
	public String server = "<Missing server in config>";
	public int port = 6667;
	public boolean SSL = false;
	public String nickservPassword = null;
	public boolean autoSplitMessage = false;
	public List<String> autoSendCommands = new ArrayList<>();
	public boolean floodProtection = true;
	public int floodProtectionDelay = 1000;
	public boolean ircNickColor = false;
	public String discordToken = "<Missing discord token in config>";

	public String IRCBotOwnerHostmask;
	public String DiscordBotOwnerID;
	public String GithubGistOAuthToken;
	public String[] GithubCreds;

	public Map<String, String> channelMapping = HashBiMap.create();

	public ChannelConfigs channelOptions = new ChannelConfigs();

	public int minutesOfInactivityToUpdate = 10;

	public Map<String, String> AutoBan = new HashMap<>();
	public List<String> BanOnSight = new ArrayList<>();

	public transient HashBiMap<TextChannel, Channel> channelMapObj = HashBiMap.create();
	public transient IrcListener ircListener;
	public transient DiscordListener discordListener;
	public transient PircBotX pircBotX;
	public transient JDA jda;

	public class ChannelConfigs {
		public final Map<String, DiscordChannelConfiguration> Discord;
		public final Map<String, IRCChannelConfiguration> IRC;

		ChannelConfigs() {
			Discord = new HashMap<>();
			IRC = new HashMap<>();
		}
	}
}