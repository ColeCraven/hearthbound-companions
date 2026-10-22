package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * Settings for the GameTest server. The tests written before daily life existed expect companions to stay put at
 * home, so daily life is off for the whole run and the life tests switch it on companion by companion. It is done
 * when the server starts, after the mod has loaded its config, so the order mods start up in doesn't matter.
 */
public class TestSetup implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (server instanceof GameTestServer) {
				Hearthbound.config().dailyLife = false;
			}
		});
	}
}
