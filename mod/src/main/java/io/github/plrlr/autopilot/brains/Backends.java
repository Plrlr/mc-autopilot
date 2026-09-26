package io.github.plrlr.autopilot.brains;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** The tactician backends: Opus (claude -p), OpenAI-compatible free APIs (Groq, Cerebras) and Gemini. */
public final class Backends {
	private Backends() {}

	static JsonObject choiceSchema(List<String> enumValues, boolean additionalPropsFlag) {
		JsonObject choice = new JsonObject();
		choice.addProperty("type", "string");
		JsonArray e = new JsonArray();
		enumValues.forEach(e::add);
		choice.add("enum", e);
		JsonObject why = new JsonObject();
		why.addProperty("type", "string");
		JsonObject props = new JsonObject();
		props.add("choice", choice);
		props.add("why", why);
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", props);
		JsonArray req = new JsonArray();
		req.add("choice");
		req.add("why");
		schema.add("required", req);
		if (additionalPropsFlag) schema.addProperty("additionalProperties", false);
		return schema;
	}

	/** Opus as tactician, through the same claude -p route as the strategist. */
	public static final class Opus implements LlmBackend {
		private final ClaudeCli cli;
		private final RateLimiter limiter;
		private volatile String planProblem;
		private volatile long planProblemUntil;
		private volatile int failuresInRow;

		public Opus(ClaudeCli cli, RateLimiter limiter) {
			this.cli = cli;
			this.limiter = limiter;
		}

		@Override
		public String name() {
			return "opus";
		}

		@Override
		public String unavailable(int estTokens) {
			if (System.currentTimeMillis() < planProblemUntil) return planProblem;
			return limiter.blocked(0);
		}

		@Override
		public CompletableFuture<Reply> ask(String system, String user, List<String> enumValues) {
			limiter.record(0);
			return cli.ask(system, user, choiceSchema(enumValues, true).toString()).thenApply(r -> {
				if (r.ok()) failuresInRow = 0;
				else if (r.error() != null && r.error().toLowerCase().contains("limit")) {
					// Plan usage limit: stop asking for a while instead of failing every call.
					planProblem = "Claude plan limit reached; using rules for 15 min";
					planProblemUntil = System.currentTimeMillis() + 15 * 60_000;
				} else if (++failuresInRow >= 3) {
					// claude missing or logged out: each try costs seconds, so pause instead.
					failuresInRow = 0;
					planProblem = "claude -p keeps failing (" + r.error() + "); using rules for 5 min";
					planProblemUntil = System.currentTimeMillis() + 5 * 60_000;
				}
				return new Reply(r.answer(), r.tokensIn(), r.tokensOut(), r.ms(), r.error(), false);
			});
		}

		public RateLimiter limiter() {
			return limiter;
		}
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).executor(Brains.POOL).build();

	/**
	 * Any OpenAI-compatible chat API with strict JSON schema output. Groq and Cerebras both work
	 * this way on their free tiers; only the URL, key and model differ.
	 */
	public static final class OpenAiCompat implements LlmBackend {
		private final String name, url, keyName, key, model;
		private final RateLimiter limiter;

		public OpenAiCompat(String name, String url, String keyName, String key, String model, RateLimiter limiter) {
			this.name = name;
			this.url = url;
			this.keyName = keyName;
			this.key = key;
			this.model = model;
			this.limiter = limiter;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String unavailable(int estTokens) {
			if (key.isBlank()) return "no " + keyName + " in mc-autopilot.env";
			if (model.isBlank()) return "no model set for " + name + " in mc-autopilot.env";
			return limiter.blocked(estTokens);
		}

		@Override
		public CompletableFuture<Reply> ask(String system, String user, List<String> enumValues) {
			long t0 = System.currentTimeMillis();
			JsonObject body = new JsonObject();
			body.addProperty("model", model);
			JsonArray msgs = new JsonArray();
			msgs.add(msg("system", system));
			msgs.add(msg("user", user));
			body.add("messages", msgs);
			body.addProperty("temperature", 0);
			body.addProperty("max_completion_tokens", 512);
			if (name.equals("groq") && model.startsWith("openai/gpt-oss")) {
				// Reasoning tokens would eat the small output budget; keep reasoning short and hidden.
				body.addProperty("reasoning_effort", "low");
				body.addProperty("include_reasoning", false);
			}
			JsonObject js = new JsonObject();
			js.addProperty("name", "decision");
			js.addProperty("strict", true);
			js.add("schema", choiceSchema(enumValues, true));
			JsonObject rf = new JsonObject();
			rf.addProperty("type", "json_schema");
			rf.add("json_schema", js);
			body.add("response_format", rf);
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(20))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((resp, err) -> {
				long ms = System.currentTimeMillis() - t0;
				if (err != null) return new Reply(null, 0, 0, ms, "network: " + err.getClass().getSimpleName(), false);
				int code = resp.statusCode();
				if (code == 429 || code == 503) {
					// Jitter so a retry doesn't land on the same second as the limit reset.
					limiter.backoff(20_000 + (long) (Math.random() * 10_000));
					return new Reply(null, 0, 0, ms, name + " " + code + " (rate limited)", true);
				}
				if (code == 401 || code == 403) {
					limiter.backoff(10 * 60_000);
					return new Reply(null, 0, 0, ms, name + " rejected the API key (http " + code + ")", true);
				}
				if (code != 200) return new Reply(null, 0, 0, ms, name + " http " + code, false);
				try {
					JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
					int in = 0, out = 0;
					if (o.has("usage")) {
						in = o.getAsJsonObject("usage").get("prompt_tokens").getAsInt();
						out = o.getAsJsonObject("usage").get("completion_tokens").getAsInt();
					}
					limiter.record(in + out);
					String content = o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
					return new Reply(JsonParser.parseString(content).getAsJsonObject(), in, out, ms, null, false);
				} catch (Exception e) {
					limiter.record(0);
					return new Reply(null, 0, 0, ms, name + " reply unreadable", false);
				}
			});
		}

		private static JsonObject msg(String role, String content) {
			JsonObject m = new JsonObject();
			m.addProperty("role", role);
			m.addProperty("content", content);
			return m;
		}
	}

	/** Google AI Studio's generateContent with a response schema (free tier: Flash / Flash-Lite). */
	public static final class Gemini implements LlmBackend {
		private final String key, model;
		private final RateLimiter limiter;

		public Gemini(String key, String model, RateLimiter limiter) {
			this.key = key;
			this.model = model;
			this.limiter = limiter;
		}

		@Override
		public String name() {
			return "gemini";
		}

		@Override
		public String unavailable(int estTokens) {
			if (key.isBlank()) return "no GEMINI_API_KEY in mc-autopilot.env";
			if (model.isBlank()) return "no GEMINI_MODEL in mc-autopilot.env";
			return limiter.blocked(estTokens);
		}

		@Override
		public CompletableFuture<Reply> ask(String system, String user, List<String> enumValues) {
			long t0 = System.currentTimeMillis();
			JsonObject body = new JsonObject();
			JsonObject sys = new JsonObject();
			sys.add("parts", parts(system));
			body.add("systemInstruction", sys);
			JsonArray contents = new JsonArray();
			JsonObject c = new JsonObject();
			c.addProperty("role", "user");
			c.add("parts", parts(user));
			contents.add(c);
			body.add("contents", contents);
			JsonObject gen = new JsonObject();
			gen.addProperty("temperature", 0);
			// Newer Flash models may think before answering, which counts against this budget.
			gen.addProperty("maxOutputTokens", 1024);
			gen.addProperty("responseMimeType", "application/json");
			gen.add("responseSchema", choiceSchema(enumValues, false));
			body.add("generationConfig", gen);
			String url = "https://generativelanguage.googleapis.com/v1beta/models/"
					+ URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
			HttpRequest req = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(25))
					.header("x-goog-api-key", key)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).handle((resp, err) -> {
				long ms = System.currentTimeMillis() - t0;
				if (err != null) return new Reply(null, 0, 0, ms, "network: " + err.getClass().getSimpleName(), false);
				int code = resp.statusCode();
				if (code == 429 || code == 503) {
					limiter.backoff(30_000 + (long) (Math.random() * 10_000));
					return new Reply(null, 0, 0, ms, "gemini " + code + " (rate limited)", true);
				}
				if (code == 403 || (code == 400 && resp.body().contains("API_KEY_INVALID"))) {
					limiter.backoff(10 * 60_000);
					return new Reply(null, 0, 0, ms, "gemini rejected the API key (http " + code + ")", true);
				}
				if (code != 200) return new Reply(null, 0, 0, ms, "gemini http " + code, false);
				try {
					JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
					int in = 0, out = 0;
					if (o.has("usageMetadata")) {
						JsonObject u = o.getAsJsonObject("usageMetadata");
						in = u.has("promptTokenCount") ? u.get("promptTokenCount").getAsInt() : 0;
						out = u.has("candidatesTokenCount") ? u.get("candidatesTokenCount").getAsInt() : 0;
					}
					limiter.record(in + out);
					String text = o.getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("content")
							.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
					return new Reply(JsonParser.parseString(text).getAsJsonObject(), in, out, ms, null, false);
				} catch (Exception e) {
					limiter.record(0);
					return new Reply(null, 0, 0, ms, "gemini reply unreadable", false);
				}
			});
		}

		private static JsonArray parts(String text) {
			JsonArray a = new JsonArray();
			JsonObject p = new JsonObject();
			p.addProperty("text", text);
			a.add(p);
			return a;
		}
	}
}
