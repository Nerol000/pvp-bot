package net.nerol.pvp_bot.bot.controller.kit;

import com.google.gson.Gson;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotBrain;
import net.nerol.pvp_bot.bot.controller.dqn.NeuralPolicy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Kit DQN brain: {@link KitObservation} (66 floats) -> policy_kit.json MLP -> {@link KitAction} (22).
 * The policy is exported by {@code RL/PythonTrainer/export_kit_policy.py}; its {@code obs_names}
 * must equal {@link KitObservation#NAMES} or the brain refuses to load.
 * If kit_obs_fixture.json (from {@code kit_obs_parity.py}) is on the classpath, the encoder is
 * checked against it once and the max difference is logged.
 */
public final class KitNeuralBrain implements BotBrain {
    public static final String POLICY_RESOURCE = "/assets/pvp_bot/policy_kit.json";
    public static final String FIXTURE_RESOURCE = "/assets/pvp_bot/kit_obs_fixture.json";

    private static final class Spec { int obs_dim; int actions; String kit; String[] obs_names; }
    private static final class Fixture { String[] obs_names; Case[] cases; }
    private static final class Case { KitObservation.Raw raw; float[] obs; }

    private final NeuralPolicy policy;
    private Vec3 prevSelf;
    private Vec3 prevTarget;
    private LivingEntity lastTarget;

    public KitNeuralBrain() {
        Spec spec = readJson(POLICY_RESOURCE, Spec.class);
        if (spec == null) {
            throw new IllegalStateException("policy resource not found on classpath: " + POLICY_RESOURCE);
        }
        if (spec.obs_dim != KitObservation.OBS_DIM || spec.actions != KitAction.values().length
                || !Arrays.equals(spec.obs_names, KitObservation.NAMES)) {
            throw new IllegalStateException("policy_kit.json does not match this mod's kit observation/actions "
                    + "(obs_dim=" + spec.obs_dim + ", actions=" + spec.actions + ")");
        }
        this.policy = NeuralPolicy.load(POLICY_RESOURCE);
        System.out.printf("Loaded kit policy (kit=%s)%n", spec.kit);
        checkParity();
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        if (target != lastTarget) {           // new opponent: no valid previous position
            prevTarget = null;
            lastTarget = target;
        }
        Vec3 self = bot.position();
        Vec3 tgt = target.position();
        if (prevSelf != null) {
            KitActionExecutor.sprintExhaustion(bot, self.x - prevSelf.x, self.z - prevSelf.z);
        }
        float[] obs = KitObservation.encode(KitObservation.gather(bot, target, prevSelf, prevTarget));
        prevSelf = self;
        prevTarget = tgt;
        KitActionExecutor.execute(bot, pack, policy.act(obs));
    }

    private static <T> T readJson(String resource, Class<T> type) {
        try (InputStream in = KitNeuralBrain.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            return new Gson().fromJson(r, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + resource + ": " + e.getMessage(), e);
        }
    }

    private static void checkParity() {
        Fixture fx = readJson(FIXTURE_RESOURCE, Fixture.class);
        if (fx == null || fx.cases == null) return;
        if (!Arrays.equals(fx.obs_names, KitObservation.NAMES)) {
            System.out.println("[kit parity] fixture obs_names differ from KitObservation.NAMES");
            return;
        }
        double maxDiff = 0.0;
        int worst = -1;
        for (int i = 0; i < fx.cases.length; i++) {
            float[] got = KitObservation.encode(fx.cases[i].raw);
            for (int j = 0; j < got.length; j++) {
                double d = Math.abs(got[j] - fx.cases[i].obs[j]);
                if (d > maxDiff) {
                    maxDiff = d;
                    worst = j;
                }
            }
        }
        System.out.printf("[kit parity] %d cases, max |java - python| = %.2e%s%n", fx.cases.length, maxDiff,
                maxDiff > 1e-4 ? "  MISMATCH at " + KitObservation.NAMES[worst] : "");
    }
}
