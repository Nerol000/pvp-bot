package net.nerol.pvp_bot.bot.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads a Q-table produced by the simulator's {@code QTable.save(path)}.
 *  File format:
 *    numStates,numActions
 *    q[0][0],q[0][1],...,q[0][numActions-1]
 *    q[1][0],...
 *    ...
 *  Loaded from the classpath so it works in both dev and packaged jars. */
public final class QTableLoader {

    /** Default location for the trained Q-table. Place the file produced by
     *  simulator's {@code Main.java} at
     *  src/main/resources/assets/pvp_bot/qtable.csv before building the mod. */
    public static final String DEFAULT_RESOURCE = "/assets/pvp_bot/qtable.csv";

    public static double[][] load(String resourcePath) throws IOException {
        InputStream stream = QTableLoader.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Q-table resource not found on classpath: " + resourcePath);
        }

        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String[] header = r.readLine().split(",");
            int numStates = Integer.parseInt(header[0]);
            int numActions = Integer.parseInt(header[1]);

            double[][] q = new double[numStates][numActions];
            for (int s = 0; s < numStates; s++) {
                String[] cells = r.readLine().split(",");
                for (int a = 0; a < numActions; a++) {
                    q[s][a] = Double.parseDouble(cells[a]);
                }
            }
            return q;
        }
    }

    private QTableLoader() {}
}
