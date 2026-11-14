<h1 align="center">Zeus Client</h1>

<p align="center">
  A client-side utility mod for Minecraft Java 1.21.11, built on Fabric.<br>
  About 178 modules, a polished in-game panel and a local control page in your browser.
</p>

<p align="center">
  <b>Version 0.9.9.0 (Beta)</b> &nbsp;·&nbsp; Minecraft 1.21.11 &nbsp;·&nbsp; Fabric &nbsp;·&nbsp; Java 21<br>
  <a href="https://discord.gg/GcbryFXgcR">Discord</a> &nbsp;·&nbsp; <a href="../../releases">Releases</a> &nbsp;·&nbsp; <a href="../../issues">Report a bug</a>
</p>

---

## Overview

Zeus runs entirely on your own client. It adds overlays, automation and tools on top of the game, and it controls nothing on a server. Press **Z** in a world, or type `/zeus`, to open the panel.

It is aimed at players who want one well-organised client instead of a pile of separate mods: everything is searchable, every setting has a reason to exist, and the whole interface can be moved, resized and recoloured.

> **Before you use it on a server:** servers decide what actually happens, and many ban these modules. Read the rules first. See [Servers and fair play](#servers-and-fair-play).

## Highlights

| Area | What you get |
| --- | --- |
| **Panel** | A fast, searchable panel with favourites, sorting, a cost indicator per module and two layouts: floating windows (default) or a full-screen panel. Text is drawn in Inter and stays sharp at any GUI scale. |
| **Combat** | Kill Aura with silent rotation and hit confirmation, Crystal and Anchor Aura, Mace Aura, Triggerbot, Auto Totem, Hover Totem, Trident Util and more. Every attacking module reports landed hits and the server's response time. |
| **Visuals** | ESP, tracers, name tags, X-Ray and Seed ESP, all projected from the game's real camera, so they stay correct in third person, freecam and while zooming. Targets that vanish leave a fading marker; targets off screen get an edge arrow. |
| **Finding** | Suspicious Chunks (with a clearance scanner), Base Finder, Chunk Scanner, storage, spawner and structure finders, and Goliath RTP area details. |
| **Tools** | Schematic Builder (Litematica and Sponge formats), waypoints, Auction House Tweaks, Spawner Protect, profiles per server, and a HUD editor with 27 movable widgets. |
| **Interface** | A custom main menu, intro film, loading screen and pause menu. All of it can be switched off in the settings. |
| **Control page** | `/zeus website` opens a page on your own PC that shows live status and lets you toggle modules and edit settings from your browser. |

## Install

1. Install **Java 21**. Check with `java -version`.
2. Install **Fabric Loader** for Minecraft 1.21.11 and start that profile once so the `mods` folder exists.
3. Download **Fabric API** for 1.21.11 (0.141.6 or newer) and put it in `.minecraft/mods`.
4. Download the latest `zeus-0.9.9.0.jar` from [Releases](../../releases) and put it in the same folder.
5. Start the game with the Fabric profile. The intro plays once (any key skips it), then the Zeus main menu appears.

The first time you enter a world, a short tour walks through the categories. Replay it with `/zeus tutorial`.

## Getting started

- **Z** opens the panel. Click a module's switch to turn it on, click its row to open its settings, and click the **None** chip to bind a key.
- **Panic** (bottom right of the panel) switches everything off at once.
- Settings are saved in `config/zeus.json`, waypoints in `config/zeus-waypoints.json`, and schematics in `config/zeus/schematics`.

### Commands

All commands are handled by your game and are never sent to the server.

| Command | Does |
| --- | --- |
| `/zeus` | Open the panel |
| `/zeus on\|off\|toggle <module>` | Switch a module |
| `/zeus list`, `/zeus panic` | List modules, switch everything off |
| `/zeus find <text>` | Search modules by name, description or alias |
| `/zeus settings <module>` | Print a module's current settings |
| `/zeus hud` | Open the HUD editor |
| `/zeus waypoint add <x> <y> <z> [name]` | Save a waypoint |
| `/zeus website [stop\|status]` | Start or stop the local control page |
| `/zeus tutorial`, `/zeus whatsnew` | Replay the tour or the update summary |
| `/zeus ai <request>` | Ask Zeus AI in plain words, e.g. `/zeus ai mine 10 iron then come back` |
| `/zeus ai tutorial` | Step-by-step AI setup: provider, key, connection test, first commands |
| `/zeus ai test`, `/zeus ai status` | Check the AI connection, see key, model and limits |
| `/zeus ai provider\|model\|endpoint <value>` | Switch AI provider (groq, gemini, openrouter, openai, deepseek, mistral, ollama, lmstudio) or model |
| `/zeus ai stop\|undo\|plan` | End a job, undo the last AI change, show the last plan |
| `/zeus ai models`, `/zeus ai again` | List the provider's models (click to pick), repeat the last request |
| `/zeus ai schematics` | List the schematics in the instance's `schematics` folder (Litematica's) with Build and Materials buttons |
| `/zeus ai build schematic <name>` | Gather the materials (chop, mine, craft, smelt) and build it 3 blocks in front of you; add `here` to restart at your spot |

### Zeus AI

Zeus AI runs on an AI provider with your own API key, or fully offline through Ollama or LM Studio. Groq and Google Gemini give free keys. Run `/zeus ai tutorial` in a world and it walks you through every step with clickable buttons. Start a normal chat message with **@** (for example `@mine 10 iron`) and it goes straight to Zeus AI, never to the server; the prefix can be changed in the settings. Press **J** to open chat with `/zeus ai ` already typed. Turn on **Chat window** in Zeus AI Functions for a small on-screen chat of your requests and its answers. The key is stored only in your local config and is shown masked in chat. Without a key, a built-in parser still handles simple commands like `on sprint` or `set killaura range to 4`.

### Zeus Crates
**Zeus Crates** on the title screen is where knives are unlocked. Everyone starts with the Stock karambit; every other knife (72 in all, nine types) comes out of one of six crates: Olympus, Tempest, Hades, Nyx, Poseidon and Titan. Each knife has a rarity: Mortal 60%, Heroic 25%, Divine 10%, Titan 4%, Olympian 1%.

- Opening a crate needs a key. Keys cost 30 Zeus tokens; you earn 1 token for every 5 minutes of active play (standing still earns nothing).
- A free random crate drops every 90 minutes of play.
- A knife you already own gives tokens back instead, more for rarer drops.
- Promo codes (Redeem tab) unlock free crates with their keys. Each code works once per player.

### Control page

`/zeus website` serves a page at `http://127.0.0.1:8765`, reachable only from your own computer. Paste the access token it prints in chat to connect. From there you can toggle modules, edit every setting, save presets, schedule changes and use an emergency disable-all.

## Modules at a glance

| Category | Examples |
| --- | --- |
| **Combat** | Kill Aura, Crystal Aura, Anchor Aura, Mace Aura, Auto Combo, Triggerbot, Aim Assist, Auto Totem, Surround |
| **Movement** | Flight, Speed, Step, Elytra Autopilot, Boat Fly, Parkour, No Fall, Safe Walk |
| **Player** | Auto Eat, Auto Tool, Chest Swap, Morph, Inventory tools |
| **World** | X-Ray, Fast Break, Schematic Builder, Tree Bot, Tunnel Miner, Spawner Protect |
| **Visuals** | ESP family, Seed ESP, Fullbright, Zoom, Freecam, Custom Crosshair, Aurora |
| **Base finding** | Suspicious Chunks, Base Finder, Storage, Spawner and Portal finders, Chunk Scanner |
| **Tools** | Auction House Tweaks, Waypoints, Chat Macros, Discord Webhook, Streamer Privacy |
| **HUD & Info** | Module List, Armor and Potion HUD, Keystrokes, Radar, Compass, Region Map |

Seed ESP deserves a note: it reads the real ore blocks in every chunk your client holds, and only uses the world seed to model chunks that have not been sent yet. In singleplayer it can generate those chunks ahead of time. On a server the seed is a model of vanilla worldgen and is only exact for a vanilla world with that seed.

## Servers and fair play

Zeus changes what your own client shows and sends. A server stays in charge of movement, combat and blocks, and can correct or punish anything it does not allow.

- **Screen-only modules** (ESP, X-Ray, Fullbright, Zoom, HUD) work anywhere.
- **Action modules** (Kill Aura, Scaffold, Auto Totem and similar) click and place like a player would, so they work as far as the server's own checks allow.
- **Movement modules** (Flight, Speed, Step and similar) move you on your side. Servers with movement checks will pull you back.
- Many public servers prohibit these modules and their anti-cheat will notice them. A ban is the server's decision. Use Zeus in your own worlds, or where the rules say it is allowed.
- Zeus does not try to disguise itself from anti-cheat. Scanners only read data Minecraft has already sent your client, so they cannot see unloaded terrain or anything a server hides.

## Build from source

You need Java 21.

```
Windows:      gradlew.bat build --no-daemon --console=plain     (or run build.bat)
Mac / Linux:  ./gradlew build
```

The first build downloads Gradle, Minecraft and the mappings, so allow a few minutes. The jar is `build/libs/zeus-0.9.9.0.jar`. Use that one, not the `-sources` jar.

## Status and known limits

Zeus is in **Beta**. It has been compiled against the real 1.21.11 jar and tested with logic tests and a software renderer, and some features are still being confirmed in everyday play. Mixins that fail to match are skipped, which means a module can do nothing instead of crashing the game.

- If the build fails, open an issue with the first red lines of the Gradle output.
- If a module does nothing, include its status text and the `Zeus` lines of `logs/latest.log`.
- Zeus is built for 1.21.11 and needs a rebuild for other versions.
- Overlays are projected onto the screen, so they are not occluded by world blocks and can drift slightly while sprinting.
- Suspicious Chunks and the other finders are heuristics. A natural structure can occasionally be flagged and a very large base can be reported as one find.

## Credits

- **Zlorbie788**: the client, its code, website and images.
- **mitko_pandata**: modules and feature ideas.
- **NikiTheFacter**: animations.
- References and inspiration: Meteor Client (base mod style), Litematica (schematic formats and workflow), Fabric, Anubis by 4ldenz (tracer and chunk-marker looks, aurora idea) and DonutSMP (context for the server helpers). A reference is not an affiliation.
- Typeface: Inter, under the SIL Open Font License.

## Licence

Zeus is released under the terms in [`LICENSE`](LICENSE). Parts of it derive from GPL-3.0 projects, so the notices in [`NOTICE`](NOTICE) must travel with the source when you redistribute it.

## Community

Updates, support and feedback are on the [Zeus Discord](https://discord.gg/GcbryFXgcR).
