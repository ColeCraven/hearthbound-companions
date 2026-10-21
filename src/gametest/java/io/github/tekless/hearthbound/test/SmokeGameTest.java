package io.github.tekless.hearthbound.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class SmokeGameTest {
	@GameTest
	public void modLoads(GameTestHelper helper) {
		helper.succeed();
	}
}
