package io.github.tekless.hearthbound.entity;

import io.github.tekless.hearthbound.core.dialogue.English;
import io.github.tekless.hearthbound.core.persona.Archetype;
import io.github.tekless.hearthbound.entity.order.BuildOrder;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.world.level.block.DoorBlock;

/**
 * Right-click a companion with an item to give it to them. Each personality has favourites; gifts
 * raise affinity (capped, so you can't farm it), and weapons or armour get equipped.
 */
public final class Gifts {
	private static final long GIFT_COOLDOWN_TICKS = 1200;

	private Gifts() {
	}

	/** Items that should keep their normal use on a companion instead of being given away. */
	public static boolean isTool(ItemStack stack) {
		return stack.is(Items.NAME_TAG) || stack.is(Items.LEAD) || stack.getItem() instanceof SpawnEggItem;
	}

	public static void receive(CompanionEntity companion, ServerPlayer player, ItemStack held) {
		// Building supplies (blocks, torches, doors) come in stacks; everything else is one at a time.
		boolean supplies = BuildOrder.isBuildingBlock(held) || held.is(Items.TORCH)
				|| (held.getItem() instanceof BlockItem item && item.getBlock() instanceof DoorBlock);
		ItemStack gift = held.split(supplies ? held.getCount() : 1);
		String name = gift.getHoverName().getString().toLowerCase(Locale.ROOT);
		if (supplies && gift.getCount() > 1) {
			name = gift.getCount() + " " + name;
		}
		boolean loved = loves(companion.archetype(), gift);
		boolean gear = companion.getEquipmentSlotForItem(gift) != EquipmentSlot.MAINHAND || gift.is(ItemTags.SWORDS) || gift.is(ItemTags.AXES);
		companion.storeOrDrop(gift);
		companion.getLookControl().setLookAt(player);
		companion.level().playSound(null, companion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.3F);
		long now = companion.level().getGameTime();
		if (now - companion.lastGiftTime() > GIFT_COOLDOWN_TICKS) {
			companion.memory().changeAffinity(loved ? 4 : 1);
			companion.setLastGiftTime(now);
			if (loved) {
				companion.memory().addEvent("{player} gave me " + English.withArticle(name));
			}
		}
		companion.say(loved ? "gift.loved" : "gift.received", Map.of("item", name));
		if (gear) {
			companion.equipFromPackSoon();
		}
	}

	static boolean loves(Archetype archetype, ItemStack stack) {
		return switch (archetype) {
			case SELLSWORD -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.SHIELD) || stack.is(Items.IRON_INGOT)
					|| stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.HEAD_ARMOR);
			case NATURALIST -> stack.is(BlockItemTags.SMALL_FLOWERS.item()) || stack.is(ItemTags.SAPLINGS) || stack.is(Items.BOOK)
					|| stack.is(Items.SPYGLASS) || stack.is(Items.HONEYCOMB) || stack.is(Items.AMETHYST_SHARD);
			case ROGUE -> stack.is(Items.EMERALD) || stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET) || stack.is(Items.DIAMOND)
					|| stack.is(Items.GOLDEN_APPLE) || stack.is(Items.CLOCK);
			case GUARDIAN -> stack.is(Items.SHIELD) || stack.is(Items.BREAD) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.IRON_SWORD)
					|| stack.is(Items.POPPY) || stack.is(Items.TOTEM_OF_UNDYING);
			case FARMHAND -> stack.is(Items.BREAD) || stack.is(Items.CAKE) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.CARROT)
					|| stack.is(Items.WHEAT_SEEDS) || stack.is(Items.APPLE) || stack.is(Items.COOKIE) || stack.is(Items.HONEY_BOTTLE);
			case MYSTIC -> stack.is(Items.AMETHYST_SHARD) || stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE) || stack.is(Items.ECHO_SHARD)
					|| stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.CHORUS_FRUIT) || stack.is(Items.OBSIDIAN);
		};
	}
}
