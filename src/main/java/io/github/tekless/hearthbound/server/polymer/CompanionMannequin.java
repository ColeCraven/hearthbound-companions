package io.github.tekless.hearthbound.server.polymer;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.dialogue.Texts;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.server.SkinProfiles;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;

/**
 * How a companion looks to a player without the mod: a vanilla mannequin wearing the companion's
 * skin, with its name above and a short status line under it ("Sellsword · free to recruit",
 * "travelling with Tekless"). Polymer drops our own synced fields from the packets, since a
 * mannequin doesn't have them, and keeps everything a mannequin understands (name, health,
 * equipment, movement).
 */
final class CompanionMannequin implements PolymerEntity {
	private final CompanionEntity companion;

	CompanionMannequin(CompanionEntity companion) {
		this.companion = companion;
	}

	@Override
	public EntityType<?> getPolymerEntityType(PacketContext context) {
		return EntityTypes.MANNEQUIN;
	}

	@Override
	public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
		if (!initial && data.stream().noneMatch(value -> CompanionEntity.affectsLook(value.id()))) {
			return; // nothing about the look changed (e.g. just health); don't resend the skin
		}
		boolean hasPack = PolymerBridge.hasResourcePack(player);
		data.add(SynchedEntityData.DataValue.create(MannequinData.PROFILE, SkinProfiles.forViewer(companion.skinVariant(), hasPack)));
		data.add(SynchedEntityData.DataValue.create(MannequinData.DESCRIPTION, Optional.of(description())));
		data.add(SynchedEntityData.DataValue.create(MannequinData.POSE, companion.isDowned() || companion.getPose() == Pose.CROUCHING ? Pose.CROUCHING : Pose.STANDING));
	}

	private Component description() {
		if (companion.isDowned()) {
			return Texts.tr("label.hearthbound.downed").withStyle(ChatFormatting.RED);
		}
		// Kept short and grey so it sits quietly under the name.
		String owner = companion.ownerName();
		if (owner != null) {
			return Texts.tr("label.hearthbound.with", owner).withStyle(ChatFormatting.GRAY);
		}
		return Texts.tr("label.hearthbound.free", companion.role().label()).withStyle(ChatFormatting.GRAY);
	}
}
