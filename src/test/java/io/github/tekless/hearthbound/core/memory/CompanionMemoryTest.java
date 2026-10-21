package io.github.tekless.hearthbound.core.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** On a shared server, one player's conversation must never leak into another's. */
class CompanionMemoryTest {
	@Test
	void switchingPartnerForgetsTheOldConversationButNotSharedEvents() {
		CompanionMemory memory = new CompanionMemory();
		memory.talkingWith("alice");
		memory.addPlayerLine("my secret base is at the old windmill");
		memory.addCompanionLine("I'll keep that to myself.");
		memory.addEvent("fought a creeper with {player}");
		memory.changeAffinity(5);

		assertTrue(memory.talkingWith("bob"), "the old conversation is dropped");
		assertTrue(memory.lines().isEmpty(), "bob never sees alice's lines");
		assertEquals(1, memory.events().size(), "shared events stay");
		assertEquals(5, memory.affinity(), "affinity stays");
	}

	@Test
	void sameSpeakerKeepsTheConversation() {
		CompanionMemory memory = new CompanionMemory();
		memory.talkingWith("alice");
		memory.addPlayerLine("hello");
		assertFalse(memory.talkingWith("alice"));
		assertEquals(1, memory.lines().size());
	}

	@Test
	void anOldSaveWithoutAPartnerKeepsItsLinesForTheFirstSpeaker() {
		CompanionMemory memory = new CompanionMemory();
		memory.addPlayerLine("from before the update");
		assertFalse(memory.talkingWith("alice"));
		assertEquals(1, memory.lines().size());
	}
}
