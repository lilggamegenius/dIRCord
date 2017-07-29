package com.LilG.Config;

public class DiscordChannelConfiguration implements ChannelConfiguration {
	final String[] commandCharacters;

	public DiscordChannelConfiguration() {
		commandCharacters = new String[0];
	}

	@Override
	public String[] getCommmandCharacters() {
		return commandCharacters;
	}
}
