# Changelog

## Unreleased: server support (2026-10-21)

- Runs on a dedicated server with no client mod needed: with Polymer installed, companions are sent
  to unmodded players as mannequins with their skin, name and a status line.
- Hub residents: the cast can live at a configured spawn hub, spawn there once, greet visitors and
  return there when dismissed. `/companion hub list|spawn|home`.
- Multiplayer rules: only the owner gives orders ("I'm with ..." lines for everyone else),
  strangers can't hurt companions, companions never target players or others' pets, replies reach
  the owner, the speaker and anyone nearby, no gathering in protected areas or next to builds, no
  looting other players' items, `@name` works anywhere in a message.
- Per-speaker conversation memory, a per-player AI rate limit and a cap on concurrent AI requests.
- A world roster and loaded-companion tracker replace large entity scans.
- Redrawn skins for Cass, Conoby, Samuel and Casper; travel outfits for Rosie and Luna.

## 0.1.0 - preview (2026-10-20)

First public preview for Minecraft Java 26.3 (Fabric).

- Companions with the 18 default player looks (9 skins x wide/slim), rendered with the vanilla
  player model and the game's own textures.
- Six archetypes (Sellsword, Naturalist, Rogue, Guardian, Farmhand, Mystic) that differ in voice,
  interests and combat behaviour, each with ~90 situations of scripted dialogue.
- Orders: recruit, dismiss, follow, wait, come, gather, give, attack, cancel, status, open pack.
- Talk in normal chat (`@Name ...`, `Name, ...`, `@all ...`), the Talk key (G), or `/companion`.
- Follow with personal distance, hurry, and safe catch-up teleport (also through dimensions).
- Gathering of natural blocks with tool rules and honest progress/failure reports.
- Essential companions (knocked down instead of killed), warnings, ambient comments, party HUD.
- Saves identity, orders, inventory, conversation memory and affinity with the world.
- Optional AI dialogue via Ollama, OpenAI-compatible APIs or Anthropic; the model can only propose
  intents that the game validates.
