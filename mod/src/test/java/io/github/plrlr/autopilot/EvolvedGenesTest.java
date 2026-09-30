package io.github.plrlr.autopilot;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvolvedGenesTest {
	@Test
	void keepsOnlyValidNewDisabledSwitches() {
		String json = """
				{"genes": [
					{"name":"evolved.leave_water","kind":"BOOL","def":0,"skill":"leave_water","why":"reach dry ground","gen":86},
					{"name":"evolved.Bad","kind":"BOOL","def":0,"skill":"Bad","why":"bad name"},
					{"name":"evolved.real_gene","kind":"REAL","def":0,"skill":"real_gene","why":"wrong kind"},
					{"name":"evolved.enabled","kind":"BOOL","def":1,"skill":"enabled","why":"wrong default"},
					{"name":"evolved.mismatch","kind":"BOOL","def":0,"skill":"another","why":"wrong suffix"},
					{"name":"evolved.existing","kind":"BOOL","def":0,"skill":"existing","why":"already registered"},
					{"name":"evolved.leave_water","kind":"BOOL","def":0,"skill":"leave_water","why":"duplicate in file"}
				]}
				""";
		assertEquals(List.of(new EvolvedGenes.Entry("evolved.leave_water", "leave_water", "reach dry ground")),
				EvolvedGenes.parse(json, Set.of("evolved.existing")));
	}

	@Test
	void malformedEntriesCannotHideLaterValidOnesOrCoerceTheirTypes() {
		String json = """
				{"genes": [
					null, [], 7, {},
					{"name":"evolved.valid","kind":"BOOL","def":"0","skill":"valid","why":"string default"},
					{"name":"evolved.valid","kind":"BOOL","def":false,"skill":"valid","why":"boolean default"},
					{"name":"evolved.valid","kind":"BOOL","def":1e-999,"skill":"valid","why":"not zero"},
					{"name":"evolved.valid","kind":"BOOL","def":0,"skill":"valid","why":"usable"}
				]}
				""";
		assertEquals(List.of(new EvolvedGenes.Entry("evolved.valid", "valid", "usable")),
				EvolvedGenes.parse(json, Set.of()));
	}

	@Test
	void corruptDocumentsReturnNoGenes() {
		for (String json : List.of("not json", "null", "[]", "{}", "{\"genes\":{}}", "{\"genes\":null}"))
			assertEquals(List.of(), assertDoesNotThrow(() -> EvolvedGenes.parse(json, Set.of())));
	}

	@Test
	void shippedDeclarationsAllParse() throws Exception {
		try (var in = EvolvedGenes.class.getResourceAsStream("/evolved-genes.json")) {
			assertNotNull(in, "the gene list must ship as a classpath resource");
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			int count = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("genes").size();
			assertEquals(count, EvolvedGenes.parse(json, Set.of()).size(), "every shipped entry must be valid and unique");
		}
	}
}
