package io.github.tekless.hearthbound.test;

import io.github.tekless.hearthbound.Hearthbound;
import io.github.tekless.hearthbound.ai.AiSetup;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * One-click AI setup, as far as it goes without a model to talk to: no key means being told where it goes (and nothing
 * changing), status says what's on, and an Ollama that isn't running is reported plainly. Nothing here turns AI on,
 * so the other tests running alongside keep their scripted lines.
 */
public class AiSetupGameTests {
	@GameTest(maxTicks = 40)
	public void withoutAKeyYouAreToldWhereItGoes(GameTestHelper helper) {
		String before = Hearthbound.config().ai.provider;
		for (String provider : List.of("anthropic", "openai")) {
			AiSetup.Cloud result = AiSetup.cloud(helper.getLevel().getServer(), provider, "some-model");
			String said = String.join("\n", result.now());
			helper.assertTrue(result.test() == null, "no test without a key (" + provider + ")");
			helper.assertTrue(said.contains("Don't type it in chat"), "never in chat: " + said);
			helper.assertTrue(said.contains(provider.equals("openai") ? "OPENAI_API_KEY" : "ANTHROPIC_API_KEY"), "the variable: " + said);
			helper.assertTrue(said.contains("config/hearthbound-secrets.json"), "or the file: " + said);
			helper.assertValueEqual(Hearthbound.config().ai.provider, before, "and nothing changed");
		}
		helper.assertTrue(String.join(" ", AiSetup.status()).contains("off"), "status says it's off: " + AiSetup.status());
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void anOllamaThatIsntRunningIsSaidPlainly(GameTestHelper helper) {
		CompletableFuture<AiSetup.Found> found = AiSetup.findOllamaModels();
		helper.succeedWhen(() -> {
			helper.assertTrue(found.isDone(), "asked");
			AiSetup.Found result = found.join();
			helper.assertTrue(result.problem().isPresent() || !result.models().isEmpty(), "either models or a reason");
			if (result.models().isEmpty()) {
				helper.assertTrue(result.problem().get().contains("ollama"), "with what to do: " + result.problem().get());
			}
		});
	}
}
