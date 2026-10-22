package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.persona.Role;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Small favours: now and then someone who lives at a home asks a passing player for something their work needs
 * ("bring me 16 logs") and offers something fair from their own pack in return. They only ask when they really
 * have the reward on them, a player hands things over by right-clicking them with the item, and the reward comes
 * out of the companion's pack (so it can run out). One favour per player a day, and each one lasts a day.
 */
public final class Favours {
	private static final long PER_PLAYER_GAP = 24000;
	private static final long LASTS = 24000;

	/** Something a companion might want, by role. */
	record Want(String name, Predicate<ItemStack> test, int count, double value) {
	}

	/** A favour asked of one player. */
	public record Favour(UUID player, Want want, int needed, int received, Item reward, int rewardCount, long until) {
		Favour withReceived(int more) {
			return new Favour(player, want, needed, received + more, reward, rewardCount, until);
		}
	}

	private static final Map<Role, List<Want>> WANTS = Map.of(
			Role.COLLECTOR, List.of(new Want("logs", s -> s.is(ItemTags.LOGS), 16, 1.0)),
			Role.BUILDER, List.of(new Want("cobblestone", s -> s.is(Items.COBBLESTONE), 32, 0.5), new Want("logs", s -> s.is(ItemTags.LOGS), 16, 1.0)),
			Role.FARMER, List.of(new Want("bone meal", s -> s.is(Items.BONE_MEAL), 8, 2.0)),
			Role.GUARDIAN, List.of(new Want("arrows", s -> s.is(Items.ARROW), 16, 1.0), new Want("iron ingots", s -> s.is(Items.IRON_INGOT), 4, 4.0)));
	/** What things are worth, roughly, for a fair swap. */
	private static final Map<Item, Double> VALUE = Map.ofEntries(Map.entry(Items.DIAMOND, 40.0), Map.entry(Items.EMERALD, 16.0),
			Map.entry(Items.GOLD_INGOT, 5.0), Map.entry(Items.IRON_INGOT, 4.0), Map.entry(Items.GOLDEN_CARROT, 4.0), Map.entry(Items.PUMPKIN_PIE, 3.0),
			Map.entry(Items.HONEY_BOTTLE, 3.0), Map.entry(Items.BREAD, 2.0), Map.entry(Items.APPLE, 2.0), Map.entry(Items.COOKED_BEEF, 2.0),
			Map.entry(Items.COOKED_PORKCHOP, 2.0), Map.entry(Items.COOKED_CHICKEN, 2.0), Map.entry(Items.COOKED_MUTTON, 2.0),
			Map.entry(Items.BAKED_POTATO, 2.0), Map.entry(Items.COOKIE, 1.0), Map.entry(Items.CARROT, 1.0), Map.entry(Items.POTATO, 1.0),
			Map.entry(Items.TORCH, 0.5));
	private static final Set<String> UNCOUNTABLE = Set.of("bread", "cooked beef", "cooked mutton", "wheat", "cobblestone", "bone meal", "honey bottle");

	private static final Map<UUID, Favour> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> ASKED = new HashMap<>();

	private Favours() {
	}

	/**
	 * Maybe asks this player a favour. Only when the companion has something fair to give, the player hasn't been
	 * asked by anyone today, and there's no favour already open.
	 */
	public static boolean maybeAsk(CompanionEntity companion, ServerPlayer player, long now) {
		Favour open = ACTIVE.get(companion.getUUID());
		if (open != null && now < open.until()) {
			return false;
		}
		if (now - ASKED.getOrDefault(player.getUUID(), -PER_PLAYER_GAP) < PER_PLAYER_GAP || companion.isOwnedBy(player)) {
			return false;
		}
		List<Want> wants = WANTS.getOrDefault(companion.role(), List.of());
		if (wants.isEmpty()) {
			return false;
		}
		Want want = wants.get(companion.getRandom().nextInt(wants.size()));
		Item reward = bestReward(companion);
		if (reward == null) {
			return false;
		}
		double each = VALUE.get(reward);
		int available = companion.countCarried(s -> s.is(reward)) - (isFood(reward) ? 1 : 0); // keep a bite for themselves
		int needed = want.count();
		int rewardCount = (int) Math.ceil(needed * want.value() / each);
		if (rewardCount > available) {
			// Ask for less rather than promise what they haven't got.
			rewardCount = available;
			needed = (int) Math.floor(rewardCount * each / want.value());
		}
		if (rewardCount < 1 || needed < 4) {
			return false;
		}
		ASKED.put(player.getUUID(), now);
		ACTIVE.put(companion.getUUID(), new Favour(player.getUUID(), want, needed, 0, reward, rewardCount, now + LASTS));
		companion.noteSpeaker(player);
		companion.getLookControl().setLookAt(player);
		companion.say("life.favour.ask", Map.of("count", Integer.toString(needed), "item", want.name(), "reward", phrase(reward, rewardCount)));
		return true;
	}

	/**
	 * A player right-clicked the companion holding something. Takes what the favour still needs and pays up when
	 * it's done. Returns true if the item was taken for a favour.
	 */
	public static boolean receive(CompanionEntity companion, ServerPlayer player, ItemStack held) {
		Favour favour = ACTIVE.get(companion.getUUID());
		long now = companion.level().getGameTime();
		if (favour == null || !favour.player().equals(player.getUUID()) || now >= favour.until() || !favour.want().test().test(held)) {
			return false;
		}
		int take = Math.min(held.getCount(), favour.needed() - favour.received());
		if (take <= 0) {
			return false;
		}
		companion.storeOrDrop(held.split(take));
		companion.level().playSound(null, companion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.3F);
		Favour updated = favour.withReceived(take);
		companion.noteSpeaker(player);
		if (updated.received() < updated.needed()) {
			ACTIVE.put(companion.getUUID(), updated);
			companion.say("life.favour.partial", Map.of("count", Integer.toString(updated.needed() - updated.received()), "item", favour.want().name()));
			return true;
		}
		ACTIVE.remove(companion.getUUID());
		int paid = companion.giveTo(player, s -> s.is(favour.reward()), favour.rewardCount());
		companion.memory().addEvent("{player} did me a favour and brought me " + favour.needed() + " " + favour.want().name());
		companion.wave();
		if (paid > 0) {
			companion.say("life.favour.thanks", Map.of("reward", phrase(favour.reward(), paid)));
		} else {
			companion.say("life.favour.cant_pay", Map.of());
		}
		return true;
	}

	/** "16 logs for 3 bread", for status replies, or null when there is no favour open. */
	public static @Nullable String describe(CompanionEntity companion) {
		Favour favour = ACTIVE.get(companion.getUUID());
		if (favour == null || companion.level().getGameTime() >= favour.until()) {
			return null;
		}
		return (favour.needed() - favour.received()) + " " + favour.want().name() + " for " + phrase(favour.reward(), favour.rewardCount());
	}

	private static @Nullable Item bestReward(CompanionEntity companion) {
		Item best = null;
		double bestValue = 0;
		for (ItemStack stack : companion.inventory().getItems()) {
			Double value = stack.isEmpty() ? null : VALUE.get(stack.getItem());
			if (value == null) {
				continue;
			}
			int count = companion.countCarried(s -> s.is(stack.getItem())) - (isFood(stack.getItem()) ? 1 : 0);
			if (count > 0 && value * count > bestValue) {
				best = stack.getItem();
				bestValue = value * count;
			}
		}
		return best;
	}

	private static boolean isFood(Item item) {
		return item != Items.DIAMOND && item != Items.EMERALD && item != Items.GOLD_INGOT && item != Items.IRON_INGOT && item != Items.TORCH;
	}

	/** "an emerald", "3 bread", "2 apples". */
	static String phrase(Item item, int count) {
		String name = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		if (count == 1) {
			return English.withArticle(name);
		}
		if (UNCOUNTABLE.contains(name)) {
			return count + " " + name;
		}
		if (name.endsWith("o") || name.endsWith("ch")) {
			return count + " " + name + "es";
		}
		return count + " " + name + (name.endsWith("s") ? "" : "s");
	}

	/** Forgets every favour (the world closed). */
	public static void reset() {
		ACTIVE.clear();
		ASKED.clear();
	}
}
