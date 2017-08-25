package com.LilG.Config;

import java.util.ArrayList;
import java.util.List;

public class IRCChannelConfiguration implements ChannelConfiguration {
	public final boolean joins;
	public final boolean quits;
	public final boolean parts;
	public final String[] commandCharacters;
	public transient final List<String> spamFilterList;

	public IRCChannelConfiguration() {
		joins = true;
		quits = true;
		parts = true;
		commandCharacters = new String[0];
		spamFilterList = new ArrayList<>();
	}

	@Override
	public String[] getCommmandCharacters() {
		return commandCharacters;
	}
}
