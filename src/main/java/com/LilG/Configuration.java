package com.LilG;

import com.google.common.collect.HashBiMap;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.TextChannel;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by lil-g on 12/12/16.
 */

public class Configuration {
    public String nickname = "dIRCord<Missing nick in config>";
    public String userName = "dIRCord<Missing username in config>";
    public String realName = "dIRCord - Discord IRC Bridge";
    public String server = "<Missing server in config>";
    public int port = 6667;
    public boolean SSL = false;
    public String nickservPassword = "<Missing nickserv password in config>";
    public boolean autoSplitMessage = false;
    public List<String> autoSendCommands = new ArrayList<>();
    public boolean floodProtection = true;
    public int floodProtectionDelay = 1000;
    public boolean ircNickColor = false;
    public String discordToken = "<Missing discord token in config>";

    public Map<String, String> channelMapping = HashBiMap.create();

    public List<String> commandCharacters = new ArrayList<>();

    public int minutesOfInactivityToUpdate = 10;

    public transient HashBiMap<TextChannel, Channel> channelMapObj = HashBiMap.create();
    public transient IrcListener ircListener;
    public transient DiscordListener discordListener;
    public transient Bridge bridge;
    public transient PircBotX pircBotX;
    public transient JDA jda;
}