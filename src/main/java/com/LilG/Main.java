package com.LilG;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.LilG.Config.Configuration;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.apache.commons.lang3.StringUtils;
import org.pircbotx.MultiBotManager;
import org.pircbotx.UtilSSLSocketFactory;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lil-g on 12/12/16.
 */
public class Main {
	final static String errorMsg = ". If you see this a lot, add a issue on the Issue tracker https://github.com/lilggamegenuis/dIRCord/issues";
	private final static String kvircFlags = "\u00034\u000F";
	private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(Main.class);
	private final static int attempts = 10;
	private final static int connectDelay = 15 * 1000;
	private final static MultiBotManager manager = new MultiBotManager();
	private final static File thisJar;
	private final static long lastModified;
	private final static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static File configFile;
	static long lastActivity = System.currentTimeMillis(); // activity as in people talking
	static Configuration[] config;
	static Map<TextChannel, Member> LastUserToSpeak = new HashMap<>();

	static {
		File thisJar_;
		long lastModified_;
		try {
			thisJar_ = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			lastModified_ = thisJar_.lastModified();
		} catch (URISyntaxException e) {
			thisJar_ = new File(".");
			lastModified_ = System.currentTimeMillis();
			e.printStackTrace();
		}
		thisJar = thisJar_;
		lastModified = lastModified_;
	}

	public static void main(String args[]) {
		LOGGER.setLevel(Level.ALL);
		new Thread(() -> {
			try {
				LOGGER.trace("Starting updater thread");
				while (!Thread.currentThread().isInterrupted()) {
					Thread.sleep(60 * 1000);
					if (Main.lastActivity + 1000 * 60 * config[0].minutesOfInactivityToUpdate < System.currentTimeMillis()) {
						LOGGER.trace("Checking for new build");
						Main.checkForNewBuild(args);
					}
				}
			} catch (InterruptedException ignored) {
			} catch (Exception e) {
				LOGGER.error("Error in update thread", e);
			}
		}).start();
		String configFilePath;
		if (args.length == 0) {
			configFilePath = "config.json";
		} else {
			configFilePath = args[0];
		}
		configFile = new File(configFilePath);
		LOGGER.info("Path = " + configFile.getAbsolutePath());
		try (Reader reader = new FileReader(configFile)) {
			config = gson.fromJson(reader, Configuration[].class);
			if (config.length == 0) {
				LOGGER.error("Config file is empty");
				System.exit(-1);
			}
			for (byte i = 0; i < config.length; i++) {
				Configuration config = Main.config[i];
				config.channelMapping = HashBiMap.create(config.channelMapping);
				org.pircbotx.Configuration ircConfig;
				org.pircbotx.Configuration.Builder configBuilder = new org.pircbotx.Configuration.Builder()
						.setAutoReconnectDelay(connectDelay)
						.setEncoding(Charset.forName("UTF-8"))
						.setAutoReconnect(true)
						.setAutoReconnectAttempts(attempts)
						.setName(config.nickname) //Set the nick of the bot.
						.setLogin(config.userName)
						.setAutoSplitMessage(config.autoSplitMessage)
						.setRealName(kvircFlags + config.realName);
				if(StringUtils.isNotBlank(config.nickservPassword)){
					configBuilder.setNickservPassword(config.nickservPassword);
				}
				for (String channel : config.channelMapping.values()) {
					String[] channelValues = channel.split(" ", 1);
					if (channelValues.length > 1) {
						configBuilder.addAutoJoinChannel(channelValues[0], channelValues[1]);
					} else {
						configBuilder.addAutoJoinChannel(channelValues[0]);
					}
				}
				if (config.floodProtection) {
					configBuilder.setMessageDelay(config.floodProtectionDelay);
				}
				if (config.SSL) {
					configBuilder.setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates());
				}
				config.ircListener = new IrcListener(i);
				config.discordListener = new DiscordListener(i);
				ircConfig = configBuilder.addListener(config.ircListener).buildForServer(config.server, config.port);
				manager.addBot(ircConfig);
				String token = config.discordToken;
				LOGGER.trace("Calling JDA Builder with token: " + token);
				config.jda = new JDABuilder(AccountType.BOT)
						.setToken(token)
						.setAutoReconnect(true)
						.setEnableShutdownHook(true)
						.addEventListener(config.discordListener)
						.buildBlocking();
				LOGGER.trace("JDA built\n" + config.jda);
			}
			manager.start();
		} catch (JsonSyntaxException e) {
			try (FileWriter emptyFile = new FileWriter(new File("EmptyConfig.json"))) {
				LOGGER.error("Error reading config json", e);
				emptyFile.write(gson.toJson(new Configuration[]{new Configuration()}));
			} catch (Exception e2) {
				LOGGER.error("Error writing empty file", e2);
			}
		} catch (Exception e) {
			LOGGER.error("Error", e);
		}

	}

	private static void checkForNewBuild(String args[]) throws IOException {
		if (Thread.holdsLock(kvircFlags)) return;
		synchronized (kvircFlags) {
			File newJar = new File(thisJar.getParent(), thisJar.getName() + ".new");
			LOGGER.trace("This jar: " + thisJar.getName() + " New jar: " + newJar.getName());
			if (!newJar.exists() || thisJar.lastModified() == lastModified) {
				LOGGER.trace("no new build found");
				return;
			}
			LOGGER.trace("Found build, exiting with code 1");
			manager.stop("Updating bridge");
			for (Configuration configuration : config) {
				configuration.jda.shutdown();
			}
			StringBuilder cmd = new StringBuilder();
			cmd.append(System.getProperty("java.home")).append(File.separator).append("bin").append(File.separator).append("java ");
			for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
				cmd.append(jvmArg).append(" ");
			}
			cmd.append("-cp ").append(ManagementFactory.getRuntimeMXBean().getClassPath()).append(" ");
			cmd.append(Main.class.getName()).append(" ");
			for (String arg : args) {
				cmd.append(arg).append(" ");
			}
			Runtime.getRuntime().exec(cmd.toString());
			System.exit(1); // tell wrapper that new jar was found
		}
	}

	public static void rehash() {
		try (Reader reader = new FileReader(configFile)) {
			Configuration[] configs = gson.fromJson(reader, Configuration[].class);
			for (byte i = 0; i < configs.length; i++) {
				Configuration config = configs[i];
				config.channelMapObj = Main.config[i].channelMapObj;
				config.ircListener = Main.config[i].ircListener;
				config.discordListener = Main.config[i].discordListener;
				config.pircBotX = Main.config[i].pircBotX;
				config.jda = Main.config[i].jda;
				if (!config.discordToken.equals(Main.config[i].discordToken))
					LOGGER.info("Discord token change will take affect on next restart");
				if (!config.server.equals(Main.config[i].server) ||
						config.port != Main.config[i].port ||
						config.SSL != Main.config[i].SSL) {
					LOGGER.info("IRC server changes will take affect on next restart");
					continue;
				}
				config.channelMapping = HashBiMap.create(config.channelMapping);
				List<String> channelsToJoin = new ArrayList<>();
				List<String> channelsToJoinKeys = new ArrayList<>();
				List<String> channelsToPart = new ArrayList<>(Main.config[i].channelMapping.values());
				for (String channel : config.channelMapping.values()) {
					String[] channelValues = channel.split(" ", 1);
					if (!channelsToPart.remove(channelValues[0])) {
						channelsToJoin.add(channelValues[0]);
						if (channelValues.length > 1) {
							channelsToJoinKeys.add(channelValues[1]);
						} else {
							channelsToJoinKeys.add(null);
						}
					}
				}
				for (String channelToPart : channelsToPart) {
					Main.config[i].pircBotX.sendRaw().rawLine("PART " + channelToPart + " :Rehashing");
				}
				for (int index = 0; index < channelsToJoin.size(); index++) {
					if (channelsToJoinKeys.get(index) != null) {
						Main.config[i].pircBotX.send().joinChannel(channelsToJoin.get(index), channelsToJoinKeys.get(index));
					} else {
						Main.config[i].pircBotX.send().joinChannel(channelsToJoin.get(index));
					}
				}
			}
			Main.config = configs;
		} catch (JsonSyntaxException | IllegalStateException e) {
			try (FileWriter emptyFile = new FileWriter(new File("EmptyConfig.json"))) {
				LOGGER.error("Error reading config json", e);
				emptyFile.write(gson.toJson(new Configuration[]{new Configuration()}));
			} catch (Exception e2) {
				LOGGER.error("Error writing empty file", e2);
			}
		} catch (Exception e) {
			LOGGER.error("Error", e);
		}
	}
}
