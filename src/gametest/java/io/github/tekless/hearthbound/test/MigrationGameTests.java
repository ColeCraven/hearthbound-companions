package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.core.persona.CastMember;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Companions saved before the 0.4 renames load as the new characters. */
public class MigrationGameTests {
	private static CompanionEntity saveAndReload(GameTestHelper helper, CompanionEntity original) {
		ServerLevel level = helper.getLevel();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		original.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		CompanionEntity copy = ModEntities.COMPANION.create(level, EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		return copy;
	}

	private static CompanionEntity saved(GameTestHelper helper, Archetype oldArchetype, String oldName, CastMember skin) {
		CompanionEntity companion = helper.spawn(ModEntities.COMPANION, new Vec3(2.5, 1, 2.5));
		companion.initIdentity(oldArchetype, oldName, skin.skinVariant());
		return companion;
	}

	@GameTest(maxTicks = 20)
	public void oldCastNamesLoadAsTheNewCharacters(GameTestHelper helper) {
		CompanionEntity freya = saveAndReload(helper, saved(helper, Archetype.SELLSWORD, "Freya", CastMember.DASHA));
		helper.assertValueEqual(freya.companionName(), "Dasha", "renamed");
		helper.assertValueEqual(freya.archetype(), Archetype.GUARDIAN, "new voice");
		helper.assertValueEqual(freya.role(), Role.GUARDIAN, "role");

		CompanionEntity conoby = saveAndReload(helper, saved(helper, Archetype.GUARDIAN, "Conoby", CastMember.COLE));
		helper.assertValueEqual(conoby.companionName(), "Cole", "renamed");
		helper.assertValueEqual(conoby.archetype(), Archetype.ROGUE, "new voice");

		CompanionEntity rosie = saveAndReload(helper, saved(helper, Archetype.FARMHAND, "Rosie", CastMember.HONEY));
		helper.assertValueEqual(rosie.companionName(), "Honey", "renamed");
		helper.assertValueEqual(rosie.role(), Role.FARMER, "role");

		CompanionEntity casper = saveAndReload(helper, saved(helper, Archetype.MYSTIC, "Casper", CastMember.LUKE));
		helper.assertValueEqual(casper.companionName(), "Luke", "renamed");
		helper.assertValueEqual(casper.role(), Role.BUILDER, "role");
		helper.assertTrue(casper.castMember().isPresent(), "recognised as a cast member again");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void aCustomNameOnACastSkinIsLeftAlone(GameTestHelper helper) {
		CompanionEntity renamed = saveAndReload(helper, saved(helper, Archetype.NATURALIST, "Seren", CastMember.DASHA));
		helper.assertValueEqual(renamed.companionName(), "Seren", "name kept");
		helper.assertValueEqual(renamed.archetype(), Archetype.NATURALIST, "voice kept");
		helper.succeed();
	}
}
