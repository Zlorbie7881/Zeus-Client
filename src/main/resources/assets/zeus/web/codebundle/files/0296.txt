# Update timeline for the Zeus website: newest first. (version, title, text)
TIMELINE = [
 ("0.9.9.0", "Hit confirmation, camera-accurate visuals and a rebuilt AH Sniper", "Fighting: every swing is now matched with the server's answer, so Kill Aura stops hitting a target that is not taking damage and the attack modules show landed and total hits with the server's delay. Kill Aura looks at the target for each hit while your own camera stays put. Hover Totem moves a totem into your offhand from any open container, and Trident Util handles riptide, including a riptide out of a fall. The AH Sniper was rebuilt: it reads every listing, pages and refreshes, buys the biggest discount and checks the item arrived. Seeing: every overlay now follows the game's real camera, so boxes, names and tracers are right in third person, in freecam and while zooming. ESP keeps a fading marker where a target disappeared and an edge arrow for anything off screen, tracers have a new look, and Zoom is a smooth slide with scaled mouse sensitivity. Finding: Suspicious Chunks now counts plants that should have grown, Chunk Scanner lists the richest chunks, the Discoveries card waits for a real base, and Goliath RTP reports the area and its edges. Also new: Spawner Protect, /zeus find and /zeus settings. Atmosphere, Jump Circles, Weather Notifier and Self Trap were removed. The start-up has a new 5-second intro (the logo's ring loads, thunder breaks, then it fades to black) and a new main menu built from a reference picture: the logo above six stone plates between columns, a moonlit storm sky with clouds that drift to the left for ever and fog that rolls along the ground in waves, round Language, Accessibility, Zeus Settings and Credits buttons in the bottom left, and a 3D news scroll in the bottom right. After about a minute on the menu a thunderstorm can roll in (dark clouds, rain, lightning and thunder) and clear again two minutes later; a singleplayer world loads behind the intro's ring with the real percent, and joining a server shows the menu sky with the logo. Plates throw lightning on hover and flash the menu on click, the medallion flashes and gets rained on in storms, the round buttons animate, December brings snow, the menu shows the date and time, other screens get the menu sky, and the loading ring flashes and fades into the world. The plates and round buttons react far more when you hover them (a spring, a shine, a ripple, embers), the menu has sounds (a rumble on hover, a crack on a click, wind and rain), the moon passes behind the storm clouds and returns, and the game window shows the Zeus medallion in the task bar. The pause menu is the logo above buttons in the menu's Greek lettering, the world list and the server list are restyled as stone plates (a test you can switch off), the menu rain is lighter, the medallion reacts to each strike instead of getting rained on, a tip sits under the loading ring, and winter brings an aurora to the menu sky. New module: Aurora, northern lights in your night sky with folding curtains, rays, colour schemes and surges. The Windows layout is a little smaller, and windows can no longer lie on top of each other. Pressing Z plays a new five second intro film (it starts from black, a stepped leader grows out of a storm and one realistic lightning bolt strikes the medallion, arcs of electricity run round it to draw the ring and the lettering is revealed), and the logo on the main menu is now a button that opens the settings. The Z panel was rebuilt with a lightning header, a category rail, module cards with cost chips, an All/On/Off filter and sharper Inter text; nothing was removed. The Windows layout, now the default, was rebuilt in the same look, its module settings open in a Panel-style window, and the lightning on buttons is played from animations rendered ahead of time. New modules: Mace Aura (in the air near a target it takes out the mace, smashes and keeps going) and Mace Damage (a mace hit counts as a fall from a great height). The ESP family has fewer, cleaner settings, corner brackets and slimmer tracers, and the game window is called \"Minecraft 1.21.11 - Zeus Client\". ESP lines, tracers and tags are now smooth pre-rendered sprites that are cut to the screen, which ends the lag when zooming in on marked targets, and Zoom now works inside the renderer's field of view instead of changing the FOV setting every frame. The Profiles window became a button next to Sort in the Windows layout, and the menu's plate lightning, round-button arcs and click flash are animations rendered ahead of time. Tracers of every module now look like Anubis (a thin flat line at half opacity, a stem from the feet to the head, pale colours by kind, a blinking arrow for off-screen targets), Suspicious Chunks marks chunks like Anubis (flat translucent tiles on the Y=63 plane, a rounded edge round each group, grow and shrink animation and a tracer to every area), and the Mojang start screen is replaced by the Zeus ring with the real percent of the game loading. The news scroll now unrolls on a spring with a roll that thins out, falling bands and rippling paper, and the menu clouds change shape and close over the whole sky when a storm forms. The start-up intro has a new sound: a rising ring, a hard crack and rolling thunder, and a warm chord. The website was repaired: the headline reads correctly again, and the Source code page works on a hosted site (its data folder is now called codebundle, so it no longer clashes with the /source page). The site was restyled (flat surfaces, no glow, one accent) and rebuilt for phones: the hero stacks, buttons are full width, filter rows scroll sideways and the helper opens as a bottom sheet. In the game, a one-screen What's new appears once after an update (/zeus whatsnew shows it again). New 3D knives: all 20 knives are now real 3D models with their own handles, blades, guards and pins, and a keyframed set of animations (draw, two inspects, two slashes and a stab, each with moves unique to the knife: butterfly handle fans, karambit ring spins, folder blade flicks, crossed shadow daggers). The Knives page has a 3D viewer (drag to turn, pinch or wheel to zoom, play, pause and scrub the animations, 26 finishes); it is a small hand-written WebGL renderer that pauses when hidden. In the game the knives are drawn from light versions of the same models (200 to 420 triangles, flat shaded) with a Settings switch, 3D knife models, that falls back to the pixel knives. The models and animations are original work in the style of the CS knives, not Valve's assets; the generator is in tools/knives. The butterfly and the karambit were then rebuilt as detailed, realistic models (the butterfly with a clip-point blade with swedge and hollow grind, channel handles with standoffs and a latch; the karambit with a hawkbill blade, contoured G10 scales, hex screws and a finger ring) and given new authored animations: the butterfly has a draw, an inspect with a fan, a rollover, a fan, a zen twirl, an aerial toss, two slashes and a stab; the karambit has a draw, an inspect, a back flip, a ring spin, a pinwheel, a toss and catch, two slashes and a stab. In the game they are new Studio tricks (Studio rollover, Studio fan, Studio ring spin and others) that play the same clips on the 3D models (a few hundred triangles each); they fall back to the classic tricks when 3D knife models is off. The website's viewer shows the buttons that belong to each knife, and the Knives page thumbnails of these two are rendered from the new models. New module: Atmosphere, a real night-sky picture (Milky Way, big northern lights, dark clouds) laid over the sky and brought to life: the lights sway and flow, the clouds drift, the stars twinkle and new shooting stars fall at random moments. It is fitted to the camera so it turns with you, it hides behind hills, and it is only a few hundred drawn rectangles, so it is light on the frame rate."),
 ("0.9.8.0", "Seven new modules, a knife gallery and Inspect, and no more single-player locks", "New modules: Target Strafe circles the player you fight, Auto Farm harvests and replants crops, Chest Stealer empties chests, barrels and shulker boxes, Inventory Cleaner throws junk away, Loot Walker walks to dropped items, Entity Speed makes your mount faster, and Ore Compass is a small panel listing the nearest ore of each kind. Kill Aura can now attack players and pick the target with the lowest health. MultiAura, Tunnel Miner, Tree Bot and the schematic builder's auto-place no longer refuse to run on a server. Every knife has its own Inspect trick, the new knife gallery in the game shows every knife and finish drawn by the game's own code, and the website has a Knife preview page and a Performance monitor page."),
 ("0.9.7.0", "Module costs, a Performance Guard and eighteen new knives", "Every module row in the menu now shows what it costs, and the Features page shows an estimate for every module, with a new Performance monitor page that adds a set of modules up on a gauge. Performance Guard switches the heaviest ESPs off when your FPS drops under a limit you set, and turns them back on when it recovers. /zeus website replaces /zeus web start and sends a cleaner chat message. New: a Keybinds screen, an update checker with a pop-up, a death marker, and an editable channel list with Twitch in Staff Live. The knife tricks flow into each other more smoothly, and there are eighteen new knives based on CS2 shapes, in twelve finishes."),
 ("0.9.6.0", "A lighter client and two fighting bots", "This one was about performance. Modules that are switched off cost nothing, the lists and overlays are cached, the scanners and outlines do less work, and a Performance page shows which modules cost the most. Light mode is now the default, and Zeus lightens itself when your frame rate drops. The module windows are smaller, and Recently used only shows in the panel layout. New modules: Crystal PvP Bot, Sword PvP Bot, Crystal Optimizer, Spear Swap, Fast XP, Fast Use, Click TP, No Jump Delay, Anti Ghost Block, Auto Reconnect, Skin Change, Anti Trap, Staff Live, Atmosphere, Discord Rich Presence and the AH Sniper in Auction House Tweaks. Every version number now starts with 0."),
 ("0.9.5.0", "A proper website and a tidier module list", "The website was reworked to behave like a published site: new header, page backgrounds, opening animations, a thunder cursor and an animated Z. The helper types its answers out and has several wordings for each. Is it safe? now offers a GitHub download, and Build a config can put together a set of modules from a few yes or no questions. In the game, the Windows layout resets once into a tidy grid with a Tidy button, the windows are smaller and opaque, and the module list does less work each frame. Boat NoClip was rebuilt so the boat moves once per tick and the server no longer pulls it out of walls."),
 ("0.9.4.0", "Favourites, per-server profiles and a new knife", "You can star modules to pin them on a Favourites page, which also shows what you used recently. The list can be sorted by most used, and a profile can switch on by itself when you join a server. Jesus is now called Water Walk, and the old name still works. The knife animations were rebuilt: spins wind up, flick and settle, tricks blend into each other, and the knife only slashes when you press attack. The Windows layout is tidier and the watermark shows the real Zeus logo. The website got a Source code page, a profile page, a config builder, a troubleshooter and FAQ categories."),
 ("0.9.3.0", "A better helper and a tidier Features page", "The website helper now answers questions about every module and every setting, with its default and range, and has nearly two hundred written answers. The Features page was redesigned with a category list, a detail drawer and the full settings of each module, and Ctrl+K opens a search. The HUD editor can now move Media Base Finder, Suspicious Chunks, Advanced Base Finder and other cards. The credits and references were expanded."),
 ("0.9.2.0", "A redesigned website", "The website got an animated logo, a Help centre with a helper on the page, a full update timeline and a build you can publish on your own domain. The HUD editor was rebuilt with real outlines, snapping with guide lines, a grid, undo and a floating inspector."),
 ("0.9.1.0", "The website and a lighter menu", "The Zeus website arrived as an optional module with a live control center. Module search no longer re-scores every module on every frame, so the menu is much lighter. The wording on the site now says plainly that Zeus is a cheat client."),
 ("0.9.0.1", "Compile fix and diagnostics", "Fixed the alarm sound for 1.21.11. Media Base Finder got a panel that says why nothing is showing, plus buttons to list teams and copy diagnostics."),
 ("0.9.0.0", "Media Base Finder and a lot of new modules", "Media Base Finder with an estimated base area, a rebuilt Staff HUD that shows how sure it is and why, richer ESPs, nineteen new modules, a Region Map and a scrolling news page on the main menu."),
 ("0.8.3.x", "Finders, miners and a dashboard", "Boat NoClip, Anti Vanish, Tunnel Miner, Market Hunter, Tunnel Base Finder, schematic import and builder, waypoints by coordinates, the global HUD editor, Goliath RTP, MultiAura and the first Media Base Finder. Also a local dashboard with a scheduler."),
 ("0.8.2.0", "An animated tour", "A short animated tour of every category the first time you enter a world. Replay it with /zeus tutorial."),
 ("0.8.1.0", "Three new modules", "Sword PvP Coach, Tree Bot for your own singleplayer world, and Elytra Waypoint, which shows distance, bearing and height."),
 ("0.8.0.x", "Settings and stability", "Settings now load before the modules start and are saved safely. Floating windows animate in, modules survive switching servers, Attribute Swap and Crystal/Anchor coordination were added, and the categories were reorganised."),
 ("0.7.x", "X-Ray, search and speed", "X-Ray opacity that is really see-through, a faster Suspicious Chunks queue, Ctrl+K search and smoother knife animations."),
 ("0.6.x", "A new panel and the Auction Bot", "The panel now fills the screen, with the big Z and a pixel-art Zeus. Friends and profile IDs, the Auction Helper and then the Auction Bot, and a clickable logo on the main menu."),
 ("0.5.9.0", "Fake Scoreboard and screenshot buttons", "Fake Scoreboard now copies a reference board exactly, and screenshots got Open and Copy buttons in chat."),
 ("0.5.8.0", "X-Ray fix and Smooth Aim Assist", "X-Ray opacity was fixed for the game's own renderer, and Smooth Aim Assist was added."),
 ("0.5.7.0", "The karambit", "A karambit joined the knife animations, held the way the reference video holds it."),
 ("0.5.6.0", "The balisong", "The butterfly knife was rebuilt as a 3D balisong, with your own skin on the arm."),
 ("0.5.5.0", "Chest Memory and Pop Counter", "Added Chest Memory and Pop Counter, and made the client lighter."),
 ("0.5.4.0", "Net Info and Session", "Net Info (ping graph and jitter) and Session (time, distance and top speed) panels."),
 ("0.5.3.0", "A sharper main menu", "A sharper main menu and a clearer X-Ray opacity slider."),
 ("0.5.2.0", "A new main menu", "A new main menu with six stone tablets, a rebuilt Fake Scoreboard and a Profiles page."),
 ("0.5.1.0", "Thunder, and a new version scheme", "More thunder in the menus. Big updates now raise the first number of the version."),
 ("0.4.5.x", "Better base hunting", "Suspicious Chunks now looks at the whole situation, finds small bases and shows its doubts, and Zeus Sense watches your own state. Freecam was made safe, and the main menu got a slow drifting background."),
 ("0.4.4.0", "A base brain", "A scoring brain for Suspicious Chunks and Base Finder, with natural-structure suppression and clustering."),
 ("0.4.3.0", "Outlines that fit", "Storage and block outlines now follow the real shape of each block."),
 ("0.4.2.0", "Freecam and more", "Freecam, Free Look, No Fall, Jesus, Safe Walk and Anti Vanish."),
 ("0.4.1.0", "A new intro film", "A new intro film, with thunder mixed into the sound."),
 ("0.4.0.0", "The Zeus main menu", "The title screen was replaced with the storm, the god on his throne and six stone tablets. The intro film arrived too."),
 ("0.3.1", "Morph and X-Ray fixes", "Morph turns you into any mob, for your own eyes only. X-Ray was corrected."),
 ("0.3.0", "Fully client side", "Zeus became a fully client-side mod with no multiplayer lock, so every module can be tried anywhere."),
]


# Extra per-release details shown on the website. "mods": module names (or ids) the release added or changed, shown as links
# to the Features page; "settings": settings or options added or changed, shown as a short list. Add an entry for every release.
EXTRA = {
    "0.9.9.0": {
        "mods": ["Mace Aura", "Mace Damage", "Kill Aura", "MultiAura", "Auto Combo", "Crystal Aura", "Hover Totem", "Trident Util", "Spawner Protect", "Chunk Scanner", "Suspicious Chunks", "Goliath RTP", "Auction House Tweaks", "Storage ESP", "ESP", "Zoom", "Freecam"],
        "settings": [
            "Hit confirmation: landed/total and the server's delay on every attack module; Kill Aura stops after four unanswered hits.",
            "AH Sniper: use learned prices, buy below market, reaction time, how to buy, search command, pages to turn, check the item arrived.",
            "Zoom: Smooth, Mouse sensitivity while zoomed, Works in third person.",
            "ESP: Tracer from (My body), Ghost markers, edge arrows, new tracer styles (Modern, Beam, Classic).",
            "Hover Totem: Where it goes (Offhand or Selected hotbar slot).",
            "Trident Util: Draw it when I use, Give my item back.",
            "Storage ESP: Discoveries card only after a base is found.",
            "Chat: /zeus find and /zeus settings.",
        ],
    },
    "0.9.8.0": {
        "mods": ["Target Strafe", "Auto Farm", "Chest Stealer", "Inventory Cleaner", "Loot Walker", "Entity Speed", "Ore Compass", "Kill Aura", "MultiAura", "Tunnel Miner", "Tree Bot", "Knife"],
        "settings": [
            "Kill Aura: Players, Pick the target by (closest or lowest health)",
            "Knife: Open the knife gallery, and a new Inspect trick for every knife",
            "MultiAura, Tunnel Miner, Tree Bot, Schematic Builder: no longer limited to single-player worlds",
            "Schematic Builder: Auto-place (was Auto-place (single-player))",
        ],
    },
    "0.9.7.0": {
        "mods": ["Performance Guard", "Update Checker", "Death Marker", "Staff Live", "Knife"],
        "settings": [
            "UI settings: Show what each costs (a cost next to every module in the panel)",
            "Interface > Module list: Show what each costs",
            "Performance Guard: FPS limit, how many to switch off, Only scanners, tell me in chat",
            "Knife: Knife type now lists eighteen CS2-style knives, and Knife finish picks the paint",
            "Staff Live: add or remove channels in the menu, YouTube, TikTok or Twitch",
        ],
    },
    "0.9.6.0": {
        "mods": ["Performance Monitor", "Crystal PvP Bot", "Sword PvP Bot", "Crystal Optimizer", "Spear Swap", "FastXP", "Fast Use", "Click TP", "No Jump Delay", "Anti Ghost Block", "Auto Reconnect", "Skin Change", "Anti Trap", "Staff Live", "Atmosphere", "Discord Rich Presence", "Auction House Tweaks"],
        "settings": [
            "Interface: Game mode (Balanced, Light, Minimal) and Auto-lighten",
            "Performance: Show what costs the most, Switch off the 3 costliest",
            "Auction House Tweaks: AH Sniper (off by default, with a spending limit)",
            "Windows layout: smaller category windows, Recently used only in the panel layout",
        ],
    },
    "0.9.5.0": {
        "mods": ["Boat NoClip", "Boat Fly"],
        "settings": [
            "Boat NoClip: Ignore server corrections",
            "Boat NoClip: works with rafts as well as boats",
            "Windows layout: Tidy button, smaller windows, scroll inside past eight rows",
        ],
    },
    "0.9.4.0": {
        "mods": ["Water Walk", "Knife", "Watermark"],
        "settings": [
            "Interface: Sort modules (Category order, Most used first, A to Z)",
            "Profiles: Switch profile per server",
            "Profiles: Use it on the server I am on",
            "Profiles: Stop switching on this server",
            "Knife: new balisong tricks Helicopter, Double toss, Latch flip and Cascade",
            "Knife: new karambit tricks Wrist orbit, Cross spin, Double toss and Ring storm",
            "Knife: Stab flurry became Spin stop (no more stabbing without attack)",
        ],
    },
    "0.9.3.0": {
        "mods": ["Media Base Finder", "Suspicious Chunks", "Advanced Base Finder"],
        "settings": ["HUD editor: Media Base Finder, Suspicious Chunks and Advanced Base Finder cards can be moved"],
    },
}
