package io.github.tekless.hearthbound.registry;

import io.github.tekless.hearthbound.Hearthbound;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;

public final class ModItems {
	public static final ResourceKey<Item> COMPANION_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, Hearthbound.id("companion_spawn_egg"));
	/** Null on servers that let players without the mod join: a vanilla client wouldn't know the item. */
	public static @Nullable Item COMPANION_SPAWN_EGG;

	private ModItems() {
	}

	/** Registers the items; skipped entirely when the server has to stay vanilla-compatible. */
	public static void init(boolean vanillaClients) {
		if (vanillaClients) {
			return;
		}
		Item egg = Registry.register(BuiltInRegistries.ITEM, COMPANION_SPAWN_EGG_KEY,
				new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.COMPANION).setId(COMPANION_SPAWN_EGG_KEY)));
		COMPANION_SPAWN_EGG = egg;
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(egg));
	}
}
