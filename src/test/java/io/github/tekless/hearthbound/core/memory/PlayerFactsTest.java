package io.github.tekless.hearthbound.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a player tells a companion, and how it's matched against the world later. */
class PlayerFactsTest {
	@Test
	void likesMatchThingsInTheWorldIgnoringPluralsAndArticles() {
		assertTrue(PlayerFacts.names("diamonds", "diamond ore"));
		assertTrue(PlayerFacts.names("the ocean", "deep ocean"));
		assertTrue(PlayerFacts.names("cherry blossoms", "cherry grove"));
		assertTrue(PlayerFacts.names("spiders", "cave spider"));
		assertTrue(PlayerFacts.names("berries", "sweet berry bush"));
		assertFalse(PlayerFacts.names("the ocean", "plains"));
		assertFalse(PlayerFacts.names("a lot of stuff", "stuff"), "filler words never match");
	}

	@Test
	void changingYourMindMovesAThingBetweenLikesAndDislikes() {
		PlayerFacts facts = new PlayerFacts();
		facts.dislike("rain");
		facts.like("Rain");
		assertEquals(List.of("Rain"), facts.likes());
		assertTrue(facts.dislikes().isEmpty());
	}

	@Test
	void keepsOnlyTheMostRecentFew() {
		PlayerFacts facts = new PlayerFacts();
		for (int i = 0; i < PlayerFacts.MAX_EACH + 2; i++) {
			facts.like("thing" + i);
		}
		assertEquals(PlayerFacts.MAX_EACH, facts.likes().size());
		assertEquals("thing" + (PlayerFacts.MAX_EACH + 1), facts.likes().get(PlayerFacts.MAX_EACH - 1));
		assertFalse(facts.likes().contains("thing0"));
	}

	@Test
	void summaryReadsLikeASentence() {
		PlayerFacts facts = new PlayerFacts();
		facts.like("diamonds");
		assertEquals("you like diamonds", facts.summary());
		facts.setNickname("Captain");
		facts.like("the ocean");
		facts.dislike("spiders");
		assertEquals("you go by Captain, you like diamonds and the ocean, and you can't stand spiders", facts.summary());
		facts.like("cake");
		assertTrue(facts.summary().contains("you like diamonds, the ocean and cake"));
	}

	@Test
	void factsAreKeptPerPlayerAndSurviveAChangeOfConversation() {
		CompanionMemory memory = new CompanionMemory();
		memory.talkingWith("alice");
		memory.factsAbout("alice").setNickname("Al");
		memory.talkingWith("bob");
		assertEquals(Optional.of("Al"), memory.knownFacts("alice").flatMap(PlayerFacts::nickname));
		assertTrue(memory.knownFacts("bob").isEmpty(), "bob hasn't said anything");
		memory.factsAbout("bob");
		assertTrue(memory.knownFacts("bob").isEmpty(), "an empty record counts as nothing known");
	}

	@Test
	void eventsAreStoredAsClausesEvenFromOldSaves() {
		CompanionMemory memory = new CompanionMemory();
		memory.addEvent("joined {player} on the road");
		memory.addEvent("{player} gave me a cake");
		memory.addEvent("I built a shelter with {player}");
		assertEquals(List.of("I joined {player} on the road", "{player} gave me a cake", "I built a shelter with {player}"), memory.events());
	}
}
