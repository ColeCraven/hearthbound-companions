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
				"callout.chest", "hurt_by_player", "fail.count_invalid", "fail.protected")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("recruit.party_full", plus(base, "count"));
		REQUIRED.put("recruit.taken", plus(base, "owner"));
		for (String key : List.of("ack.build", "done.build", "status.build")) {
			REQUIRED.put(key, plus(base, "thing"));
		}
		REQUIRED.put("fail.build_unknown", plus(base, "things"));
		REQUIRED.put("fail.build_site", plus(base, "thing", "why"));
		REQUIRED.put("fail.build_materials", plus(base, "thing", "need", "have"));
		for (String key : List.of("ack.mine", "done.mine", "fail.mine_hazard", "fail.mine_cave", "fail.mine_bottom", "fail.mine_full", "status.mine")) {
			REQUIRED.put(key, plus(base, "ores", "count", "danger", "item", "tool", "have"));
		}
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
		for (String key : List.of("suggest.eat", "suggest.shelter", "suggest.carry", "greet.friend", "greet.cold")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("banter.reply", plus(base, "other"));
		REQUIRED.put("pastime.study", plus(base, "thing"));
		REQUIRED.put("pastime.loot", plus(base, "item"));
		for (FailureReason reason : FailureReason.values()) {
			REQUIRED.putIfAbsent(reason.lineKey(), plus(base, "item", "tool", "count", "have", "mob"));
		}
		for (TalkTopic topic : TalkTopic.values()) {
			REQUIRED.put(topic.lineKey(), plus(base, "biome", "event"));
		}
		for (String biome : List.of("forest", "desert", "snow", "ocean", "jungle", "swamp", "mountain", "plains", "nether", "end")) {
			REQUIRED.put("ambient.biome." + biome, base);
		}
		REQUIRED.put("project.ack.group", plus(base, "plan"));
		REQUIRED.put("project.short", plus(base, "thing", "need", "have"));
		REQUIRED.put("project.blocked.materials", plus(base, "thing", "need", "have"));
		REQUIRED.put("project.blocked.site", plus(base, "thing"));
		for (String key : List.of("project.blocked.water", "project.blocked.seeds", "project.blocked.hoe", "project.nofurnace", "ack.home",
				"ack.home.teleport", "fail.no_home")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("project.farm.planted", plus(base, "count"));
		REQUIRED.put("project.farm.harvest", plus(base, "count", "item"));
		REQUIRED.put("project.farm.stored", plus(base, "count"));
		REQUIRED.put("fail.pvp_off", plus(base, "mob"));
		REQUIRED.put("combat.spar_over", plus(base, "mob"));
		REQUIRED.put("talk.role", plus(base, "biome", "event", "role", "skills"));
		for (String sky : List.of("talk.sky.rain", "talk.sky.night", "talk.sky.day")) {
			REQUIRED.put(sky, base);
		}
		// Getting to know the player.
		REQUIRED.put("fact.nickname", plus(base, "nick"));
		REQUIRED.put("fact.like", plus(base, "thing"));
		REQUIRED.put("fact.dislike", plus(base, "thing"));
		REQUIRED.put("talk.about_me", plus(base, "biome", "event", "facts"));
		for (String key : List.of("fact.forget", "talk.about_me.none", "talk.bond.close", "talk.bond.cold", "talk.bond.stranger", "talk.answer",
				"talk.answer.good", "talk.answer.bad", "ask.nickname", "ask.nickname.skip", "ask.skip", "ask.like", "ask.dislike", "ask.how", "ask.open",
				"gesture.crouch", "react.diamonds", "react.sleep", "react.wake", "react.player_died", "react.respawn")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("recall.liked", plus(base, "thing"));
		REQUIRED.put("recall.liked_find", plus(base, "thing", "item"));
		REQUIRED.put("recall.disliked", plus(base, "thing", "mob"));
		REQUIRED.put("react.level", plus(base, "level"));
		REQUIRED.put("react.advancement", plus(base, "advancement"));
		REQUIRED.put("react.challenge", plus(base, "advancement"));
		REQUIRED.put("gift.food", plus(base, "item"));
		REQUIRED.put("gift.flower", plus(base, "item"));
		// Task questions and conversation relevance (0.4).
		REQUIRED.put("ask.join_first", base);
		REQUIRED.put("ask.build_what", plus(base, "things"));
		REQUIRED.put("talk.mention.mob", plus(base, "thing"));
		REQUIRED.put("talk.mention.thing", plus(base, "thing"));
		for (String topic : List.of("weather", "road", "food", "teasing")) {
			for (String step : List.of("open", "reply", "close")) {
				REQUIRED.put("exchange." + topic + "." + step, plus(base, "other", "thing"));
			}
		}
		REQUIRED.put("routine.gather", plus(base, "item"));
		REQUIRED.put("routine.farm", plus(base, "item"));
		REQUIRED.put("routine.hunt", plus(base, "mob"));
		REQUIRED.put("routine.torch", base);
		REQUIRED.put("fail.camp_campfire", base);
		REQUIRED.put("role.strong", plus(base, "task"));
		REQUIRED.put("role.weak", plus(base, "task"));
		// Daily life at home (0.6).
		for (String key : List.of("life.wake", "life.work", "life.meal", "life.leisure", "life.social", "life.sleep", "life.shelter", "life.fire",
				"life.bed_taken", "life.self.shelter", "life.self.farm", "life.self.tools", "life.favour.cant_pay")) {
			REQUIRED.put(key, base);
		}
		REQUIRED.put("life.need", plus(base, "thing"));
		REQUIRED.put("life.stored", plus(base, "count"));
		REQUIRED.put("life.project.progress", plus(base, "count"));
		REQUIRED.put("life.project.done", plus(base, "count"));
		REQUIRED.put("life.favour.ask", plus(base, "count", "item", "reward"));
		REQUIRED.put("life.favour.partial", plus(base, "count", "item"));
		REQUIRED.put("life.favour.thanks", plus(base, "reward"));
		REQUIRED.put("status.life", plus(base, "thing", "favour", "mood"));
		REQUIRED.put("status.favour", plus(base, "thing", "favour", "mood"));
		for (String topic : List.of("meal", "story", "festival", "friends", "rivals", "people")) {
			for (String step : List.of("open", "reply", "close")) {
				REQUIRED.put("exchange." + topic + "." + step, plus(base, "other", "thing"));
			}
		}
		// Plain requests picked up as a team job (0.6).
		REQUIRED.put("team.plan", plus(base, "plan", "thing"));
		REQUIRED.put("team.progress", plus(base, "thing", "count"));
		REQUIRED.put("team.done", plus(base, "thing"));
		// Copying a saved build, and getting what it needs (0.7).
		REQUIRED.put("blueprint.unknown", plus(base, "thing", "names"));
		REQUIRED.put("blueprint.done_partly", plus(base, "thing", "count"));
		for (String key : List.of("supply.gather", "supply.craft", "supply.smelt")) {
			REQUIRED.put(key, plus(base, "count", "item", "thing"));
		}
		REQUIRED.put("supply.blocked", plus(base, "thing", "missing"));
		for (String key : List.of("gear.swapped", "gear.worn", "gear.repaired")) {
			REQUIRED.put(key, plus(base, "item"));
		}
		for (String key : List.of("craft.no_table", "smelt.no_furnace", "smelt.no_fuel", "move.pillar", "move.bridge")) {
			REQUIRED.put(key, base);
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

	/** Pastimes each archetype can choose by itself (see Pastimes.pick) must have their own lines. */
	@Test
	void everyChosenPastimeHasLines() {
		Map<Archetype, List<String>> pastimes = Map.of(
				Archetype.SELLSWORD, List.of("pastime.patrol", "pastime.practice"),
				Archetype.NATURALIST, List.of("pastime.flower"),
				Archetype.ROGUE, List.of("pastime.patrol"),
				Archetype.GUARDIAN, List.of("pastime.patrol"),
				Archetype.FARMHAND, List.of("pastime.pet", "pastime.flower"),
				Archetype.MYSTIC, List.of("pastime.stargaze", "pastime.patrol"));
		pastimes.forEach((archetype, keys) -> keys.forEach(key ->
				assertFalse(LineBank.of(archetype).get(key).isEmpty(), archetype + " needs lines for " + key)));
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

	@Test
	void placeholdersThatStartASentenceAreCapitalised() {
		assertEquals("A shelter? Sure.", LineSelector.fill("{thing}? Sure.", Map.of("thing", "a shelter")));
		assertEquals("Not here. The ground's too uneven.", LineSelector.fill("Not here. {why}.", Map.of("why", "the ground's too uneven")));
		assertEquals("I'll build a wall.", LineSelector.fill("I'll build {thing}.", Map.of("thing", "a wall")));
	}

	@Test
	void articlesAgreeWithWhatFillsThem() {
		assertEquals("Look, an apple!", LineSelector.fill("Look, a {item}!", Map.of("item", "apple")));
		assertEquals("Watch out, an enderman!", LineSelector.fill("Watch out, a {mob}!", Map.of("mob", "enderman")));
		assertEquals("Some sweet berries? Thank you!", LineSelector.fill("A {item}? Thank you!", Map.of("item", "sweet berries")));
		assertEquals("I love these cookies.", LineSelector.fill("I love this {item}.", Map.of("item", "cookies")));
		assertEquals("It's a cactus.", LineSelector.fill("It's a {item}.", Map.of("item", "cactus")));
		assertEquals("Ha, a zombie.", LineSelector.fill("Ha, a {mob}.", Map.of("mob", "zombie")));
	}

	/** Per-character files only add variety: whatever they say must use placeholders the game supplies. */
	@org.junit.jupiter.api.Test
	void castLinesUseKnownPlaceholders() {
		for (io.github.tekless.hearthbound.core.persona.CastMember member : io.github.tekless.hearthbound.core.persona.CastMember.values()) {
			LineBank bank = LineBank.of(member.archetype(), member.id());
			for (Map.Entry<String, Set<String>> required : REQUIRED.entrySet()) {
				List<String> lines = bank.get(required.getKey());
				assertFalse(lines.isEmpty(), member + " has no lines for " + required.getKey());
				for (String line : lines) {
					for (String placeholder : LineSelector.placeholders(line)) {
						assertTrue(required.getValue().contains(placeholder), member + " " + required.getKey() + " uses {" + placeholder + "}: " + line);
					}
				}
			}
		}
	}
}
