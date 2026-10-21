package io.github.tekless.hearthbound.server.polymer;

import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.registry.ModEntities;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything that talks to Polymer. Only loaded when Polymer is installed (see
 * {@code VanillaClients}), so the mod still runs fine without it.
 */
public final class PolymerBridge {
	private static boolean resourcePacks;

	private PolymerBridge() {
	}

	public static void init() {
		// Companions keep their own entity type on the server; players without the mod get a mannequin.
		PolymerEntityUtils.registerOverlay(ModEntities.COMPANION, CompanionMannequin::new);

		String skins = Hearthbound.config().server.skins == null ? "auto" : Hearthbound.config().server.skins.toLowerCase(Locale.ROOT);
		resourcePacks = FabricLoader.getInstance().isModLoaded("polymer-resource-pack")
				&& (skins.equals("auto") || skins.equals("resource_pack"));
		if (resourcePacks) {
			// Puts the cast textures (and English text) into Polymer's server resource pack. Players only get
			// the pack if the server owner turns on Polymer's pack hosting; otherwise nothing is sent.
			PolymerResourcePackUtils.addModAssets(Hearthbound.MOD_ID);
		}
	}

	/** Does this player have the server resource pack loaded (so cast textures can be shown)? */
	static boolean hasResourcePack(ServerPlayer player) {
		if (!resourcePacks || player.connection == null) {
			return false;
		}
		try {
			return PolymerResourcePackUtils.hasMainPack(player.connection);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
