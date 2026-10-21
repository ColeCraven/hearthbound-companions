package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.life.CropGrowth;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.entity.order.Routines;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Time that passed while a home wasn't loaded (everyone away, the chunks asleep), worked out honestly when someone
 * comes back. Crops in the farm grow as they would have; if a farmer lives there and there's a storage chest with
 * room, ripe crops are harvested and replanted and the produce goes into that chest, and only that chest. Nothing
 * outside the farm is touched, and the work is capped (a week at most, and a few hundred crops). Then everyone who's
 * near hears one short summary, and a page goes into the diary on the lectern at the meeting spot, if there is one.
 */
public final class CatchUp {
	/** At most a week is caught up. */
	static final long MAX_ELAPSED = 7L * 24000;
	/** At most this many crops harvested in one catch-up. */
	static final int MAX_HARVESTS = 256;

	/** What happened, for the summary. */
	public record Report(long elapsed, List<String> farmers, Map<Item, Integer> stored, int grown, int ripeWaiting, boolean noStorage, boolean full) {
		public int storedTotal() {
			return stored.values().stream().mapToInt(Integer::intValue).sum();
		}
	}

	private CatchUp() {
	}

	/** Catches the home up, records the summary for whoever comes by, and returns what happened (null if nothing could). */
	public static Report run(ServerLevel level, HomeAreas areas, HomeAreas.Area area, List<CompanionEntity> residents, long elapsed, Random random) {
		Places places = areas.places(level, area);
		Zone farm = places.farm();
		boolean allowed = farm != null && (places.farmMarked() || Routines.enabled()) && farm.volume() <= 4096;
		if (!allowed) {
			return null;
		}
		long time = Math.min(elapsed, MAX_ELAPSED);
		List<CompanionEntity> farmers = residents.stream().filter(c -> c.role() == Role.FARMER).toList();
		List<Container> chests = new ArrayList<>();
		for (BlockPos pos : places.storage()) {
			Container chest = Storage.chestAt(level, pos);
			if (chest != null) {
				chests.add(chest);
			}
		}
		int days = (int) (time / 24000);
		int harvestsEach = farmers.isEmpty() || chests.isEmpty() ? 0 : days + 1;
		Map<Item, Integer> stored = new LinkedHashMap<>();
		int grown = 0;
		int ripeWaiting = 0;
		int harvests = 0;
		boolean full = false;
		for (BlockPos pos : farm.blocks()) {
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop)) {
				continue;
			}
			Yield yield = Yield.of(crop);
			if (yield == null) {
				continue;
			}
			BlockState ground = level.getBlockState(pos.below());
			boolean wet = ground.hasProperty(FarmlandBlock.MOISTURE) && ground.getValue(FarmlandBlock.MOISTURE) > 0;
			int age = crop.getAge(state);
			int allowedHarvests = full || harvests >= MAX_HARVESTS ? 0 : Math.min(harvestsEach, MAX_HARVESTS - harvests);
			CropGrowth.Result result = CropGrowth.simulate(age, crop.getMaxAge(), time, wet, allowedHarvests, random);
			int done = 0;
			for (int i = 0; i < result.harvested(); i++) {
				Map<Item, Integer> drops = yield.roll(random);
				if (!fits(chests, drops)) {
					full = true;
					break;
				}
				drops.forEach((item, count) -> {
					int left = count;
					for (Container chest : chests) {
						left -= Storage.insert(chest, item, Math.min(left, Storage.room(chest, item)));
						if (left <= 0) {
							break;
						}
					}
					stored.merge(item, count, Integer::sum);
				});
				done++;
			}
			harvests += done;
			// Harvests that didn't happen (no room) leave the crop ripe and waiting.
			int finalAge = done < result.harvested() ? crop.getMaxAge() : result.age();
			if (finalAge != age) {
				level.setBlock(pos, crop.getStateForAge(finalAge), Block.UPDATE_CLIENTS);
				grown++;
			}
			if (finalAge >= crop.getMaxAge()) {
				ripeWaiting++;
			}
		}
		Report report = new Report(time, farmers.stream().map(c -> c.companionName().split(" ")[0]).toList(), stored, grown, ripeWaiting,
				chests.isEmpty(), full);
		String summary = summary(report);
		if (summary != null) {
			area.pendingSummary = summary;
			areas.markDirty();
			Diary.write(level, places, summary);
			Hearthbound.LOGGER.info("Caught up {} after {} ticks: {}", area.id, elapsed, summary);
		}
		return report;
	}

	private static boolean fits(List<Container> chests, Map<Item, Integer> drops) {
		for (Map.Entry<Item, Integer> drop : drops.entrySet()) {
			int room = 0;
			for (Container chest : chests) {
				room += Storage.room(chest, drop.getKey());
			}
			if (room < drop.getValue()) {
				return false;
			}
		}
		return true;
	}

	/** One short line, or null if there's nothing worth saying. */
	static String summary(Report report) {
		String when = report.elapsed() >= 36000 ? "the last " + Math.round(report.elapsed() / 24000.0) + " days" : report.elapsed() >= 18000 ? "the last day" : "a while";
		String who = report.farmers().isEmpty() ? null : io.github.tekless.hearthbound.core.dialogue.English.list(report.farmers());
		if (report.storedTotal() > 0 && who != null) {
			List<String> parts = new ArrayList<>();
			report.stored().forEach((item, count) -> {
				if (item != Items.WHEAT_SEEDS && item != Items.BEETROOT_SEEDS && count > 0) {
					parts.add(count + " " + name(item));
				}
			});
			int seeds = report.stored().getOrDefault(Items.WHEAT_SEEDS, 0) + report.stored().getOrDefault(Items.BEETROOT_SEEDS, 0);
			String crops = parts.isEmpty() ? seeds + " seeds" : io.github.tekless.hearthbound.core.dialogue.English.list(parts)
					+ (seeds > 0 ? " (and " + seeds + " spare seeds)" : "");
			return "While you were away (" + when + "): " + who + " harvested " + crops + ", replanted, and put it all in storage."
					+ (report.full() ? " The storage is full now, so some crops are still waiting." : "");
		}
		if (report.grown() > 0 && report.noStorage() && who != null) {
			return "While you were away (" + when + "): the crops grew and " + report.ripeWaiting() + " are ripe, but " + who
					+ " had nowhere to put the harvest. Mark a chest with /companion setplace storage.";
		}
		if (report.grown() > 0) {
			return "While you were away (" + when + "): the crops kept growing" + (report.ripeWaiting() > 0 ? ", and " + report.ripeWaiting() + " are ripe." : ".");
		}
		return null;
	}

	private static String name(Item item) {
		return new net.minecraft.world.item.ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT)
				+ (item == Items.CARROT || item == Items.BEETROOT ? "s" : item == Items.POTATO || item == Items.POISONOUS_POTATO ? "es" : "");
	}

	/** What one ripe crop gives once a seed (or a carrot or potato) has gone back in the ground. */
	private enum Yield {
		WHEAT(Items.WHEAT, Items.WHEAT_SEEDS), CARROTS(null, Items.CARROT), POTATOES(null, Items.POTATO), BEETROOTS(Items.BEETROOT, Items.BEETROOT_SEEDS);

		private final Item produce;
		private final Item seed;

		Yield(Item produce, Item seed) {
			this.produce = produce;
			this.seed = seed;
		}

		static Yield of(CropBlock crop) {
			if (crop == Blocks.WHEAT) {
				return WHEAT;
			}
			if (crop == Blocks.CARROTS) {
				return CARROTS;
			}
			if (crop == Blocks.POTATOES) {
				return POTATOES;
			}
			return crop == Blocks.BEETROOTS ? BEETROOTS : null;
		}

		Map<Item, Integer> roll(Random random) {
			Map<Item, Integer> drops = new LinkedHashMap<>();
			if (produce != null) {
				drops.put(produce, 1);
			}
			// One seed (or carrot, or potato) was guaranteed and went straight back in; the bonus ones are spare.
			int spare = CropGrowth.bonusDrops(random);
			if (spare > 0) {
				drops.merge(seed, spare, Integer::sum);
			}
			if (this == POTATOES && random.nextInt(50) == 0) {
				drops.put(Items.POISONOUS_POTATO, 1);
			}
			return drops;
		}
	}
}
