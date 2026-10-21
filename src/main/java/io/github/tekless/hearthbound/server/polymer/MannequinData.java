package io.github.tekless.hearthbound.server.polymer;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;

/**
 * The synced-data slots of a vanilla mannequin that we fill in for players without the mod.
 * Extending Mannequin is only a way to reach its protected field ids; this class is never created.
 */
final class MannequinData extends Mannequin {
	static final EntityDataAccessor<ResolvableProfile> PROFILE = DATA_PROFILE;
	/** Declared right after the profile and the "immovable" flag in Mannequin. */
	static final EntityDataAccessor<Optional<Component>> DESCRIPTION =
			new EntityDataAccessor<>(DATA_PROFILE.id() + 2, EntityDataSerializers.OPTIONAL_COMPONENT);
	static final EntityDataAccessor<Pose> POSE = DATA_POSE;

	private MannequinData(EntityType<Mannequin> type, Level level) {
		super(type, level);
	}
}
