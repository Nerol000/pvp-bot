package net.nerol.pvp_bot.bot.reader;

import net.nerol.pvp_bot.bot.action.BotAction;

import java.io.*;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.strip;

public class CSVReader {
    public static final String bot_replay = "../src/main/resources/assets/pvp_bot/bot_replay.csv";

    public static List<BotAction> load(String file) {
        List<BotAction> actions = new ArrayList<>();

        try {
            File f = new File(file);
            System.out.println("Absolute path: " + f.getAbsolutePath());
            System.out.println("Exists: " + f.exists());
            System.out.println("Readable: " + f.canRead());
            if (!f.exists()) {
                throw new RuntimeException("CSV NOT FOUND");
            }

            BufferedReader reader = new BufferedReader(new FileReader(file));

            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
                String[] p = line.split(",");

                // action column
                String actionStr = strip(p[11]);
                BotAction action = BotAction.valueOf(actionStr);
                actions.add(action);

                //System.out.println(actionStr);

            }
            reader.close();
        } catch (Exception e) {
            System.out.printf("Could not load file with name %s%n", file);
            e.printStackTrace();
        }

        return actions;
    }
}
