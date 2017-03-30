//package com.LilG;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by lil-g on 1/22/17.
 */
public class Wrapper {

    public static void main(String[] args) throws IOException, InterruptedException, URISyntaxException {
        String jarName = "dIRCord.jar";
        String configPath = "";
        if (args.length > 1) {
            configPath = args[1];
            jarName = args[0];
        } else if (args.length > 0) {
            jarName = args[0];
        }
        if (jarName.equalsIgnoreCase("-h") || jarName.equalsIgnoreCase("--help")) {
            System.out.println("Arguments: \nName of jar: default \"dIRCord.jar\"\nPath to configuration json: default \"config.json\"");
            return;
        }
        File thisJar = new File(jarName);
        File newJar = new File(jarName + ".new");
        System.out.printf("Wrapper started: \njar name: %s \njar file path: %s \nnew jar path: %s\n", jarName, thisJar.getAbsolutePath(), newJar.getAbsolutePath());
        for (; ; ) {
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", jarName, configPath);
            builder.inheritIO();
            Process ps = builder.start();
            ps.waitFor();
            int exitVal = ps.exitValue();
            switch (exitVal) {
                case 0:
                    return;
                case 1:
                    if (newJar.exists()) {
                        if (newJar.renameTo(thisJar)) {
                            System.out.println("File renamed");
                        } else {
                            System.out.println("couldn't rename file");
                            System.exit(-1);
                        }
                    } else {
                        System.out.println("File doesn't exist");
                        System.exit(-1);
                    }
                    continue;
                default:
                    return;
            }
        }
    }
}
/*
,
    "#ssrg": "#SSRG"
*/