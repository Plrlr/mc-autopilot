package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * The neural brain, as scripts/loop/mlp.py writes it: dense layers with ReLU between them, one
 * output per action kind (head "_v": the state's baseline). Weights are half-precision, base64,
 * input-major (w[i * out + j] links input i to output j). One forward pass scores every option of a
 * decision: ~110k multiply-adds, tens of microseconds on the game thread.
 */
final class MlpModel {
	private final int[] sizes;
	private final float[][] w, b;
	private final Map<String, Integer> heads = new HashMap<>();

	private MlpModel(int[] sizes) {
		this.sizes = sizes;
		w = new float[sizes.length - 1][];
		b = new float[sizes.length - 1][];
	}

	/** Reads the "mlp" object; throws on anything malformed (the caller then ignores the model). */
	static MlpModel parse(JsonObject o, int features) {
		JsonArray s = o.getAsJsonArray("sizes");
		int[] sizes = new int[s.size()];
		for (int i = 0; i < sizes.length; i++) sizes[i] = s.get(i).getAsInt();
		if (sizes.length < 2 || sizes[0] != features) throw new IllegalArgumentException("input size " + sizes[0] + " != " + features);
		MlpModel m = new MlpModel(sizes);
		JsonArray layers = o.getAsJsonArray("layers");
		if (layers.size() != sizes.length - 1) throw new IllegalArgumentException("layer count");
		for (int k = 0; k < layers.size(); k++) {
			JsonObject l = layers.get(k).getAsJsonObject();
			m.w[k] = halves(l.get("w").getAsString(), sizes[k] * sizes[k + 1]);
			m.b[k] = halves(l.get("b").getAsString(), sizes[k + 1]);
		}
		JsonArray h = o.getAsJsonArray("heads");
		if (h.size() != sizes[sizes.length - 1]) throw new IllegalArgumentException("head count");
		for (int i = 0; i < h.size(); i++) m.heads.put(h.get(i).getAsString(), i);
		return m;
	}

	private static float[] halves(String b64, int n) {
		byte[] raw = Base64.getDecoder().decode(b64);
		if (raw.length != 2 * n) throw new IllegalArgumentException("expected " + n + " weights, got " + raw.length / 2);
		ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
		float[] out = new float[n];
		for (int i = 0; i < n; i++) out[i] = Float.float16ToFloat(bb.getShort());
		return out;
	}

	/** Every head's output for state x. */
	double[] forward(double[] x) {
		float[] h = new float[x.length];
		for (int i = 0; i < x.length; i++) h[i] = (float) x[i];
		for (int k = 0; k < w.length; k++) {
			int in = sizes[k], out = sizes[k + 1];
			float[] next = b[k].clone();
			float[] wk = w[k];
			for (int i = 0; i < in; i++) {
				float hi = h[i];
				if (hi == 0) continue; // ReLU zeros are common: skip their row
				int row = i * out;
				for (int j = 0; j < out; j++) next[j] += hi * wk[row + j];
			}
			if (k < w.length - 1) for (int j = 0; j < out; j++) if (next[j] < 0) next[j] = 0;
			h = next;
		}
		double[] y = new double[h.length];
		for (int i = 0; i < h.length; i++) y[i] = h[i];
		return y;
	}

	/** The head's column, or -1. */
	int head(String name) {
		Integer i = heads.get(name);
		return i == null ? -1 : i;
	}

	int heads() {
		return heads.size();
	}

	int params() {
		int n = 0;
		for (int k = 0; k < w.length; k++) n += w[k].length + b[k].length;
		return n;
	}
}
