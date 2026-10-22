package io.github.tekless.hearthbound.dialogue;

import io.github.tekless.hearthbound.core.memory.CompanionMemory;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import io.github.tekless.hearthbound.world.Obey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * {@code /companion explain <name>}: the last thing they heard, how it was read and who it was for, what they made of
 * it, what they're doing now and anything holding them up. For working out why someone didn't do what you said.
 */
public final class Explain {
	private Explain() {
	}

	/** @param viewer who's asking (null from the console or a command block) */
	public static List<String> lines(CompanionEntity companion, @Nullable ServerPlayer viewer) {
		List<String> out = new ArrayList<>();
		String name = companion.companionName();
		HeardLog.last(companion).ifPresentOrElse(heard -> {
			long seconds = Math.max(0, (companion.level().getGameTime() - heard.tick()) / 20);
			out.add(name + " last heard, " + ago(seconds) + ", from " + heard.speaker() + ": \"" + heard.text() + "\"");
			out.add("Read as: " + heard.reading());
			out.add("Meant for: " + heard.recipients());
			out.add("Result: " + heard.result());
		}, () -> out.add(name + " hasn't heard anything since the server started."));
		List<CompanionMemory.Line> said = companion.memory().lines();
		for (int i = said.size() - 1; i >= 0; i--) {
			if (!said.get(i).fromPlayer()) {
				out.add("Last said: \"" + said.get(i).text() + "\"");
				break;
			}
		}
		out.add("Now: " + (companion.isDowned() ? "knocked down" : companion.isSleeping() ? "asleep" : companion.order().describe()));
		List<String> blockers = blockers(companion, viewer);
		out.add("Blockers: " + (blockers.isEmpty() ? "none" : String.join("; ", blockers)));
		return out;
	}

	/** Anything that stops them doing what they're asked, right now. */
	public static List<String> blockers(CompanionEntity companion, @Nullable ServerPlayer viewer) {
		List<String> out = new ArrayList<>();
		if (companion.isDowned()) {
			out.add("knocked down (help them up, or give them a moment)");
		}
		if (companion.isSleeping()) {
			out.add("asleep (talking to them wakes them)");
		}
		String job = companion.order().blocker();
		if (job != null) {
			out.add(job);
		}
		if (companion.ownerId() == null) {
			out.add("not with anyone yet, so jobs wait until they join (or /companion obey on)");
		} else if (viewer != null && !companion.isOwnedBy(viewer) && !Obey.active(viewer)) {
			out.add("travels with " + companion.ownerName() + ", so takes orders from them, not you");
		}
		boolean room = false;
		for (int slot = 0; slot < companion.inventory().getContainerSize(); slot++) {
			if (companion.inventory().getItem(slot).isEmpty()) {
				room = true;
				break;
			}
		}
		if (!room) {
			out.add("pack full");
		}
		if (companion.getHealth() < companion.getMaxHealth() * 0.3F && !companion.isDowned()) {
			out.add("badly hurt");
		}
		String need = companion.needs().pressing();
		if (need != null) {
			out.add(need);
		}
		if (viewer != null && companion.level() != viewer.level()) {
			out.add("in another dimension");
		}
		return out;
	}

	private static String ago(long seconds) {
		if (seconds < 2) {
			return "just now";
		}
		if (seconds < 120) {
			return seconds + " seconds ago";
		}
		return seconds / 60 + " minutes ago";
	}
}
