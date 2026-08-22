package net.nerol.pvp_bot.bot.controller.fsm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads a {@link Genome} exported by the Python trainer's {@code AdaptiveTeacher.export_genome()}
 * (RL/PythonTrainer/core/opponents.py), so the in-game {@code td_max} / {@code improve} behaviors
 * can run the ACTUAL evolved genome from a training run instead of a hand-picked representative.
 *
 * <p>The JSON schema is {@code {"arm":..., "genome_id":..., "value":..., "params":{...}}} where the
 * {@code params} keys are the snake_case knob names from the trainer's {@code ParameterizedFSM}.
 * Place an exported {@code *_genome.json} at {@code src/main/resources/assets/pvp_bot/} under the
 * expected name (e.g. {@code td_max_genome.json}) before building the mod.
 *
 * <p>Loading is best-effort: if the resource is missing or malformed, {@link #loadOrDefault} returns
 * the supplied fallback preset, so the behavior is always runnable even without an export.
 */
public final class GenomeLoader {

    public static final String TD_MAX_RESOURCE  = "/assets/pvp_bot/td_max_genome.json";
    public static final String IMPROVE_RESOURCE = "/assets/pvp_bot/improve_genome.json";

    /**
     * Load the genome at {@code resourcePath}; on any problem (missing file, bad JSON) return
     * {@code fallback}. Missing individual params fall back to the corresponding {@code fallback}
     * value, so a partial export still works.
     */
    public static Genome loadOrDefault(String resourcePath, Genome fallback) {
        try (InputStream in = GenomeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return fallback;
            }
            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonObject p = root.has("params") ? root.getAsJsonObject("params") : root;
            String label = root.has("arm") ? root.get("arm").getAsString() : fallback.label;
            return new Genome(label,
                    getD(p, "preferred_distance", fallback.preferredDistance),
                    getD(p, "band", fallback.band),
                    getD(p, "attack_prob", fallback.attackProb),
                    getD(p, "retreat_prob", fallback.retreatProb),
                    getD(p, "strafe_prob", fallback.strafeProb),
                    getI(p, "strafe_ticks", fallback.strafeTicks),
                    getD(p, "jump_prob", fallback.jumpProb),
                    getD(p, "pause_prob", fallback.pauseProb),
                    getI(p, "pause_ticks", fallback.pauseTicks),
                    getD(p, "aim_tol", fallback.aimTol),
                    getB(p, "wait_for_charge", fallback.waitForCharge),
                    getI(p, "stap_ticks", fallback.stapTicks),
                    getB(p, "bait", fallback.bait),
                    getB(p, "jump_reset", fallback.jumpReset));
        } catch (Exception e) {
            System.out.printf("Failed to load genome %s, using %s preset: %s%n",
                    resourcePath, fallback.label, e.getMessage());
            return fallback;
        }
    }

    private static double getD(JsonObject o, String key, double def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : def;
    }

    private static int getI(JsonObject o, String key, int def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? (int) Math.round(o.get(key).getAsDouble()) : def;
    }

    private static boolean getB(JsonObject o, String key, boolean def) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : def;
    }

    private GenomeLoader() {}
}