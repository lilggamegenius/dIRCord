package com.LilG.Config;

public class IRCChannelConfiguration implements ChannelConfiguration {
	public final boolean joins;
	public final boolean quits;
	public final boolean parts;
	public final String[] commandCharacters;

	public IRCChannelConfiguration() {
		joins = true;
		quits = true;
		parts = true;
		commandCharacters = new String[0];
	}

	@Override
	public String[] getCommmandCharacters() {
		return commandCharacters;
	}
}
