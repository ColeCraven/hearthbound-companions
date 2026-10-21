package io.github.tekless.hearthbound.registry;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final ResourceKey<EntityType<?>> COMPANION_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Hearthbound.id("companion"));

	/** Player-sized (0.6 x 1.8, eyes at 1.62) so companions fit wherever the player fits. */
	public static final EntityType<CompanionEntity> COMPANION = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			COMPANION_KEY,
			EntityType.Builder.of(CompanionEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(COMPANION_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(COMPANION, CompanionEntity.createAttributes());
	}
}
