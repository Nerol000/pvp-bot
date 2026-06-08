package net.nerol.pvp_bot.bot.reader;

import net.nerol.pvp_bot.bot.controller.BotAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.strip;

public class CSVReader {
    /** Classpath path to the recorded action sequence. Lives under
     *  src/main/resources/assets/pvp_bot/bot_replay.csv in the mod source tree
     *  and ends up at the root of the classpath in the built jar. */
    public static final String bot_replay = "/assets/pvp_bot/bot_replay.csv";

    /** Column index of the action name in the CSV layout written by Main.java. */
    private static final int ACTION_COLUMN = 11;

    public static List<BotAction> load(String resourcePath) {
        List<BotAction> actions = new ArrayList<>();

        InputStream stream = CSVReader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            System.out.printf("CSV resource not found on classpath: %s%n", resourcePath);
            return actions;
        }

        int skipped = 0;
        int lineNumber = 1; // header is line 1; data rows start at 2

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                String[] cols = line.split(",");
                if (cols.length <= ACTION_COLUMN) {
                    System.out.printf("Skipping CSV line %d: %d columns (need > %d)%n",
                            lineNumber, cols.length, ACTION_COLUMN);
                    skipped++;
                    continue;
                }

                String actionStr = strip(cols[ACTION_COLUMN]);
                try {
                    actions.add(BotAction.valueOf(actionStr));
                } catch (IllegalArgumentException e) {
                    System.out.printf("Skipping CSV line %d: unknown action '%s'%n",
                            lineNumber, actionStr);
                    skipped++;
                }
            }
        } catch (IOException e) {
            System.out.printf("Error reading CSV %s at line %d%n", resourcePath, lineNumber);
            e.printStackTrace();
        }

        if (skipped > 0) {
            System.out.printf("CSV %s: loaded %d actions, skipped %d malformed rows%n",
                    resourcePath, actions.size(), skipped);
        }

        return actions;
    }
}