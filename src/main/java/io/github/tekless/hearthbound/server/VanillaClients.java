package io.github.tekless.hearthbound.server;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.config.HearthboundConfig;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Decides whether this server should look after players who don't have the mod. When it does,
 * companions are sent to them as vanilla mannequins (through Polymer) and the mod adds nothing
 * they would need to download. Nothing here touches Polymer's classes, so it is safe to call
 * when Polymer isn't installed.
 */
public final class VanillaClients {
	private static boolean active;

	private VanillaClients() {
	}

	/** True once the Polymer bridge is running. */
	public static boolean active() {
		return active;
	}

	public static boolean polymerInstalled() {
		return FabricLoader.getInstance().isModLoaded("polymer-core");
	}

	/** Called from the mod initialiser, before anything is registered. Returns whether the bridge is on. */
	public static boolean decide(HearthboundConfig config) {
		String mode = config.server.vanillaClients == null ? "auto" : config.server.vanillaClients.trim().toLowerCase(Locale.ROOT);
		boolean dedicated = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
		boolean want = switch (mode) {
			case "on", "true" -> true;
			case "off", "false" -> false;
			default -> dedicated;
		};
		if (want && !polymerInstalled()) {
			if (!mode.equals("auto") || dedicated) {
				Hearthbound.LOGGER.warn("Players without Hearthbound can't see companions on this server: install Polymer"
						+ " (polymer-bundled for 26.3) or set server.vanillaClients to \"off\" to silence this.");
			}
			want = false;
		}
		active = want;
		return want;
	}

	/** Hooks companions into Polymer. Only call when {@link #decide} returned true. */
	public static void start() {
		io.github.tekless.hearthbound.server.polymer.PolymerBridge.init();
		Hearthbound.LOGGER.info("Players without the mod can join: companions appear to them as mannequins (skins: {})",
				Hearthbound.config().server.skins);
	}
}
