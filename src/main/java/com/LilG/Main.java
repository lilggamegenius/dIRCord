package com.LilG;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.pircbotx.MultiBotManager;
import org.pircbotx.UtilSSLSocketFactory;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Created by lil-g on 12/12/16.
 */
public class Main {
    public final static String kvircFlags = "\u00034\u000F";
    public final static MultiBotManager manager = new MultiBotManager();
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(Main.class);
    private final static int attempts = 10;
    private final static int connectDelay = 15 * 1000;
    public static long lastActivity = System.currentTimeMillis(); // activity as in people talking
    public static Configuration[] config;

    public static void main(String args[]) throws LoginException, InterruptedException, RateLimitedException {
        LOGGER.setLevel(Level.ALL);
        new Thread(() -> {
            try {
                LOGGER.trace("Starting thread");
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(60 * 1000);
                    if (Main.lastActivity + 1000 * 60 * 10 < System.currentTimeMillis()) {
                        LOGGER.trace("Checking for new build");
                        Main.checkForNewBuild();
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

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File configFile = new File(configFilePath);
        LOGGER.info("Path = " + configFile.getAbsolutePath());
        try (Reader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, Configuration[].class);
            for (byte i = 0; i < config.length; i++) {
                Configuration config = Main.config[i];
                config.channelMapping = HashBiMap.create(config.channelMapping);
                org.pircbotx.Configuration ircConfig;
                org.pircbotx.Configuration.Builder configBuilder = new org.pircbotx.Configuration.Builder()
                        .setAutoReconnectDelay(connectDelay)
                        .setEncoding(Charset.forName("UTF-8"))
                        .setAutoReconnect(true)
                        .setAutoReconnectAttempts(attempts)
                        .setNickservPassword(config.nickservPassword)
                        .setName(config.nickname) //Set the nick of the bot.
                        .setLogin(config.userName)
                        .setAutoSplitMessage(config.autoSplitMessage)
                        .setRealName(kvircFlags + config.realName);
                for (String channel : config.channelMapping.values()) {
                    String[] channelValues = channel.split(" ", 1);
                    if (channelValues.length > 1) {
                        configBuilder.addAutoJoinChannel(channelValues[0], channelValues[1]);
                    } else {
                        configBuilder.addAutoJoinChannel(channelValues[0]);
                    }
                }
                if (config.SSL) {
                    configBuilder.setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates());
                }
                config.ircListener = new IrcListener(i);
                config.discordListener = new DiscordListener(i);
                ircConfig = configBuilder.addListener(config.ircListener).buildForServer(config.server, config.port);
                manager.addBot(ircConfig);
                manager.start();
                String token = config.discordToken;
                LOGGER.trace("Calling JDA Builder with token: " + token);
                config.jda = new JDABuilder(AccountType.BOT)
                        .setToken(token)
                        .setAutoReconnect(true)
                        .setAudioEnabled(true)
                        .setEnableShutdownHook(true)
                        .addEventListener(config.discordListener)
                        .buildAsync();
            }
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

    public static void checkForNewBuild() throws URISyntaxException, IOException {
        if (Thread.holdsLock(kvircFlags)) return;
        synchronized (kvircFlags) {
            File thisJar = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File newJar = new File(thisJar.getParent(), thisJar.getName() + ".new");
            LOGGER.trace("This jar: " + thisJar.getName() + " New jar: " + newJar.getName());
            if (!newJar.exists()) {
                LOGGER.trace("no new build found");
                return;
            }
            LOGGER.trace("Found build, exiting with code 1");
            manager.stop("Updating bridge");
            for (Configuration configuration : config) {
                configuration.jda.shutdown();
            }
            System.exit(1); // tell wrapper that new jar was found
        }
    }
}
