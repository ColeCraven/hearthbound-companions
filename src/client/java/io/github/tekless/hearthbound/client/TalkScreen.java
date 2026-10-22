package io.github.tekless.hearthbound.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.network.PartyStatusPayload;
import io.github.tekless.hearthbound.network.TalkPayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Talking to one companion without the chat box: who they are and how they're doing at the top, what's been said in the
 * middle, quick replies and a few suggestions for right now, and a box to type anything else. Everything sent from here
 * goes through the same parser as chat, so it does exactly what saying it would.
 */
public final class TalkScreen extends Screen {
	private static final int PANEL_WIDTH = 340;
	private static @Nullable TalkPayloads.State latest;

	private int entityId;
	private @Nullable EditBox input;
	private List<String> shownSuggestions = List.of();
	private int scroll;
	private int ticks;

	public TalkScreen(int entityId) {
		super(Component.translatable("screen.hearthbound.talk"));
		this.entityId = entityId;
	}

	/** A fresh picture of the companion from the server. */
	public static void receive(TalkPayloads.State state) {
		latest = state;
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof TalkScreen screen && screen.entityId == state.header().entityId()) {
			screen.refresh();
		}
	}

	private @Nullable TalkPayloads.State state() {
		return latest != null && latest.header().entityId() == entityId ? latest : null;
	}

	private void refresh() {
		TalkPayloads.State state = state();
		if (state != null && !state.suggestions().equals(shownSuggestions)) {
			rebuildWidgets(); // new things worth saying: new buttons
		}
	}

	private int left() {
		return (width - panelWidth()) / 2;
	}

	private int panelWidth() {
		return Math.min(PANEL_WIDTH, width - 16);
	}

	@Override
	protected void init() {
		String typed = input == null ? "" : input.getValue();
		int left = left();
		int panel = panelWidth();
		int bottom = height - 10;

		// Who else you can talk to: one button per companion in the party, along the top.
		int x = left;
		for (PartyStatusPayload.Entry entry : PartyHud.entries()) {
			String first = entry.name().split(" ")[0];
			int w = Math.min(70, font.width(first) + 12);
			if (x + w > left + panel) {
				break;
			}
			int id = entry.entityId();
			Button tab = Button.builder(Component.literal(first), button -> switchTo(id)).bounds(x, 8, w, 16).build();
			tab.active = id != entityId;
			addRenderableWidget(tab);
			x += w + 2;
		}

		input = new EditBox(font, left, bottom - 18, panel - 52, 18, Component.translatable("screen.hearthbound.talk.input"));
		input.setMaxLength(256);
		input.setHint(Component.translatable("screen.hearthbound.talk.hint"));
		input.setValue(typed);
		addRenderableWidget(input);
		addRenderableWidget(Button.builder(Component.translatable("screen.hearthbound.talk.send"), button -> send(input.getValue()))
				.bounds(left + panel - 48, bottom - 18, 48, 18).build());

		// Quick replies: the everyday orders, one click each. "Give" fills the box so you can say what.
		String[][] quick = {
				{"follow", "Follow me"}, {"wait", "Wait here"}, {"come", "Come here"}, {"status", "What are you doing?"}};
		int qx = left;
		int qy = bottom - 40;
		int qw = (panel - 4 * 3) / 5;
		for (String[] reply : quick) {
			String line = reply[1];
			addRenderableWidget(Button.builder(Component.translatable("screen.hearthbound.talk." + reply[0]), button -> send(line))
					.bounds(qx, qy, qw, 18).build());
			qx += qw + 3;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.hearthbound.talk.give"), button -> {
			input.setValue("Give me ");
			setFocused(input);
		}).bounds(qx, qy, qw, 18).build());

		// Things worth saying right now, from the server: a chest you're looking at, something to smelt, a saved build.
		TalkPayloads.State state = state();
		shownSuggestions = state == null ? List.of() : List.copyOf(state.suggestions());
		int sx = left;
		int sy = bottom - 62;
		for (String suggestion : shownSuggestions) {
			int w = Math.min(panel, font.width(suggestion) + 10);
			if (sx + w > left + panel) {
				break;
			}
			addRenderableWidget(Button.builder(Component.literal(suggestion), button -> send(suggestion)).bounds(sx, sy, w, 18).build());
			sx += w + 3;
		}
		setInitialFocus(input);
	}

	private void switchTo(int id) {
		entityId = id;
		scroll = 0;
		ClientPlayNetworking.send(new TalkPayloads.Open(id));
		rebuildWidgets();
	}

	private void send(String text) {
		String line = text == null ? "" : text.strip();
		if (line.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new TalkPayloads.Say(entityId, line));
		if (input != null) {
			input.setValue("");
		}
		scroll = 0;
		ticks = 14; // ask again shortly, for the reply
	}

	@Override
	public void tick() {
		if (++ticks >= 20) {
			ticks = 0;
			ClientPlayNetworking.send(new TalkPayloads.Open(entityId));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && input != null && input.isFocused()) {
			send(input.getValue());
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll + (scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = left();
		int panel = panelWidth();
		int top = 30;
		int historyBottom = height - 10 - 66;
		graphics.fill(left - 4, top - 4, left + panel + 4, historyBottom + 2, 0xAA000000);
		TalkPayloads.State state = state();
		if (state == null) {
			graphics.text(font, Component.translatable("screen.hearthbound.talk.waiting"), left, top, 0xFFAAAAAA, false);
			return;
		}
		TalkPayloads.Header header = state.header();
		int colour = 0xFF000000 | CompanionEntity.nameRgb(header.archetype());
		graphics.text(font, Component.literal(header.name()), left, top, colour, true);
		String role = header.role();
		graphics.text(font, Component.literal(role), left + panel - font.width(role), top, 0xFFCCCCCC, false);
		String about = "Mood: " + header.mood() + "   Needs: " + header.need();
		graphics.text(font, Component.literal(about), left, top + 11, 0xFFBBBBBB, false);
		graphics.text(font, Component.literal(font.plainSubstrByWidth("Now: " + header.task(), panel)), left, top + 22, 0xFFBBBBBB, false);
		graphics.horizontalLine(left, left + panel, top + 33, 0x66FFFFFF);

		// The conversation, newest at the bottom; scroll to see older lines.
		List<FormattedCharSequence> rows = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		String first = header.name().split(" ")[0];
		for (TalkPayloads.Line line : state.lines()) {
			String text = (line.fromPlayer() ? "You: " : first + ": ") + line.text();
			for (FormattedCharSequence row : font.split(Component.literal(text), panel)) {
				rows.add(row);
				colours.add(line.fromPlayer() ? 0xFFDDDDAA : 0xFFFFFFFF);
			}
		}
		int lineHeight = font.lineHeight + 1;
		int visible = Math.max(1, (historyBottom - (top + 38)) / lineHeight);
		scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
		int end = rows.size() - scroll;
		int start = Math.max(0, end - visible);
		int y = historyBottom - (end - start) * lineHeight;
		if (rows.isEmpty()) {
			graphics.text(font, Component.translatable("screen.hearthbound.talk.empty"), left, historyBottom - lineHeight, 0xFF888888, false);
		}
		for (int i = start; i < end; i++) {
			graphics.text(font, rows.get(i), left, y, colours.get(i), false);
			y += lineHeight;
		}
	}
}
