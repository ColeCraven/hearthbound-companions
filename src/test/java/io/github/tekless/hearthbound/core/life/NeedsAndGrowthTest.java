package io.github.tekless.hearthbound.core.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Hunger, rest and company; crops growing while nobody watches; how neighbours get on. */
class NeedsAndGrowthTest {
	@Test
	void hungerBuildsAndAMealPutsItRight() {
		Needs needs = new Needs();
		for (int i = 0; i < 200; i++) {
			needs.pass(Activity.WORK, 3, true);
		}
		assertEquals(Needs.MAX, needs.hunger(), "ten minutes of work without food");
		assertEquals("hungry", needs.pressing());
		needs.eat();
		assertEquals(0, needs.hunger());
	}

	@Test
	void sleepRestsAndCompanyCheers() {
		Needs needs = new Needs(0, 90, 90);
		for (int i = 0; i < 100; i++) {
			needs.pass(Activity.SLEEP, 3, false);
		}
		assertEquals(0, needs.tiredness(), "a night's sleep");
		assertEquals(90, needs.loneliness(), "nobody gets lonely asleep");
		needs.pass(Activity.SOCIAL, 3, true);
		assertTrue(needs.loneliness() < 90, "company helps");
		assertTrue(new Needs(0, 0, 80).chattiness() > 1.0, "lonely people talk more");
		assertTrue(new Needs(0, 90, 0).chattiness() < 1.0, "tired people talk less");
		assertNull(new Needs().pressing());
		assertEquals("cheerful", new Needs().mood());
	}

	@Test
	void cropsGrowAboutAStageAQuarterDayWhenWatered() {
		Random random = new Random(1);
		int total = 0;
		for (int i = 0; i < 100; i++) {
			total += CropGrowth.simulate(0, 7, 24000, true, 0, random).age();
		}
		assertTrue(total > 300 && total < 500, "about four stages a day on wet ground: " + total);
		assertEquals(7, CropGrowth.simulate(0, 7, 7 * 24000, false, 0, random).age(), "with nobody to harvest, ripe and waiting");
	}

	@Test
	void harvestsOnlyHappenWhenAllowedAndAreCapped() {
		Random random = new Random(2);
		CropGrowth.Result none = CropGrowth.simulate(7, 7, 24000, true, 0, random);
		assertEquals(0, none.harvested());
		assertEquals(7, none.age());
		CropGrowth.Result some = CropGrowth.simulate(7, 7, 3 * 24000, true, 2, random);
		assertTrue(some.harvested() >= 1 && some.harvested() <= 2, "harvested, but no more than allowed");
		assertTrue(some.age() <= 7);
		assertEquals(0, CropGrowth.simulate(3, 7, 0, true, 5, random).harvested(), "no time, no harvest");
	}

	@Test
	void bonusDropsAreZeroToThree() {
		Random random = new Random(3);
		for (int i = 0; i < 200; i++) {
			int drops = CropGrowth.bonusDrops(random);
			assertTrue(drops >= 0 && drops <= 3);
		}
	}

	@Test
	void mostNeighboursWarmUpAndSomeClash() {
		assertTrue(Bonds.clash("rogue", "guardian"));
		assertTrue(Bonds.clash("guardian", "rogue"));
		assertTrue(!Bonds.clash("farmhand", "naturalist"));
		Random random = new Random(4);
		assertEquals(1, Bonds.afterTimeTogether("farmhand", "naturalist", random));
		int drift = 0;
		for (int i = 0; i < 1000; i++) {
			drift += Bonds.afterTimeTogether("rogue", "guardian", random);
		}
		assertTrue(drift < 0, "a clashing pair drifts apart over time: " + drift);
		assertEquals("friends", Bonds.describe(Bonds.FRIENDS));
		assertEquals("rivals", Bonds.describe(Bonds.RIVALS));
	}
}
