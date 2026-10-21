# AI dialogue design

The mod is **fully playable without any AI service**. Every companion has a scripted,
personality-specific line bank (see [PERSONALITIES.md](PERSONALITIES.md)), and every task works the
same with or without AI. A language model is an *optional* upgrade that makes free-form
conversation richer.

## Provider options

| Provider | Setting | Runs on | Cost | Privacy | Notes |
|---|---|---|---|---|---|
| **None** (default) | `"provider": "none"` | - | Free | Nothing leaves your PC | Scripted lines, rule-based command parser. |
| **Ollama** (local) | `"provider": "ollama"` | Your GPU/CPU | Free (electricity) | Nothing leaves your PC* | Native `/api/chat` with a JSON-schema `format`, `think: false`, explicit `num_ctx`. |
| **OpenAI-compatible** | `"provider": "openai"` | Cloud or local server | Pay per token | Prompts sent to that provider | Works with OpenAI, OpenRouter, Groq, LM Studio, llama.cpp server - set `baseUrl`. Uses `response_format: json_schema`. |
| **Anthropic** | `"provider": "anthropic"` | Cloud | Pay per token | Prompts sent to Anthropic | Messages API with `output_config.format` (JSON schema). |

\*Ollama model tags ending in `:cloud` are proxied to ollama.com even when you call localhost. The
mod refuses `:cloud` tags unless you set `"allowCloudModels": true`.

Raw HTTP (`java.net.http.HttpClient`) plus Gson is used for all providers instead of each
vendor's SDK. Minecraft already bundles Gson, so the mod adds **no extra libraries** - SDKs would
add several megabytes of dependencies (HTTP clients, Kotlin/Jackson runtimes) that can clash with
other mods. All three APIs are documented REST endpoints.

### Hardware for local models (Ollama)

Approximate VRAM at 4-bit quantisation with an 8k context
([Ollama library](https://ollama.com/library), model cards on Hugging Face):

| Your GPU | Suggested model | Licence |
|---|---|---|
| CPU only / 4 GB | `qwen3:4b` (slow on CPU: 15-60 s per reply) | Apache-2.0 |
| 8 GB | `qwen3.5:4b`, `gemma4:e4b` | Apache-2.0 |
| 12-16 GB | `qwen3.5:9b`, `gemma4:12b`, `ministral-3:8b` | Apache-2.0 |
| 24 GB | `mistral-small3.2:24b` | Apache-2.0 |

A warm 8-9B model on a modern GPU answers a companion turn (~1.5k tokens in, ~150 out) in roughly
1-5 seconds. Minecraft itself also needs VRAM, so leave headroom.

### Cloud cost estimate

A typical turn is ~1,500 input tokens (character sheet, memory, world state, recent chat) and
~150 output tokens. Per **100 companion replies**, at list prices on 2026-10-20:

| Model | Input / output per 1M tokens | ~Cost per 100 replies |
|---|---|---|
| Anthropic `claude-haiku-5-5` | $0.10 / $0.50 | ~$0.03 |
| Anthropic `claude-opus-5-5` (effort `low`) | $4.00 / $20.00 | ~$1.10 (incl. some thinking tokens) |
| OpenAI `gpt-5-nano` | $0.05 / $0.40 | ~$0.014 |
| Groq `openai/gpt-oss-20b` | $0.075 / $0.30 | ~$0.016 |

Sources: [OpenAI pricing](https://developers.openai.com/api/docs/pricing),
[Groq models](https://console.groq.com/docs/models), Anthropic pricing from
[platform.claude.com](https://platform.claude.com/docs/en/about-claude/pricing). Prices change -
check before relying on them. Reasoning/"thinking" tokens are billed as output.

## What is sent to a cloud provider

Only for the companion you are talking to, and only when the rule-based parser couldn't handle the
message or you enabled AI flavour for replies:

* the companion's character sheet (archetype, name, traits),
* a short memory summary and a few recent events (written by the game),
* a compact world-state block: biome, time of day, weather, health levels, current task, nearby
  mob types, your held item,
* the last few lines of your conversation with that companion,
* your in-game name (set `"sharePlayerName": false` to send "the player" instead).

Not sent: your coordinates, other players' chat, chat with other companions, file paths, or
anything from outside the game. Provider retention policies:
[OpenAI](https://developers.openai.com/api/docs/guides/your-data) (not used for training by
default; abuse logs up to 30 days),
[Anthropic](https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data)
(not used for training; deleted within 30 days by default),
[Ollama local](https://ollama.com/privacy) (prompts stay on your machine).

## Credentials

* Put keys in an **environment variable** (`HEARTHBOUND_API_KEY`, or the provider's standard
  `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`) or in `config/hearthbound-secrets.json` in your game
  folder. Neither is part of this repository; `.gitignore` excludes secrets files.
* An example lives at [`docs/examples/hearthbound.json`](examples/hearthbound.json) and
  [`docs/examples/hearthbound-secrets.example.json`](examples/hearthbound-secrets.example.json).
* Keys are never logged, never sent to the client, and never included in prompts.

## Contract between the model and the game

The model must answer with JSON matching this schema (flat, so it works with every provider's
structured-output mode; nullable fields instead of optional ones):

```json
{
  "type": "object",
  "properties": {
    "reply":  {"type": "string"},
    "intent": {"type": "string", "enum": ["none", "follow", "wait", "come", "gather", "cancel",
                                           "give", "attack", "status", "recruit", "dismiss"]},
    "item":   {"type": ["string", "null"]},
    "count":  {"type": ["integer", "null"]},
    "target": {"type": ["string", "null"]}
  },
  "required": ["reply", "intent", "item", "count", "target"],
  "additionalProperties": false
}
```

Rules enforced **in Java**, not trusted to the model:

1. `intent` is checked against an allow-list; `item` is resolved against the real item registry and
   the list of things this mod can gather; `count` is clamped (1-256); `target` must be an entity
   the companion can actually see.
2. If the proposed intent is **accepted**, the task starts first, then the model's reply is shown.
3. If it is **rejected**, the model's reply is discarded (it may have said "Sure!") and a truthful
   refusal with the real reason is produced (scripted, in the companion's voice).
4. A **commitment guard** checks replies with `intent: "none"` for promises like "I'll go get
   wood". If found, the reply is replaced with a scripted line, so a companion never promises
   something it isn't doing.
5. Progress and completion messages come from the task state machine with real numbers, never
   from the model.

## Prompt design

* **Stable prefix first** (character sheet, rules, 2-3 rotating style examples, the JSON contract)
  so providers can cache it.
* **Memory**: a short rolling summary plus a list of salient events, both written by Java from
  verified game events (fought together, received a gift, was downed...).
* **World state**: compact key-value block built on the server thread.
* **Recent conversation**: last 6-8 lines with this companion only.
* **Anti-repetition**: the last few things this companion said are included with "don't repeat
  these"; Java also rejects replies that reuse a recent opener or are near-duplicates and falls back
  to a scripted line.
* **Prompt injection**: in-game text (item names, renamed mobs, signs, player names) is untrusted.
  It is placed inside clearly delimited data blocks, formatting codes are stripped, length is
  capped, and - most importantly - the model has no privileges: it can only propose intents from
  the allow-list, which Java validates against the real game.

## Failure handling

| Situation | Behaviour |
|---|---|
| Provider not configured / `none` | Scripted dialogue. |
| Timeout (default 12 s) or network error | Scripted reply; after 3 failures in a row the provider is paused for 2 minutes (circuit breaker) and a one-time hint is shown in chat. |
| Invalid JSON / refusal / truncated output | Scripted reply. |
| Player sends a new message while a reply is pending | The older request is cancelled; the new one includes both messages. |
| Companion dies, is dismissed, unloads, or the player leaves before a reply arrives | Reply is dropped. |

All network calls use `HttpClient.sendAsync` on background threads; the result is applied on the
server thread via `server.execute(...)`, after re-checking that the companion and conversation are
still valid. The game thread never waits for the network.
