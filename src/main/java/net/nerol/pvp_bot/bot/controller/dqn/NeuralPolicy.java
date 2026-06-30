package net.nerol.pvp_bot.bot.controller.dqn;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the MLP exported by {@code RL/PythonTrainer/export_weights.py} (policy.json) and runs the
 * forward pass by hand — a few hundred multiply-adds per tick, no ONNX/native dependency. ReLU is
 * applied between layers but not after the output layer; {@link #act} returns the arg-max action.
 */
public final class NeuralPolicy {

    public static final String DEFAULT_RESOURCE = "/assets/pvp_bot/policy.json";

    private static final class LayerData { float[][] w; float[] b; }      // w: [out][in], b: [out]
    private static final class PolicyData { int obs_dim; int actions; LayerData[] layers; }

    private final float[][][] w;   // [layer][out][in]
    private final float[][] b;     // [layer][out]

    private NeuralPolicy(PolicyData data) {
        int n = data.layers.length;
        this.w = new float[n][][];
        this.b = new float[n][];
        for (int l = 0; l < n; l++) {
            this.w[l] = data.layers[l].w;
            this.b[l] = data.layers[l].b;
        }
    }

    public static NeuralPolicy load(String resourcePath) {
        try (InputStream in = NeuralPolicy.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("policy resource not found on classpath: " + resourcePath);
            }
            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            PolicyData data = new Gson().fromJson(r, PolicyData.class);
            if (data == null || data.layers == null || data.layers.length == 0) {
                throw new IllegalStateException("empty or invalid policy json");
            }
            return new NeuralPolicy(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load neural policy from " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /** Forward pass + arg-max. {@code obs} length must equal the network's input dimension. */
    public int act(float[] obs) {
        float[] x = obs;
        for (int l = 0; l < w.length; l++) {
            boolean last = (l == w.length - 1);
            float[] y = new float[b[l].length];
            for (int o = 0; o < y.length; o++) {
                float[] wo = w[l][o];
                float s = b[l][o];
                for (int i = 0; i < x.length; i++) {
                    s += wo[i] * x[i];
                }
                y[o] = last ? s : Math.max(0f, s);
            }
            x = y;
        }
        int best = 0;
        for (int a = 1; a < x.length; a++) {
            if (x[a] > x[best]) best = a;
        }
        return best;
    }
}
