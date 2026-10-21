package io.github.tekless.hearthbound.core.intent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectParserTest {
	private static final String BUILDERS = "please help expand this village. Everyone should have their own house, unless you all want to share, "
			+ "or just some of you do. Work with the collectors, gather materials and start building.";
	private static final String FARMERS = "build a farm and start harvesting food for us to eat. Connect with the builders and maybe build a "
			+ "building for us to store and cook food.";

	@Test
	void theBuildersMessageIsHousesWithCollectorsSupplying() {
		ProjectParser.Plan plan = ProjectParser.parse(BUILDERS).orElseThrow();
		assertTrue(plan.houses());
		assertTrue(plan.collectors());
		assertTrue(plan.share());
		assertFalse(plan.farm());
		assertFalse(plan.storehouse());
	}

	@Test
	void theFarmersMessageIsAFarmAndAStorehouse() {
		ProjectParser.Plan plan = ProjectParser.parse(FARMERS).orElseThrow();
		assertTrue(plan.farm());
		assertTrue(plan.storehouse());
		assertTrue(plan.builders());
		assertFalse(plan.houses());
	}

	@Test
	void shortRequests() {
		assertTrue(ProjectParser.parse("build houses").orElseThrow().houses());
		assertTrue(ProjectParser.parse("make a kitchen").orElseThrow().storehouse());
		assertTrue(ProjectParser.parse("start farming, plant some wheat").orElseThrow().farm());
	}

	@Test
	void ordinaryOrdersAreNotProjects() {
		assertTrue(ProjectParser.parse("follow me").isEmpty());
		assertTrue(ProjectParser.parse("gather some logs").isEmpty());
		assertTrue(ProjectParser.parse("build a shelter").isEmpty());
		assertTrue(ProjectParser.parse("work with the collectors").isEmpty());
	}
}
