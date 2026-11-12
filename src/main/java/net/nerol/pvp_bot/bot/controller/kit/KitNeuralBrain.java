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
 * Kit DQN brain: {@link KitObservation} (66 floats) + the 5 held movement keys -> policy_kit.json MLP
 * -> {@link KitAction}. The policy is exported by {@code RL/PythonTrainer/demo_kit_env.py}; its
 * {@code obs_names} must equal {@link KitObservation#NAMES} + {@link #KEY_NAMES} or the brain refuses
 * to load. Its {@code action_names} pick which {@link KitAction}s the outputs mean (the sword kit uses
 * only IDLE .. JUMP). Without turning actions the aim stays locked on the target, as in training.
 * If kit_obs_fixture.json (from {@code kit_obs_parity.py}) is on the classpath, the encoder is
 * checked against it once and the max difference is logged.
 */
public final class KitNeuralBrain implements BotBrain {
    public static final String POLICY_RESOURCE = "/assets/pvp_bot/policy_kit.json";
    public static final String FIXTURE_RESOURCE = "/assets/pvp_bot/kit_obs_fixture.json";
    public static final String[] KEY_NAMES = {"key_fwd", "key_back", "key_left", "key_right", "key_sprint"};

    private static final class Spec { int obs_dim; int actions; String kit; String[] obs_names; String[] action_names; }
    private static final class Fixture { String[] obs_names; Case[] cases; }
    private static final class Case { KitObservation.Raw raw; float[] obs; }

    private final NeuralPolicy policy;
    private final KitAction[] actions;        // policy output index -> KitAction
    private final boolean aimLock;
    private Vec3 prevSelf;
    private Vec3 prevTarget;
    private LivingEntity lastTarget;

    public KitNeuralBrain() {
        Spec spec = readJson(POLICY_RESOURCE, Spec.class);
        if (spec == null) {
            throw new IllegalStateException("policy resource not found on classpath: " + POLICY_RESOURCE);
        }
        String[] names = Arrays.copyOf(KitObservation.NAMES, KitObservation.OBS_DIM + KEY_NAMES.length);
        System.arraycopy(KEY_NAMES, 0, names, KitObservation.OBS_DIM, KEY_NAMES.length);
        if (spec.obs_dim != names.length || !Arrays.equals(spec.obs_names, names)
                || spec.action_names == null || spec.action_names.length != spec.actions) {
            throw new IllegalStateException("policy_kit.json does not match this mod's kit observation/actions "
                    + "(obs_dim=" + spec.obs_dim + ", actions=" + spec.actions
                    + "); export it with: python demo_kit_env.py <ckpt> <kit> <json>");
        }
        this.actions = new KitAction[spec.actions];
        boolean turns = false;
        for (int i = 0; i < spec.actions; i++) {
            actions[i] = KitAction.valueOf(spec.action_names[i].toUpperCase(java.util.Locale.ROOT));
            turns |= actions[i].ordinal() >= KitAction.YAW_L.ordinal()
                    && actions[i].ordinal() <= KitAction.LOOK_AT_TARGET.ordinal();
        }
        this.aimLock = !turns;
        this.policy = NeuralPolicy.load(POLICY_RESOURCE, names.length);
        if (policy.numActions() != actions.length) {
            throw new IllegalStateException("policy_kit.json has " + policy.numActions() + " outputs but "
                    + actions.length + " action_names");
        }
        System.out.printf("Loaded kit policy (kit=%s, %d actions)%n", spec.kit, actions.length);
        checkParity();
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        if (target != lastTarget) {           // new opponent: no valid previous position
            prevTarget = null;
            lastTarget = target;
        }
        if (aimLock) {
            pack.setLookAtTarget(true);
        }
        Vec3 self = bot.position();
        Vec3 tgt = target.position();
        if (prevSelf != null) {
            KitActionExecutor.sprintExhaustion(bot, self.x - prevSelf.x, self.z - prevSelf.z);
        }
        float[] base = KitObservation.encode(KitObservation.gather(bot, target, prevSelf, prevTarget));
        float[] obs = Arrays.copyOf(base, base.length + KEY_NAMES.length);
        obs[base.length] = pack.isForward() ? 1f : 0f;
        obs[base.length + 1] = pack.isBackward() ? 1f : 0f;
        obs[base.length + 2] = pack.isStrafeLeft() ? 1f : 0f;
        obs[base.length + 3] = pack.isStrafeRight() ? 1f : 0f;
        obs[base.length + 4] = pack.isSprinting() ? 1f : 0f;
        prevSelf = self;
        prevTarget = tgt;
        KitActionExecutor.execute(bot, pack, actions[policy.act(obs)]);
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
