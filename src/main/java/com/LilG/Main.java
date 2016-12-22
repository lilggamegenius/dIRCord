package com.LilG;

import ch.qos.logback.classic.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.pircbotx.MultiBotManager;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
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
    public static JDA jda;
    public static PircBotX pircBotX;
    public static Configuration[] config;

    public static void main(String args[]) throws LoginException, InterruptedException, RateLimitedException {
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
            Configuration config = Main.config[0];
            org.pircbotx.Configuration ircConfig;
            org.pircbotx.Configuration.Builder configBuilder = new org.pircbotx.Configuration.Builder()
                    .setAutoReconnectDelay(connectDelay)
                    .setEncoding(Charset.forName("UTF-8"))
                    .setAutoReconnect(true)
                    .setAutoReconnectAttempts(attempts)
                    .setNickservPassword(config.nickservPassword)
                    .setName(config.nickname) //Set the nick of the bot.
                    .setLogin(config.userName)
                    .setAutoSplitMessage(false)
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
            ircConfig = configBuilder.addListener(new IrcListener((byte) 0)).buildForServer(config.server, config.port);
            manager.addBot(ircConfig);
            manager.start();
            String token = config.discordToken;
            LOGGER.trace("Calling JDA Builder with token: " + token);
            jda = new JDABuilder(AccountType.BOT)
                    .setToken(token)
                    .setAutoReconnect(true)
                    .setAudioEnabled(true)
                    .setEnableShutdownHook(true)
                    .addListener(new DiscordListener((byte) 0))
                    .buildAsync();
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
}
