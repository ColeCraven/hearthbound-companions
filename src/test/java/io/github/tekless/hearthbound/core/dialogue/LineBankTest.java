package io.github.tekless.hearthbound.core.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tekless.hearthbound.core.intent.TalkTopic;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.task.FailureReason;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Checks that every personality can answer every situation the game asks for. */
class LineBankTest {
	/** Every line key game code uses, with the placeholders the code provides for it. */
	static final Map<String, Set<String>> REQUIRED = new HashMap<>();

	static {
		Set<String> base = Set.of("name", "player", "max");
		for (String key : List.of("greet.stranger", "greet.owner", "recruit.accept", "recruit.already", "dismiss.accept",
				"dismiss.not_recruited", "not_recruited", "ack.follow", "ack.follow.already", "ack.wait", "ack.wait.already",
				"ack.cancel.nothing", "ack.come", "ack.give.nothing", "status.idle", "status.follow", "status.wait", "status.downed",
				"fail.downed", "fail.no_target", "fail.stuck", "unclear.generic", "unclear.gather_what", "combat.low_health",
				"combat.retreat", "combat.player_low_health", "combat.creeper", "downed", "revived", "move.caught_up",
				"ambient.night", "ambient.dawn", "ambient.rain", "ambient.thunder", "ambient.cave", "ambient.idle", "callout.lava",
				"callout.chest", "hurt_by_player", "fail.count_invalid")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("recruit.party_full", plus(base, "count"));
		REQUIRED.put("ack.gather", plus(base, "count", "item"));
		REQUIRED.put("ack.cancel", plus(base, "task"));
		REQUIRED.put("ack.attack", plus(base, "mob"));
		REQUIRED.put("ack.give", plus(base, "count", "item"));
		REQUIRED.put("status.gather", plus(base, "item", "have", "count"));
		REQUIRED.put("progress.gather", plus(base, "item", "have", "count"));
		REQUIRED.put("done.gather", plus(base, "item", "have", "count"));
		REQUIRED.put("done.delivered", plus(base, "item", "count"));
		REQUIRED.put("partial.gather", plus(base, "item", "have", "count"));
		REQUIRED.put("fail.unknown_item", plus(base, "item"));
		REQUIRED.put("fail.not_gatherable", plus(base, "item"));
		REQUIRED.put("fail.give_missing", plus(base, "item"));
		REQUIRED.put("fail.unsupported", plus(base, "action"));
		REQUIRED.put("fail.refuse_target", plus(base, "mob"));
		REQUIRED.put("combat.engage", plus(base, "mob"));
		REQUIRED.put("combat.kill", plus(base, "mob"));
		REQUIRED.put("callout.hostile_behind", plus(base, "mob"));
		REQUIRED.put("callout.ore", plus(base, "item"));
		for (FailureReason reason : FailureReason.values()) {
			REQUIRED.putIfAbsent(reason.lineKey(), plus(base, "item", "tool", "count", "have", "mob"));
		}
		for (TalkTopic topic : TalkTopic.values()) {
			REQUIRED.put(topic.lineKey(), plus(base, "biome", "event"));
		}
		for (String biome : List.of("forest", "desert", "snow", "ocean", "jungle", "swamp", "mountain", "plains", "nether", "end")) {
			REQUIRED.put("ambient.biome." + biome, base);
		}
	}

	private static Set<String> plus(Set<String> base, String... extra) {
		Set<String> all = new HashSet<>(base);
		all.addAll(List.of(extra));
		return all;
	}

	@ParameterizedTest
	@EnumSource(Archetype.class)
	void everySituationHasLinesWithKnownPlaceholders(Archetype archetype) {
		LineBank bank = LineBank.of(archetype);
		for (Map.Entry<String, Set<String>> required : REQUIRED.entrySet()) {
			List<String> lines = bank.get(required.getKey());
			assertFalse(lines.isEmpty(), archetype + " has no lines for " + required.getKey());
			for (String line : lines) {
				for (String placeholder : LineSelector.placeholders(line)) {
					assertTrue(required.getValue().contains(placeholder),
							archetype + " line for " + required.getKey() + " uses {" + placeholder + "} which the game doesn't supply: " + line);
				}
			}
		}
	}

	@ParameterizedTest
	@EnumSource(Archetype.class)
	void frequentSituationsHaveSeveralVariants(Archetype archetype) {
		LineBank bank = LineBank.of(archetype);
		for (String key : List.of("ack.follow", "ack.wait", "ack.gather", "unclear.generic", "talk.generic", "ambient.idle", "greet.owner")) {
			assertTrue(bank.get(key).size() >= 3, archetype + " needs at least 3 variants for " + key);
		}
	}

	@Test
	void archetypesSoundDifferent() {
		Set<String> seen = new HashSet<>();
		for (Archetype archetype : Archetype.values()) {
			for (String line : LineBank.of(archetype).get("ack.follow")) {
				assertTrue(seen.add(line), "Two archetypes share the line: " + line);
			}
		}
	}

	@Test
	void selectorCyclesBeforeRepeating() {
		LineBank bank = LineBank.of(Archetype.SELLSWORD);
		List<String> options = bank.get("ack.follow");
		LineSelector selector = new LineSelector(new Random(42));
		Set<String> firstRound = new HashSet<>();
		for (int i = 0; i < options.size(); i++) {
			firstRound.add(selector.pick(bank, "ack.follow", Map.of()));
		}
		assertEquals(options.size(), firstRound.size(), "every variant used once before any repeat");
		String previous = selector.pick(bank, "ack.follow", Map.of());
		for (int i = 0; i < 50; i++) {
			String next = selector.pick(bank, "ack.follow", Map.of());
			assertNotEquals(previous, next, "never the same line twice in a row");
			previous = next;
		}
	}

	@Test
	void fillsPlaceholdersAndNeverShowsBraces() {
		assertEquals("16 oak logs. On it.", LineSelector.fill("{count} {item}. On it.", Map.of("count", "16", "item", "oak logs")));
		assertFalse(LineSelector.fill("Hello {player}, {unknown}!", Map.of()).contains("{"));
	}
}
