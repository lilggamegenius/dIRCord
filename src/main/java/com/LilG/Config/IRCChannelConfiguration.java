package com.LilG.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IRCChannelConfiguration implements ChannelConfiguration {
	public final boolean joins;
	public final boolean quits;
	public final boolean parts;
	public final String[] commandCharacters;
	public final Map<String, String> ignoreUserMessageIf;
	public transient final List<String> spamFilterList;

	public IRCChannelConfiguration() {
		joins = true;
		quits = true;
		parts = true;
		commandCharacters = new String[0];
		ignoreUserMessageIf = new HashMap<>();
		spamFilterList = new ArrayList<>();
	}

	@Override
	public String[] getCommmandCharacters() {
		return commandCharacters;
	}
}
