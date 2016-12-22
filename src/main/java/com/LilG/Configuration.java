package com.LilG;

import java.util.ArrayList;
import java.util.HashMap;
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
    public List<List<String>> autoSendCommands = new ArrayList<>();
    public boolean floodProtection = true;
    public int floodProtectionDelay = 1000;
    public boolean ircNickColor = false;

    public String discordToken = "<Missing discord token in config>";

    public Map<String, String> channelMapping = new HashMap<>();
    public List<String> commandCharacters = new ArrayList<>();
}