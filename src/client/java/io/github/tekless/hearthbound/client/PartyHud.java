package io.github.tekless.hearthbound.client;

import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.PartyStatusPayload;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A small, translucent party list in the top-left corner: name, what each companion is doing,
 * and a health bar. Hidden with F1, while the debug screen is open, or when you have no party.
 */
public final class PartyHud {
	private static volatile List<PartyStatusPayload.Entry> entries = List.of();
	private static long lastUpdate;

	private PartyHud() {
	}

	public static void update(PartyStatusPayload payload) {
		entries = payload.entries();
		lastUpdate = System.currentTimeMillis();
	}

	public static List<PartyStatusPayload.Entry> entries() {
		return entries;
	}

	public static void clear() {
		entries = List.of();
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entries.isEmpty() || minecraft.getDebugOverlay().showDebugScreen()
				|| System.currentTimeMillis() - lastUpdate > 5000 || !HearthboundClient.hudVisible()) {
			return;
		}
		Font font = minecraft.font;
		int x = 4;
		int y = 4;
		int width = 0;
		for (PartyStatusPayload.Entry entry : entries) {
			width = Math.max(width, Math.max(font.width(entry.name()) + 46, font.width(entry.task()) + 8));
		}
		width = Math.min(width, 180);
		for (PartyStatusPayload.Entry entry : entries) {
			graphics.fill(x - 2, y - 2, x + width, y + 20, 0x66000000);
			int colour = CompanionEntity.nameRgb(entry.archetype());
			graphics.text(font, Component.literal(entry.name()), x, y, 0xFF000000 | colour, true);
			// Health bar to the right of the name.
			int barX = x + font.width(entry.name()) + 6;
			int barWidth = 36;
			float fraction = entry.maxHealth() <= 0 ? 0 : Mth.clamp(entry.health() / entry.maxHealth(), 0, 1);
			graphics.fill(barX, y + 2, barX + barWidth, y + 6, 0xAA330000);
			graphics.fill(barX, y + 2, barX + Math.round(barWidth * fraction), y + 6, entry.downed() ? 0xFF777777 : fraction < 0.3F ? 0xFFDD3333 : 0xFF44CC44);
			String task = font.plainSubstrByWidth(entry.task(), width - 4);
			graphics.text(font, Component.literal(task), x, y + 10, entry.downed() ? 0xFFFF6666 : 0xFFBBBBBB, true);
			y += 24;
		}
	}
}
