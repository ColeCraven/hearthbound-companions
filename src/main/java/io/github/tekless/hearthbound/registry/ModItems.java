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

public final class ModItems {
	public static final ResourceKey<Item> COMPANION_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, Hearthbound.id("companion_spawn_egg"));
	public static final Item COMPANION_SPAWN_EGG = Registry.register(
			BuiltInRegistries.ITEM,
			COMPANION_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.COMPANION).setId(COMPANION_SPAWN_EGG_KEY)));

	private ModItems() {
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(COMPANION_SPAWN_EGG));
	}
}
