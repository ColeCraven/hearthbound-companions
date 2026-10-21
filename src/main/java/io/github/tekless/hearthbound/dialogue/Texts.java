package io.github.tekless.hearthbound.dialogue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.tekless.hearthbound.Hearthbound;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Translatable messages that still read properly on clients without the mod: every component
 * carries the English text from our own {@code en_us.json} as its fallback, so a vanilla client
 * shows "No companion called..." instead of the raw key.
 */
public final class Texts {
	private static final Map<String, String> ENGLISH = load();

	private Texts() {
	}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatableWithFallback(key, ENGLISH.getOrDefault(key, key), args);
	}

	private static Map<String, String> load() {
		try (InputStream in = Hearthbound.class.getResourceAsStream("/assets/hearthbound/lang/en_us.json")) {
			if (in != null) {
				Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
						new TypeToken<Map<String, String>>() { }.getType());
				if (map != null) {
					return map;
				}
			}
		} catch (Exception e) {
			Hearthbound.LOGGER.warn("Could not read the English text for fallbacks: {}", e.getMessage());
		}
		return Map.of();
	}
}
