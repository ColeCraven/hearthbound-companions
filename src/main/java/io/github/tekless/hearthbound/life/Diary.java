package io.github.tekless.hearthbound.life;

import io.github.tekless.hearthbound.Hearthbound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

/**
 * The home's diary: a written book on a lectern near the meeting spot, with a page for each "while you were away".
 * Only an empty lectern gets one, and only that diary is ever written in; any other book is left alone.
 */
final class Diary {
	static final String TITLE = "Our diary";
	private static final int MAX_PAGES = 50;

	private Diary() {
	}

	static void write(ServerLevel level, Places places, String text) {
		BlockPos pos = places.lectern();
		if (!Hearthbound.config().diary || pos == null || !level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
			return;
		}
		long day = level.getOverworldClockTime() / 24000 + 1;
		Filterable<Component> page = Filterable.passThrough(Component.literal("Day " + day + "\n\n" + text));
		ItemStack book = lectern.getBook();
		if (book.isEmpty()) {
			ItemStack diary = new ItemStack(Items.WRITTEN_BOOK);
			diary.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), "The neighbours", 0, List.of(page), true));
			LecternBlock.tryPlaceBook(null, level, pos, level.getBlockState(pos), diary);
			return;
		}
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (!book.is(Items.WRITTEN_BOOK) || content == null || !TITLE.equals(content.title().raw())) {
			return; // someone else's book
		}
		List<Filterable<Component>> pages = new ArrayList<>(content.pages());
		pages.add(page);
		while (pages.size() > MAX_PAGES) {
			pages.remove(0);
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(content.title(), content.author(), content.generation(), pages, true));
		lectern.setChanged();
	}
}
