# Changelog

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
