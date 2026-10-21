package io.github.tekless.hearthbound.command;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** What {@code /companion help} prints: everything you can say and do, with examples. */
final class HelpText {
	private HelpText() {
	}

	static List<Component> lines() {
		return List.of(
				Component.literal("Hearthbound Companions").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				section("Talking"),
				item("@Name hi", " then just keep typing. Say bye when you're done."),
				item("@all ...", " talks to everyone travelling with you."),
				item("G", " opens chat with the name of whoever you're looking at. Right-click someone to say hi."),
				item("call me Captain", " · ", "I love the ocean", " · ", "I hate spiders", " They remember, and bring it up."),
				item("what do you know about me?", " · ", "are we friends?"),
				plain("Crouch twice while looking at someone and they'll crouch back."),
				section("Recruiting"),
				item("@Name join me", " / ", "@Name you're dismissed", " (up to 3 at a time)"),
				section("Orders"),
				item("follow me", " · ", "wait here", " · ", "come here", " · ", "stop", " · ", "what are you doing?"),
				item("get me 16 oak logs", " · ", "give me everything", " · ", "attack that", " (look at it)"),
				section("Digging (needs a pickaxe)"),
				item("go mining", " digs down, then along, picking up ores."),
				item("dig a tunnel", " · ", "dig down 20", " · torches go up if they carry some."),
				section("Building (from blocks in their pack)"),
				item("build a shelter", " · ", "build a wall", " · ", "build a bridge", " (across water or a gap ahead)"),
				section("Gifts and gear"),
				plain("Right-click with an item to give it. Blocks, torches and doors go over as a whole stack."),
				plain("Sneak + right-click to open their pack. They wear armour and use the best sword you give them."),
				section("Commands"),
				item("/companion list", " · ", "/companion help"));
	}

	private static Component section(String title) {
		return Component.literal(title).withStyle(ChatFormatting.YELLOW);
	}

	private static Component plain(String text) {
		return Component.literal("  " + text).withStyle(ChatFormatting.GRAY);
	}

	/** Alternating example (white) and explanation (grey) parts. */
	private static Component item(String... parts) {
		MutableComponent line = Component.literal("  ");
		for (int i = 0; i < parts.length; i++) {
			line.append(Component.literal(parts[i]).withStyle(i % 2 == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		}
		return line;
	}
}
