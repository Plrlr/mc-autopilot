package io.github.plrlr.autopilot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Bundled gene declarations, parsed without loading any Minecraft classes. */
public final class EvolvedGenes {
	private EvolvedGenes() {}

	// The mod's logger by name keeps the parser usable without initializing the client entry point.
	private static final Logger LOGGER = LoggerFactory.getLogger("autopilot");
	private static final Pattern NAME = Pattern.compile("^evolved\\.[a-z][a-z0-9_]{2,39}$");

	record Entry(String name, String skill, String why) {}

	public static boolean validName(String name) { return name != null && NAME.matcher(name).matches(); }

	static List<Entry> load(Set<String> existing) {
		try (var in = EvolvedGenes.class.getResourceAsStream("/evolved-genes.json")) {
			if (in == null) {
				LOGGER.warn("Missing evolved-genes.json; no evolved genes loaded");
				return List.of();
			}
			return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), existing);
		} catch (IOException | RuntimeException ex) {
			LOGGER.warn("Could not read evolved-genes.json; no evolved genes loaded", ex);
			return List.of();
		}
	}

	/** Bad entries do not prevent later valid entries from registering; duplicates never replace genes. */
	static List<Entry> parse(String json, Set<String> existing) {
		List<Entry> out = new ArrayList<>();
		try {
			var genes = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("genes");
			if (genes == null) throw new IllegalArgumentException("missing genes array");
			Set<String> names = new HashSet<>(existing);
			int index = 0;
			for (JsonElement row : genes) {
				try {
					JsonObject e = row.getAsJsonObject();
					String name = string(e, "name"), skill = string(e, "skill");
					JsonElement def = e.get("def");
					if (!validName(name) || !name.substring(8).equals(skill) || !string(e, "kind").equals("BOOL")
							|| def == null || !def.isJsonPrimitive() || !def.getAsJsonPrimitive().isNumber()
							|| def.getAsBigDecimal().signum() != 0)
						throw new IllegalArgumentException("expected an evolved BOOL gene defaulting to zero with a matching skill");
					String why = string(e, "why");
					if (!names.add(name)) throw new IllegalArgumentException("duplicate gene");
					out.add(new Entry(name, skill, why));
				} catch (RuntimeException ex) {
					LOGGER.warn("Skipping evolved gene entry {}: {}", index, ex.getMessage());
				}
				index++;
			}
		} catch (RuntimeException ex) {
			LOGGER.warn("Invalid evolved-genes.json; no evolved genes loaded", ex);
			return List.of();
		}
		return List.copyOf(out);
	}

	private static String string(JsonObject e, String key) {
		JsonElement v = e.get(key);
		if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("expected string " + key);
		return v.getAsString();
	}
}
