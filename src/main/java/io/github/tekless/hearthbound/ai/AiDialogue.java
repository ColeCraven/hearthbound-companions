package io.github.tekless.hearthbound.ai;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.core.intent.Intent;
import io.github.tekless.hearthbound.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional AI conversation. Returns false whenever no model is configured (or it is unavailable),
 * in which case the caller falls back to scripted dialogue. Never blocks the game thread.
 */
public final class AiDialogue {
	private AiDialogue() {
	}

	/**
	 * Starts an asynchronous AI reply if a provider is configured and healthy.
	 *
	 * @param proposalHint an intent the rule-based parser already recognised, or null
	 * @return true if an AI reply is on its way (the caller should not answer itself)
	 */
	public static boolean tryConverse(ServerPlayer player, CompanionEntity companion, String text, Intent proposalHint) {
		AiService service = Hearthbound.ai();
		return service != null && service.converse(player, companion, text);
	}
}
