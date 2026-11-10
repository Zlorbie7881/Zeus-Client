<<<<<<< HEAD
# Zeus 0.9.9.0

A thunder client for Minecraft **1.21.11** (Fabric, Java 21): about 178 modules, a Zeus main menu, an intro film, a full-screen storm panel, world scanners, waypoint tools, movement assistance, a schematic builder and a local website with a live control center. Press **Z** in a world (or type `/zeus`) to open the panel.

This README is the single document for the whole project. It replaces the 49 separate release-note, build-status and review files that used to sit next to it; everything they said is kept below (see "Version history" and "Third-party attribution").

**Client-side behaviour and server limits.** Zeus draws overlays and controls your local client. Multiplayer servers are authoritative for movement, combat and blocks, and can correct or punish what Zeus does. The world scanners only read chunk data that Minecraft has already delivered to your client; nothing here can reveal unloaded terrain, server-hidden blocks or players the server never sent. Check the rules of every server before you use a module on it (see "What a server accepts").

**Not run in real Minecraft.** Zeus 0.9.9.0 was compiled and logic-tested against hand-written stand-ins for the game's classes, never launched in a real Minecraft. Read "Honest limits" before you rely on anything.

---

## Contents

1. Setup, step by step
2. What is new in 0.9.9.0
3. What Zeus deliberately does not do
4. Version history
5. What a server accepts
6. Crystal Aura and Anchor Aura
7. The modules
8. The interface, HUD editor, dashboard and commands
9. Building it
10. Honest limits
11. Third-party attribution and licence

---

## Setup, step by step

1. Install **Java 21**. Check with `java -version` in a terminal; it must say 21 or higher.
2. Install the **Fabric Loader** for Minecraft **1.21.11** (from fabricmc.net) and start that profile once, so the `mods` folder exists.
3. Download **Fabric API 0.141.6+1.21.11** (or newer for 1.21.11) and put its jar in the `mods` folder (`.minecraft/mods`).
4. Unzip this project. The archive has exactly one top-level folder, `zeus/`. In that folder run `gradlew.bat build --no-daemon --console=plain` (Windows, or double-click `build.bat`) or `./gradlew build` (Mac / Linux). The first build downloads Gradle 0.9.5.1, Minecraft and the mappings, so give it a few minutes and an internet connection.
5. Copy `build/libs/zeus-0.9.9.0.jar` (not the `-sources` one) into the same `mods` folder.
6. Start the game with the Fabric 1.21.11 profile. When loading ends, the **intro film** plays (any key or click skips it) and then the **Zeus main menu** appears. The first time you enter a world a short animated tour shows every category; replay it with `/zeus tutorial`.
7. In a world press **Z** to open the panel. Click a module row to open its settings, click its switch to turn it on, click the **None** chip and press a key to give it a key. **Panic** (bottom right) switches everything off.
8. If something looks wrong: open **Zeus Customs** from the menu (or the panel in a world), go to the **Panel** page and check **Zeus main menu**, **Intro film at start-up** and **Intro animation**. The **Classic menu** link in the corner of the menu goes back to the game's own title screen.
9. If the build fails, send the first red lines of the Gradle output. If a module does nothing in game, send its Status text and the "Zeus" lines of `logs/latest.log`.

Settings save to `config/zeus.json`. Waypoints are in `config/zeus-waypoints.json`. Schematics go in `config/zeus/schematics` (Litematica's own `schematics` folder is scanned too).

---

## What is new in 0.9.9.0

0.9.9.0 is written up in five groups: the Z panel, fighting, seeing, finding, and tools and housekeeping.

### The Z panel
- **A new panel layout.** The panel you open with Z is rebuilt: a navy lightning header with the Zeus lettering, a left rail (Library: Active and Favourites; Modules: Combat, Movement, Player, World, Visuals, Base Finding, Tools; Client: Interface, Friends, Profiles, HUD & Info, Panel), and module cards on the right. Every module, page, setting, key bind, search, sort and favourite from before is still there.
- **Sharper text.** The panel draws its text with the bundled Inter font (five weights) in device pixels, so it stays crisp at any GUI scale and in small windows. The search box and text settings use the same font.
- **Cards.** Each card shows its icon tile, name, an info pill, description, cost chip in milliseconds, key chip, star, switch and a chevron that opens its settings across the full width. Settings flow into one to three columns depending on width.
- **All / On / Off filter, sort pill and a list/tiles toggle** sit above the module list. A new **Module cards** setting (Panel page and Interface) chooses two columns or one.
- **Profiles shortcut** in the header opens the Profiles page; the footer shows status, hints, how many modules are active and the Panic button; the Performance card shows FPS, a sparkline and the cost of Zeus itself.
- **The Windows layout is now the default**, rebuilt in the same look: rounded category windows with Inter text, a top bar with the medallion (without the sliding shine and sparks), search, sort and buttons, and a footer. A module's settings open in a Panel-style window with the same switches, sliders and chips as the Panel, with the description under the name. A config saved before this is switched over once; the layout button in the top bar switches back.
- **Animations are rendered ahead of time.** The lightning on buttons, cards and switches is no longer drawn from random lines each frame. `tools/anim/make_arcs.py` renders sprite sheets (an arc that circles a button, an arc that flows along a card edge, a burst for a switch) and the game only shows the right frame, tinted with your accent colour.
- **ESP drawn as smooth sprites, and no more zoom lag.** Every ESP line (box edges, brackets, tracers, the glint, the diamond on the target) is now one anti-aliased picture rendered ahead of time (tools/anim/make_strokes.py) instead of several flat rectangles, so a box costs about half as many shapes and a tracer about a tenth. Lines are cut to the screen, so a box that is far bigger than the screen when you zoom in on something close is no longer a handful of huge overlapping shapes, and a frame budget drops the dark outlines when a lot is on the screen at once. Name tags are drawn with the interface font at the real pixel size (a dark pill with a colour dot, the name and the distance), and the health bar is a smooth glowing bar.
- **Zoom no longer changes your FOV setting every frame.** Changing the setting makes the game redo its chunk culling each time, which is what made zooming stutter with many things marked. Zoom now works inside the renderer's own field of view (and the overlay reads the same number, so ESP stays lined up with any zoom). If that hook is ever missing, it falls back to the old way by itself.
- **Pictures that are scaled are now smooth.** The game samples its dynamic pictures with the nearest pixel, which made every stretched animation look jagged; the sprites are now sampled smoothly.
- **Tracers look like Anubis'.** Every tracer of every module (ESP, Player Detect, Item ESP, Block ESP, Villager ESP, the finders, Friends and the rest) now uses the Anubis look by default: a thin flat line at half opacity from the crosshair to the middle of the target, a line from the feet to the head of the target (a "stem", can be switched off), and a pale colour for each kind (players light grey, animals green, water animals blue, monsters red, ambient dark, friends mint). An optional setting colours by distance (red close, green at 60 blocks). A target off the screen gets a blinking triangle on a circle round the crosshair, as in Anubis. A target behind you shows only that arrow (no line), pointing the way you would turn, and it settles to one side when the target is straight behind instead of flipping. The old Modern, Beam and Classic looks are still in the style list.
- **Suspicious Chunks marks chunks like Anubis.** A flagged chunk is a flat translucent red tile on the Y=63 plane (purple for a likely mined geode), the chunks of one find are joined into one shape with a thin edge only round the outside and softly rounded corners, the tiles grow in from the middle of the group when found and shrink away when they stop being flagged, and a tracer runs to the middle of every separate area. The old Outline and Outline width settings are gone; the colour setting is now the tile colour.
- **The start screen is Zeus.** The red Mojang logo and its loading bar are replaced by the Zeus medallion and ring with the real percent of the game's resource loading under it (and a tip), whose ring hugs the medallion so it never touches the lettering, and which closes with the same flash as a world loading and then lifts to show the menu. The game still finishes loading exactly as before; if the Zeus screen cannot be drawn, the game's own is used. The switch is the "Zeus loading screens" setting.
- **The news scroll unrolls like a real one.** Opening it is a motion and not a fade: the roll rises while the bottom rod drops out of it on a spring (it swings a little past its place and settles back), the roll gets thinner as the paper leaves it (sixteen ray cast frames, tools/make_scroll_unroll.py), its bands loosen and fall away, the paper ripples while it moves, and the title band is written in at the end. Rolling it up runs the same motion without the swing.
- **Living clouds and a storm that closes the whole sky.** The three cloud banks on the menu no longer only slide sideways: each one slowly changes shape by fading to and from a twin picture (same big shapes, different details; tools/menu_overcast.py). When a storm forms (it now builds over 24 seconds), a heavy dark deck rolls in from the right across the whole sky and also changes shape, the banks thicken until the gap in the middle is gone, low ragged cloud races along under it, and the flashes light the deck from inside. Calm mode and the lighter performance modes keep the clouds still and skip the twins.
- **A new sound for the start-up intro.** The old one was a wall of noise. The new one (made by tools/intro_sound.py, synthesised, in time with the film) climbs three octaves with the ring while sparks come faster, pulls the air in just before the first bolt, and then gives that bolt a hard crack, a falling boom and a long rolling thunder in a big room, with three smaller strikes after it, a bell as the ring closes and a warm chord that fades with the picture. The game no longer adds its own thunder clap on top of it.
- **The Profiles window is a button.** In the Windows layout the Profiles category is gone from the grid and a Profiles button sits next to the Sort button (on its right); it opens the Profiles settings in the same window as every other module.
- **Menu animations rendered ahead of time.** The lightning on the stone plates (two crackling arcs that chase each other round the edge, with forks and sparks), on the round buttons and the click flash (shock waves and bolts) are sprite sheets made by tools/anim/make_plate.py. The game only picks a frame; nothing is drawn as live line segments any more.
- **Fewer, cleaner ESP settings.** The ESP family (about 30 modules) lost the settings that only fine-tuned looks (glow, fade, box size, reveal, pulse, tracer glint, tracer distance, label size, and in ESP also fill alpha, glow outline, health heart, health format, max entities and ignore self). They keep their defaults. Corners style is now bright corner brackets with a hairline and a whisper of fill, and tracers are slimmer and fade in towards the target.
- The game window is called "Minecraft 1.21.11 - Zeus Client", also in the task bar tooltip.

### Fighting
- **Mace Aura** (Combat). In the air near a target it takes the mace out, hits when the attack bar is full, keeps doing that for as long as there is a target and puts your slot back after you land. A smash needs a fall of more than 1.5 blocks; the module waits for a little more than that, skips targets that are still flashing from a hit and uses the same hit tracking and silent rotation as Kill Aura.
- **Mace Damage** (Combat). Makes a hit with a mace count as a fall from a great height (default 22 blocks, about 64 damage, the way Wurst's MaceDMG does it): just before the hit it sends position packets that go up and come back down inside the distance the server allows for one move, so the server adds the drops up as a fall. It only works when you hold a mace, only when the hit can land, and keeps to free air above you. No Fall, Anti Hunger and Fake Lag leave those packets alone.
- **Hit confirmation.** Every swing is written down before it leaves, and the server's answer (the damage packet, the hurt flash, an entity being removed) is matched back to it. Kill Aura stops swinging at a target after four unanswered hits, and every module that attacks now shows landed/total and the server's confirmation delay, which is the real limit on hit rate. A target that is out of the server's reach is visibly ignored instead of silently wasting packets.
- **Silent rotation in Kill Aura.** A look packet goes to the target immediately before each hit, so the server sees you facing what you hit while your camera stays where you put it. Multi Aura, Auto Combo and the crystal aura use the same approach.
- **Hover Totem** (World) moves a totem from any open container to your offhand (or the selected hotbar slot) by hovering it. A new mixin reads the hovered slot.
- **Trident Util** (World) takes the trident out when you use anything, holds the riptide long enough to count, gives your sword back, and can riptide you upward out of a fall.
- **AH Sniper, rebuilt.** It reads every listing on the page (name, count, price from the lore, several price formats), pages through the auction house, refreshes, and can run a search command to put the auction house on the item you are hunting. It buys the biggest discount rather than the cheapest thing, can take a ceiling from the prices the client has learned for that item (buy below market by a percent you choose), can shift-click for servers that buy instantly, and after confirming it counts the item in your inventory before it counts the money as spent. Failed attempts are remembered for three minutes so nothing is clicked in a loop, a confirm window that asks more than your remaining budget is refused, and both limits still switch it off by itself.

### Seeing
- **Visuals that follow the real camera.** Every overlay (ESP boxes, name tags, tracers, edge arrows, ghost markers, block outlines) is now projected from the game's own camera and the renderer's own field of view instead of your eye and the FOV option. That fixes third person (the camera is behind you), freecam, a tilted camera, sprinting and zooming all at once. Tracers also gained "My body" as a start point, which looks right in third person.
- **ESP cannot lose a target.** Invisible entities are marked, a marked entity that disappears (vanish, teleport, despawn, out of range) leaves a fading marker at its last known spot with a countdown, and anything behind you or off the edge of the screen gets an arrow on the edge with its distance instead of being dropped.
- **Modern tracers.** The new default draws a thin tail that swells into a bright head, a glint that travels along the line, a diamond bracket on the target and an optional distance pill. Beam gives a soft glow around a white-hot core; Classic is the old clean stroke. Every ESP shares the renderer, so blocks, items, storage, spawners, clues and amethyst all improved together.
- **Zoom, rebuilt.** Smooth slide in and out at your frame rate, mouse sensitivity scaled while zoomed (so a high zoom can still be aimed), an optional switch to first person while zoomed, and both the field of view and the sensitivity restored exactly when it switches off.

### Finding
- **Suspicious Chunks and the clearance scanner are one module.** Suspicious Chunks now also counts plants that should have grown and did not (kelp short of the top, ripe crops, tall cane, long vines). A chunk that was never ticked shows nobody has been near, and a chunk inside a ring of untouched ones is reported as a cleared area. The separate Sus Chunks (Clearance) is gone; Advanced Base Finder and Tunnel Base Finder use the same evidence.
- **Chunk Scanner** (Scanner) counts one block per chunk across the loaded chunks and lists the richest ones with a direction and a distance.
- **Discoveries only after a base.** The "ZEUS / DISCOVERIES" card no longer appears for any chest you walk past. It needs either a base the finders have already recorded here, or a spot that is unmistakably a base (four or more containers, or two or more with machines or displays). Its footer says which base was found and whether it is intact or looted.
- **Goliath RTP** now reports the area, the size in grid cells, the distance to each edge and the Goliaths next door, and GoliathGrid gained regions, cells, edges, neighbours and per-region listing.

### Tools and housekeeping
- **Spawner Protect** (World) refuses to break spawners (and containers if you choose) and lists nearby spawners that are too bright to spawn.
- **Accessibility.** `/zeus find <text>` searches every module by name, description and alias from the chat box, and `/zeus settings <module>` prints the current value of every visible setting, so the whole client can be read and changed without the mouse.
- **New intro film.** The Zeus logo's ring draws itself from 0 to 100 percent and lights the head brighter as it fills. When the circle closes, a thunderstorm breaks in the dark around the logo, lighting the clouds behind forked bolts, and the screen settles into a steady blue glow. About 5 seconds, 16:9, any key or click skips it.
- **New main menu, built from your reference picture.** The Zeus medallion and lettering sit above six stone plates (Singleplayer, Multiplayer, Zeus Customs, Settings, Realms, Exit) between two pairs of columns, under a full moon and a starry sky. Banks of storm cloud drift to the left for ever, with new ones always coming in from the right, and fog rolls along the ground in waves. The four round buttons in the bottom left are Language, Accessibility, Zeus Settings and Credits. In the bottom right is a 3D news scroll: closed it is a rolled parchment that turns slowly, a click unrolls it, and the wheel moves the text. The intro fades to black and the menu fades in. The pictures are made by `tools/menu_layers.py` (from `tools/menu_reference.jpg`), `tools/menu_clouds.py` and `tools/make_news_scroll.py`. Every minute or two the menu can turn stormy: after about a minute a thunderstorm rolls in (dark clouds across the top, rain, lightning with thunder), and two minutes later it clears again; the **Storms on the main menu** setting turns it off. While a singleplayer world loads, the intro's ring draws itself round the medallion with the game's real percent and no lightning; while you join a server you see the menu sky with the logo and only the Cancel button (**Zeus loading screens** turns both off). The storm pictures are made by `tools/menu_storm.py`. Hovering a plate sends two arcs of lightning chasing round its edge and a click sends a flash through the whole menu before the next screen opens; in a storm the medallion flashes with each clap, sparks run along its rim and the rain lands on it and splashes; the Language, Accessibility and Zeus Settings buttons animate on hover (the globe turns, the figure waves, the bolt crackles) and the Credits scroll unrolls a little. In December (**Seasonal menu**, or Always to see it now) snow falls on the menu and a storm becomes a blizzard. The top right shows the date and the time (**Date and time on the menu**, **24-hour clock**). The plates also lift with a small spring and lean towards the pointer; a shine sweeps over a plate as the pointer arrives (the letters flash white as it passes), a ripple spreads from where the pointer came in, embers rise off the plate and a line of light grows along its lower edge, and the round buttons jump, send out a ring and have a spark circling their rim. The menu has sound (**Menu sounds**: a soft rumble when you point at a plate and a crack of lightning on a click; **Menu ambience** with its own volume: low wind and soft rain, louder in a storm, wind alone in the snow; the sounds are made by `tools/menu_sounds.py`). In a storm the moon is swallowed by the cloud, which builds from the right, and is the first thing to come out again as it clears. The game window's icon in the task bar and the title bar is the Zeus medallion (**Zeus window icon**, applied when the game starts). The server list, world list, options, disconnect and error screens get the menu sky when no world is open (**Menu sky behind game screens**), and when a world finishes loading the ring flashes and the screen fades into the world instead of cutting. The pause menu is the logo above the buttons, with the buttons in the same Greek lettering as the menu (**Zeus pause menu**; the lettering is drawn by `tools/greek_font.py`). The world list and the server list are restyled as rows of stone plates, with stone plate buttons and Greek titles on those screens, as a test (**Stone plate lists (test)**: switch it off to get the game's own lists back; to remove the test completely, delete `ZeusSkins`, `ListPlates`, `ScreenSkinMixin`, `ListPlateMixin` and `PauseTitleMixin` and their lines in `zeus.mixins.json`). The menu rain is lighter (two layers and a smaller picture), the medallion is no longer rained on but reacts to each lightning strike (it is lit from the side of the bolt, an arc jumps toward the bolt, and a shock ring and a few sparks follow), and a short tip such as "Press Z to open the panel" sits under the loading ring (**Loading tips**). In winter the sky of the menu gets an aurora, a green and violet curtain with rays that comes and goes with the snow and fades out when a storm arrives (**Menu aurora**).
- **Removed as filler:** Atmosphere, Jump Circles, Weather Notifier and Self Trap.
- **Honest note:** compiled, not launched in a real Minecraft. The new panel was checked with a software renderer, and the sources were compiled against the real 1.21.11 jar (with stand-ins only for third-party libraries). The packet hook names were checked against the real 1.21.11 mappings with `javap`, and every mixin target uses `require = 0`, but the behaviour in game is unproven.

---


## What Zeus deliberately does not do

A few things Zeus leaves out on purpose:

- Server-specific shop and purchase automation, auto-buying, junk selling, disconnect policies and anti-cheat related behaviour. Since 0.9.6.0 the AH Sniper in Auction House Tweaks can buy by itself, but only when you arm it, only for the items and prices you typed, and only up to its spending and purchase limits.

**A short caution.** Many of the modules above (aimbots, triggerbots, flight, speed, Reach, Auto Clicker, Boat Fly on multiplayer, staff and vanish detection) are against the rules of most multiplayer servers and are noticed by anti-cheats. A ban is the server's call. Use them in your own worlds or where the rules explicitly allow it.

---

## Version history

Only the current release is written up here. Earlier releases are not listed.

### 0.9.9.0

Hit confirmation and silent rotation, ESP that keeps its target and tracers that follow the real camera, a rebuilt AH Sniper and Zoom, Hover Totem, Trident Util, Spawner Protect and Chunk Scanner, Suspicious Chunks with the clearance scanner, Discoveries that waits for a real base, a fuller Goliath RTP, and /zeus find and /zeus settings. See "What is new in 0.9.9.0".

---

## What a server accepts

Zeus cannot make a server do something it does not allow. This is how each kind of module behaves.

**Works the same anywhere, because it only changes your screen:** ESP, Block ESP, Seed ESP, X-Ray, Hole ESP, Light Overlay, Trajectories, Breadcrumbs, Fullbright, Time Changer, Weather Changer, Aurora, Atmosphere, Chunk Borders, Block Outline, Suspicious Chunks, Waypoints, Zoom, Durability Alert, every Interface module (Watermark, Module List, Info, Armor HUD, Potion HUD, Keystrokes, Target HUD, Radar, Compass, Item Counter).

**Sends normal actions, the server checks them:** Kill Aura, Crystal Aura, Anchor Aura, Auto Clicker, Bow Spam, Bow Aimbot, Auto Totem, Auto Armor, Auto Weapon, Auto Tool, Auto Eat, Auto Fish, Exp Thrower, Chest Swap, Auto Replenish, Surround, Self Trap, Auto Trap, Hole Filler, Auto Web, Self Web, Scaffold, Nuker. They click and place the way you would, so they work as far as the server allows normal clicks.

**Moves you on your side, the server may undo it:** Flight, Speed, High Jump, Step, Low Gravity, Air Jump, Spider, Jesus, Long Jump, Safe Walk, **Parkour** (new), Fast Climb, Reverse Step, Elytra Boost, Anti Void. The server sees where you end up. On a plain server this works; a server with movement checks will pull you back.

**Limited by the server on purpose:**
- **Reach** extends your own aim. Servers refuse block and attack distances much beyond normal (about 5.5 and 4 blocks).
- **Fast Break** breaks blocks faster on your screen, but the server decides when a block is really broken. About 1.4x is accepted; more and the block comes back. "No pause between blocks" is a real gain.
- **Resize** makes you look and feel tiny or huge on your side only. Tight gaps can still push you back.
- **Hitboxes** helps your aim; the server still checks the real distance.
- **No Fall** tells the server you landed before a fall gets long enough to hurt.
- **Anti Knockback** ignores the push the server sends when you are hit. The server still thinks you were pushed, so you may be pulled back a little.

**Removed because they cannot work on your side:** God Mode, Water Breathing, Fire Resistance, Haste, Damage Boost, World Control (time and weather on the server), and the old Quick Actions (game mode, heal, teleport, clear items). The server owns all of those.

### About being "detected"
Zeus does not hide anything. On a server with an anticheat, many of these modules will be noticed and can get you kicked or banned, and most servers forbid them in their rules. I did not build anything to disguise Zeus from an anticheat. Use it where it is allowed (your own worlds, your own server, servers that say yes). The one exception is a tiny convenience in singleplayer: while a module keeps you in the air, Zeus dips you a tenth of a block every second or so, so the game's own "Flying is not enabled on this server" check does not kick you out of your own world. It does nothing on a multiplayer server.

---

## Crystal Aura and Anchor Aura

**Crystal Aura** works in a loop, one step per tick: if there is no good spot and **Place obsidian** is on, it puts obsidian next to the target; it places an end crystal on the best obsidian or bedrock spot (most estimated damage to the target while your own estimated damage stays under **Maximum self damage**, and **Anti suicide** keeps it from ever killing you); then it breaks crystals that would hurt the target by at least **Minimum damage**. The estimate uses the real explosion rules (distance, line of sight, armor) and ignores enchantments and potions.

Settings: target range, place range, break range, place delay, break delay, minimum damage, maximum self damage, anti suicide, place obsidian, obsidian delay, break only useful crystals, pause while eating, swing arm, targets.

**Anchor Aura** places a respawn anchor next to the target, charges it with glowstone (1 to 4) and sets it off with a slot that does not hold glowstone. It does not run in the Nether. Settings: target range, place range, charges, delay, minimum damage, maximum self damage, anti suicide, swing arm, targets.

You need the items in your hotbar. Zeus does not take them from your inventory for you.

---

## The modules

*Zeus has about 160 modules. The lists below are the original grouping; the additions are listed right after them.*

**Combat:** Kill Aura, Crystal Aura, Anchor Aura, Auto Clicker, Reach, Anti Knockback, Hitboxes, Auto Totem, Auto Armor, Auto Weapon, Bow Spam, Bow Aimbot, Surround, Self Trap, Auto Trap, Hole Filler, Auto Web, Self Web.

**Movement:** Flight (steers your own movement: jump to rise, sneak to sink, no permission needed), Speed, High Jump, Step, Low Gravity, No Fall, Auto Sprint, Air Jump, Anti Void, Auto Jump, Auto Walk, Fast Climb, Spider, Jesus, Long Jump, Safe Walk, **Parkour** (new), Reverse Step, Elytra Boost, Sneak, Scaffold.

**Player:** Resize, **Morph** (new), Auto Eat, Auto Tool, Auto Respawn, Exp Thrower, Chest Swap, Auto Fish, Anti AFK, No Bad Effects.

**World:** X-Ray, Fast Break, Fast Place, Nuker.

**Render:** ESP, Block ESP, **Seed ESP**, Hole ESP, Light Overlay, Trajectories, Breadcrumbs, Fullbright, Time Changer, Weather Changer, **Aurora**, **Atmosphere**, Chunk Borders, Block Outline, **Freecam, Free Look, Butterfly Knife & Karambit**.

**Scanner:** Suspicious Chunks, **Base Finder, Anti Vanish, Storage ESP, Spawner ESP, Amethyst Finder, Kelp Finder, Crop Finder, Ancient City Finder, Trial Chamber Finder, Portal Finder, Farm Finder, Bed Finder, Bee Finder** (new).

**Utility (new):** **Discord Webhook** (new), Waypoints (saved in `config/zeus-waypoints.json`, marks where you died), Zoom, Auto Replenish, Durability Alert.

**Interface:** Watermark, Module List, Info (now with ping), **Armor HUD, Potion HUD, Keystrokes** (with clicks per second)**, Target HUD, Radar, Compass, Item Counter** (new). HUD boxes in the same corner stack automatically, and every one has a **Corner** setting.

### More modules by category
- **Combat:** Sword PvP Coach, Attribute Swap, MultiAura (single-player), Triggerbot, W Tap, Jump Reset, Shield Breaker, Mace Swap, Key Throw, Smooth Aim Assist; Crystal Aura and Anchor Aura now coordinate with each other.
- **Movement:** Boat Fly, Boat NoClip, Advanced Elytra Autopilot (id `elytrawaypoint`), Air Stuck.
- **World:** Tree Bot (single-player), Tunnel Miner (single-player), Area Clearer (the old Nuker), Schematic Builder, Goliath RTP.
- **Visuals:** Item ESP (item:colour list), Villager ESP, ESP shapes / reveal / friends, Nametags, Target ESP, Jump Circles, Custom Crosshair, Low Health Warning, KnifeSwing with Replace any sword.
- **Base Finding:** Suspicious Chunks (mined geode, crop, bee-nest clues), Base Finder, Advanced Base Finder, Tunnel Base Finder, CaveFinder (id `caveesp`), Storage ESP, Media Base Finder with estimated base area.
- **Tools:** Anti Vanish (tab-list removal hook), Auction House Tweaks (Auction Bot, Market Hunter, price graph), Staff HUD (rebuilt), Weather Notifier, Block Notifier, Coord Snapper, Chat Macro 1-3, Streamer Privacy, Zeus Brain, Schematic Library, Waypoints with coordinate entry.
- **HUD & Info:** HUD Editor, Region Map, Fake Lag Monitor (monitor only).
- **Interface:** Server Switch Persistence, Remember enabled modules, tutorial.

### Notes on a few modules
- **Seed ESP** builds an ore pattern from a world seed (the box at the top of its page, set to `6608149111735331168`) and draws the veins around you through walls with the same renderer every other ESP uses, so a box is the real shape of the block and sits exactly on it. Every ore has its own switch and colour, the vein amount and vein size can be scaled, height and range are limited, and extra blocks can be picked from the block menu. **Every chunk your client holds is read block by block, so a box is the real ore block, exactly where it is**: nothing is ever drawn in the air or on plain stone, and an ore you mine is gone within a couple of seconds. Truth and guesses never share a chunk — the seed is used only for chunks your client has no terrain for (`Seed guesses`), and those boxes are drawn dimmer, start with `~`, and never go higher than **Predictions above me** over you. To see them, set **Range** past your render distance, since a chunk the game has already sent is always drawn from its real blocks. In a singleplayer world, **Generate ahead** replaces the guessing for those far chunks with the real thing: it hands the chunks inside **Range** that your client has not been sent to the world's own generator, and reads the ore blocks out of them, so the boxes are vanilla worldgen for this seed rather than a model. It really generates those chunks, so they are written to your save, and it works one chunk at a time with progress in the status box. On multiplayer, the generator cannot run on the client — the server controls worldgen — so **Generate ahead** is singleplayer only and will remind you with a toast if pressed in a server world. The prediction/model path is used on multiplayer, with the **Predictions above me** clamp keeping guesses grounded. **Remember per world** keeps one seed for every save (by its folder) and every server (by its address) in `config/zeus-seeds.json`, so coming back to one puts its own seed back by itself; **World seeds...** opens a page with every remembered world, where a click uses that world's seed and **Forget** drops it. The **Nearest veins** box lists, for each ore you have switched on, the closest vein with its distance, an eight-point direction (N, NE, E...), and its coordinates, preferring a real block over a `~` guess. It also shows the ore counts of the last frame and says which world's seed is in use. Vein heights cluster around each ore's real sweet spot (diamond near Y=-58, coal near Y=96) instead of being spread flat over the whole band. A client is never told a server's seed or worldgen, so the pattern is a model of the vanilla overworld: it is exact only for a world really generated with that seed and default generation, not for a server with another seed, a datapack or a custom generator.
- **Time Changer and Weather Changer** only change what your screen shows. The server keeps its own time and weather, and your screen holds yours every tick.
- **3D knives**: all 20 knives are real 3D models with 6 keyframed animations each (draw, two inspects, two slashes, stab; unique flourishes per knife). The website Knives page has a WebGL viewer (js/knife3d.js, data in web/knives3d.json). In the game the light models (knives/knives_lo.json, 200-420 triangles, flat shaded by ui/KnifeMesh.java) replace the pixel knives; the setting "3D knife models" turns it off. Original models, not Valve assets. Regenerate with `python3 tools/knives/make_knives.py`. The butterfly and the karambit are detailed hero models (tools/knives/pro.py) with authored clips (tools/knives/pro_moves.py): in the game they play as the Studio tricks, on the website each knife shows its own animation buttons.
- **What's new screen**: after an update Zeus shows one screen with the highlights of the new version, once (a new install gets the tutorial instead). `/zeus whatsnew` shows it again. The text is the ITEMS list in ui/ZeusWhatsNewScreen.java; update it for every release.
- **Website for phones**: css/polish.css (loaded last) restyles the site with flat surfaces and one accent, and fits every page to a phone: the hero stacks, buttons are full width, filter rows scroll sideways, text fields are 16 px so iOS does not zoom, and the helper opens as a bottom sheet.
- **Website fixes**: the home headline reads "A cheat client for Minecraft 1.21.11." again, and the Source code page works when the site is hosted: its data now lives in web/codebundle (it used to be web/source, which clashed with the /source page). tools/make_source_manifest.py writes there; run it on every release.
- **A new intro film for the Z key** (five seconds, 1920 x 1080 as an mp4, and in the game as pictures plus a sound): it starts from pure black and is built around one realistic lightning strike. A storm that you only see by its lightning (sheet lightning and two distant bolts that sit behind the nearer clouds), then a leader grows down out of the cloud in steps, forking as it comes, while streamers rise from the medallion to meet it. The return stroke hits the top of the Zeus medallion with re-strikes: the bolt has a crooked main channel, forks that keep heading downward, forks of forks and channels that spread under the cloud, with fine filaments, a thin white-hot core that varies in brightness along its length, and a soft blue glow. The flash lights the clouds along the bolt, a shock wave blows them open and the medallion appears in the light. Two arcs of electricity run round the rim from the point of impact and close the ring (a comet keeps running along it, and the ring never touches the lettering, which sits below it), the "Zeus Client" lettering is revealed from the middle outwards, with nothing drawn across it, a last shine crosses the logo, and the camera pushes in on a flash. It uses the Zeus logo and nothing from Minecraft; the sound is made for it and kept soft and clean (distant thunder, a quiet moment before the strike, a deep boom, a warm pad and two chimes), and the Intro film sound setting switches it off. Any key or click skips it. It follows the Intro animation setting (Video, Video once, Lightning, Quick, Off), is skipped in Calm mode because of its flashes, and can be shown from Zeus Settings with Play panel intro. It is made by tools/panel_intro.py.
- **The logo on the main menu is a button**: it swells a little when you point at it, a ring draws round the medallion, a small label says it opens the settings, and a click opens Zeus Settings.
- **The Windows layout** (a floating window for every category) is a little smaller, so more fit on the screen. Windows can no longer lie on top of each other: when you drag one against another it stops at the edge (or goes round it), a window that grows when you unfold it pushes its neighbours aside, and a window that is moved out of the way glides to its new place. A dragged window lines up with the screen edges and with the edges of the windows beside it, and lifts a little (a bigger shadow and a brighter edge) while you hold it. The Tidy button still puts every window back into the neat grid.
- **Aurora** draws northern lights in the night sky of your world: several nested curtains that fold and drift, with sharp rays, a pink fringe along the lower edge, colours that change with height (green, then teal, blue and violet, and red at the top in a surge) and a surge every minute or two when the sky wakes up (brighter arcs, rays shooting up, a corona overhead). It is a rebuilt, much richer take on the aurora idea in the Anubis mod (it keeps the idea, not the code). It is drawn on top of the game picture from the camera's own view, so it turns with you; it is cut along a skyline measured from the heights of the land around you, so it goes behind hills and trees, and it fades in at night, out at dawn and in rain, and when you are under a roof. Settings: colours (five schemes), brightness, curtains, height, direction, arc length, speed, surges, when to show it, hide behind land, and quality (Zeus lowers it itself when your frame rate is low). Because it is drawn over the picture, an entity or your hand in front of the sky can be tinted a little by the lights, and the skyline is an estimate.
- **Atmosphere** puts a real night-sky picture over the sky of your world (a diagonal Milky Way, big folding northern lights in green, teal, blue and violet, and dark textured clouds framing the view) and brings it to life: the aurora curtains sway and flow, the clouds drift and swell, the stars twinkle, and a new shooting star falls across the sky at random moments (never a frozen one). The picture was taken apart ahead of time (the sky without its stars and its shooting star, two layers of stars, a glow layer and a map of where the lights and clouds are; the pieces are in `assets/zeus/textures/atmosphere`), so in the game it is only a few hundred textured rectangles and no per-pixel work. It is fitted to the real camera, so it turns with you, matches the picture exactly when you look straight up, and continues as a mirror of itself towards the horizon; it is cut along the same skyline as Aurora, so it goes behind hills and trees, and it fades with day, rain and roofs. Settings: brightness, aurora motion, twinkling stars, shooting stars (off to often), detail (Zeus lowers it itself when the frame rate is low), when to show it, and hide behind land. Use it instead of the Aurora module, not together. It is checked in a software render of the real classes, not yet run inside Minecraft; away from the zenith the picture is fitted with the local scale and turn of the camera, so it bends a little at the edges of the view.
- **Zoom** changes the field of view the renderer uses, so very high zoom is limited only by the setting (up to 8x).
- **Radar** shows players (white), monsters (red), animals (green) and items (yellow); it can turn with you or keep north up.

---

## The interface

- **Panel:** one full-screen window with a sidebar, module rows that open to show their settings, search, an Active page and a Panel page for the look.
- **Windows:** a floating window per category. Left click switches a module, right click opens its settings, click a header to fold a window and drag it to move it. Positions are saved.
- Switch layouts with the button next to the close button, or on the Panel page. Colours, intro (Video, Video once, Lightning, Quick, Off), the main menu switch, thunder sound and messages are on the Panel page too.

### HUD editor
Run `/zeus hud` or open Interface settings → **Open HUD editor**. Drag a widget (it snaps to edges, centre lines and other widgets), scale it with the mouse wheel (0.50x-2.50x), nudge with the arrow keys, Tab to the next widget, **R** reset, **H** hide, **U** undo, **G** grid, **S** snapping, **Esc** done. The floating panel lists every widget with a switch and edits position, scale, opacity and accent colour. Positions are saved under `hudLayout` in `config/zeus.json` and travel with profiles.

### Local web dashboard
This is the **Zeus website**. `/zeus website` (or switching on the **Web Dashboard** module) serves it on `http://127.0.0.1:8765` (this computer only; change the port in the module). On the Settings page paste the access token (click it in chat, or use *Copy the access token*). It shows live status, switches modules, edits every setting, saves presets, has an emergency disable-all, auction-alert settings and a scheduler for delayed module actions. `/zeus website stop` ends it.

### Server Switch Persistence
Interface → **Server Switch Persistence** chooses what comes back after you change server: Safe only, None, Everything (default) or Customize.

---

## Building it

Needs Java 21. In this folder:

- Windows: double-click `build.bat`, or run `gradlew.bat build`
- Mac / Linux: `./gradlew build`

The jar is `build/libs/zeus-0.9.9.0.jar` (see "Setup, step by step" above). Settings save to `config/zeus.json`.

### Release checklist (every update)
1. Raise the version in `Zeus.VERSION`, `fabric.mod.json`, `gradle.properties` and `defaults.version` in `src/main/resources/assets/zeus/web/site.json`.
2. Add the release to `tools/site/timeline_data.py` (`TIMELINE` and `EXTRA` with the modules and settings it touched) and any new questions to `tools/site/help_*.py`.
3. Refresh `features.json` from the game's module list (`FeatureCatalog.json()`, also served at `/api/features` by the in-game site), then run `python3 tools/site/features_extra.py <web folder>` and `python3 tools/site/gen_site_data.py <web folder>`.
4. **Last step before building: `python3 tools/make_source_manifest.py`.** It copies the current source into the website's Source code page and marks what changed since the previous release (it compares with the bundle already in the folder, so keep that folder between releases). `tools/build_website.py` refuses to build if the bundle is for another version.
5. Build the jar, then `python3 tools/build_website.py --url ... --jar build/libs/zeus-<version>.jar --github <repo link> --out website`.

### Commands
`/zeus` opens the panel, `/zeus toggle|on|off <module>`, `/zeus list` and `/zeus panic` also work, as do `/zeus hud` (HUD editor), `/zeus tutorial` (replay the tour), `/zeus waypoint add <x> <y> <z> [name]` and `/zeus website|stop|status` (local dashboard, see "The interface"). They are client commands: they are handled by your game and never sent to a server. Every module also has its own unbound key (click the **none** chip on its card).

---

## Honest limits

- **Zeus 0.9.9.0 has not been run in a real Minecraft.** It was checked against stand-in classes and logic tests only, so a wrong class or method name for 1.21.11 can still stop your Gradle build or make a mixin skip itself. Tell me what you see and I will fix it.
- **The website helper is not an AI.** It is a search engine over this site's own content plus rules for common question shapes. It answers what is written down (modules, settings, FAQ, commands, timeline) and says so when it cannot find something. It cannot browse, run code or answer general knowledge. The "thinking" is an animation, not computation time.
- **References are credited from what I know was used.** Meteor Client is listed for the base mod style as you asked; Fabric, Litematica, the fonts and DonutSMP are listed for what they are used for. Correct the list in `site.json` (website) and `ZeusCreditsScreen.java` (in-game) if it is wrong. The root `NOTICE` file still carries the licence attribution that parts of Zeus require.
- **The estimated base area is a search area, not a location.** It comes from the Goliath grid cell, which is inferred from coordinates (not a server shard id) and can go stale if the server changes its map. Exact coordinates appear only when the player's body is loaded by your client.
- **Staff detection only reads what the server sends.** Ranks, markers and team prefixes are matched against tab text and teams; a server that hides or renames them, or a vanished staff member the server never reports, will not show. A match is a confidence, not proof. The "watching" alert relies on game-mode packets the server chooses to send.

- **Everything is drawn on the screen, not in the world.** ESP boxes, tracers and markers (including the Suspicious Chunks tiles, which are flat shapes on the Y=63 plane projected onto the screen, so unlike Anubis they are not hidden by or sorted with the world's own blocks) are projected onto the screen from your field of view, so they can drift a little while you sprint (Freecam and Free Look follow the camera).
- **Cave ESP shows open space, not "caves" as the game defines them.** A large room you dug yourself looks like a cave too. Chunks your game has not loaded are not scanned.

- **Mixins that miss are skipped silently.** Anti Knockback, Hitboxes, X-Ray, the glow outline, Morph and Weather Changer use small mixins. If a name is wrong the mixin is skipped and that module does nothing instead of crashing.
- **Weather Changer and Time Changer use the game's client-side time and weather values.** 1.21.11 changed how the clock works, so if the sky does not follow, tell me what you see.
- **X-Ray removes blocks from the chunk meshes through a mixin**, one on the game's chunk view and one on Sodium's / Embeddium's. I have not seen it run in a real game. The Status box on the X-Ray card tells you what happened; if it says the hook did not run, send me that text and the game log. Fullbright and X-Ray ore lighting now use a light hook (with night vision as an optional second method).
- **The title-screen swap is a mixin on the game's screen switch** (with a per-tick check as a backup). If both fail you simply get the normal title screen. The menu does not have the game's language and accessibility buttons or Realms.
- **The intro film is 1024 x 576** (16:9, 40 frames a second, 200 pictures in `intro/`). A sharper film gives a sharper intro.
- **Screen sizes:** the panel and menu are drawn for 16:9. Other shapes work, and very small windows (GUI scale 4 on a small screen) are cramped.
- **Morph is visual only, and only shows where the game draws your own body** (third person and the inventory preview). Mobs that depend on being in water or on special state (squid, dolphins, guardians) are drawn in their resting pose. If a mob cannot be copied, you get a toast and nothing changes.
- **Crystal Aura and Anchor Aura are an estimate.** The damage numbers ignore enchantments and potion effects.
- **ESP 2D boxes and tracers use the game's FOV setting,** so they can drift a little while you sprint.
- **Suspicious Chunks is still a heuristic.** It is stricter now, so a modded or heavily edited structure can slip through, and a very big player base can still be reported as one.
- **Finders only see chunks your game has loaded** (your render distance), and Storage ESP shows block entities as the game reports them.
- Some modules add a hidden attribute modifier named `zeus:...` or a hidden night-vision effect to **your own copy** of the player. They are removed when you switch the module off or leave the world, and Zeus tops them up every few ticks because the server sometimes replaces them.
- Zeus will not work on other Minecraft versions without being rebuilt.

---

## Licence

Zeus is distributed under the terms in `LICENSE`; see also `NOTICE` for the notices that must travel with the source when it is redistributed.

- The scanners remain limited to data Minecraft already delivered and cannot reveal unloaded terrain or server-hidden players.
- The auction workflow is split by risk: Auction Bot can list your items; Market Hunter learns prices and alerts on bargains, and its listing opener is a single user-triggered click. Market Hunter never buys on its own. The AH Sniper added in 0.9.6.0 is the one exception: it is off by default, buys only what your rules name, and stops at the spending and purchase limits you set. Any automation must comply with the server's rules.
- The website uses the Inter typeface under the SIL Open Font License.
=======
# Zeus-Client
Open-source cheat client with unbeatable features.
>>>>>>> 7cbf78d3045ccbdd1d4bfeaed4fccf7dbed318ae
