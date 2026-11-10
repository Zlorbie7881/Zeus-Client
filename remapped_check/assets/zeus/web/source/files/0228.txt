"""Data for the Build a config page: the yes/no questions, the module buckets they switch on, and the playstyles
(a playstyle is simply a set of pre-filled answers). The page scores modules from the buckets, filters them by the answers
(cheats allowed? single-player? low-end PC?) and shows why each module is in the pack."""

# each bucket: label, why (shown next to its modules), and the modules in order of importance. The first `core` are weighted higher.
BUCKETS = {
 "defence":  ("Staying alive", "keeps you alive in a fight", 4, ["autototem", "autoarmor", "autoeat", "antiknockback", "nofall", "antivoid", "durabilityalert", "lowhealth"]),
 "melee":    ("Melee fighting", "helps you win sword and axe fights", 3, ["autoweapon", "aimassist", "killaura", "reach", "triggerbot", "wtap", "jumpreset", "shieldbreaker", "hitboxes", "swordpvpcoach", "autoclicker", "multiaura", "targetstrafe", "swordbot", "spearswap"]),
 "crystal":  ("Crystal and anchor PvP", "for crystal and anchor fights", 5, ["crystalaura", "surround", "holefiller", "holeesp", "maceswap", "attributeswap", "autocombo", "crystalmacro", "anchoraura", "anchormacro", "autotrap", "selftrap", "crystalbot", "crystaloptimizer"]),
 "ranged":   ("Bows", "for bow fights", 2, ["bowaimbot", "bowspam", "trajectories"]),
 "pvpinfo":  ("Fight information", "tells you what your target and opponents are doing", 4, ["targethud", "popcounter", "threatradar", "armorhud", "potionhud", "nametags", "targetesp", "friends"]),
 "bases":    ("Finding bases", "finds bases and hidden storage", 3, ["suspicious", "basefinder", "storageesp", "baseclues", "spawneresp", "blockesp", "chestmemory", "cheststealer", "waypoints"]),
 "underground": ("Tunnels and caves", "for bases and ores underground", 3, ["tunnelbasefinder", "lightdetector", "caveesp", "xray", "lightoverlay", "fullbright"]),
 "donut":    ("DonutSMP", "made for DonutSMP", 3, ["mediabasefinder", "goliathrtp", "staffhud", "auction_house_tweaks", "autosell", "regionmap", "stafflive"]),
 "explore":  ("Exploring", "for long trips and finding your way", 5, ["elytrawaypoint", "elytraboost", "waypoints", "breadcrumbs", "compass", "radar", "nofall", "fullbright", "zoom", "autosprint", "chestswap", "regionmap", "portalfinder", "ancientcityfinder", "trialfinder", "entityspeed"]),
 "mine":     ("Mining", "for digging and finding ore", 5, ["xray", "tunnelminer", "autotool", "fastbreak", "fullbright", "lightoverlay", "antivoid", "autoreplenish", "storageesp", "nuker", "orecompass", "inventorycleaner", "lootwalker"]),
 "build":    ("Building", "for building faster and cleaner", 5, ["fastplace", "schematicbuilder", "schematiclibrary", "scaffold", "autoreplenish", "coordsnapper", "blockoutline", "lightoverlay", "zoom", "freecam", "autotool", "fullbright"]),
 "stream":   ("Streaming", "keeps private details off camera", 3, ["streamerprivacy", "screenshottweaks", "watermark", "keystrokes", "infohud", "modulelist", "session", "netinfo"]),
 "hud":      ("Information on screen", "shows information you check often", 4, ["infohud", "armorhud", "potionhud", "keystrokes", "modulelist", "netinfo", "session", "itemcounter", "compass"]),
 "auto":     ("Small chores", "does small chores for you", 4, ["autoeat", "autotool", "autoreplenish", "autorespawn", "autosprint", "chestmemory", "autofish", "antiafk", "chatmacro1", "autoreconnect", "fastxp", "fastuse", "autofarm", "inventorycleaner", "lootwalker"]),
 "practice": ("Single-player practice", "lets you try things in your own world", 6, ["killaura", "multiaura", "triggerbot", "reach", "hitboxes", "speed", "flight", "treebot", "tunnelminer", "nuker", "swordpvpcoach", "freecam"]),
 "fun":      ("Fun extras", "makes the game more fun to look at", 3, ["knife", "jumpcircles", "customcrosshair", "weatherchanger", "timechanger", "morph", "atmosphere", "discordpresence"]),
 "basics":   ("Quality of life", "small helpers nearly everyone wants", 4, ["fullbright", "zoom", "autosprint", "waypoints", "keystrokes", "nojumpdelay", "antighostblock", "perfguard", "deathmarker"]),
}

# modules that are cheats in the eyes of most servers. Without "cheats are fine here" none of them are offered.
SAFE = ["autotool", "autoeat", "autosprint", "keystrokes", "infohud", "armorhud", "potionhud", "modulelist", "netinfo", "session", "itemcounter", "compass",
        "zoom", "fullbright", "waypoints", "breadcrumbs", "chestmemory", "durabilityalert", "blockoutline", "lightoverlay", "streamerprivacy", "watermark",
        "screenshottweaks", "autoreplenish", "autorespawn", "coordsnapper", "schematiclibrary", "timechanger", "weatherchanger", "customcrosshair", "jumpcircles",
        "knife", "perfguard", "perfmonitor", "deathmarker", "updatecheck", "lowhealth", "regionmap", "radar", "friends", "chatmacro1", "fastplace", "freelook", "trajectories", "nametags", "chestswap", "autofish"]
# modules that only run in single-player (Zeus switches them off on servers)
SPONLY = ["treebot", "tunnelminer", "multiaura"]
# modules that cost the most frame time
HEAVY = ["atmosphere", "esp", "itemesp", "blockesp", "villageresp", "holeesp", "chunkborders", "invisesp", "suspicious", "basefinder", "tunnelbasefinder", "caveesp", "xray",
         "spawneresp", "amethystesp", "kelpesp", "storageesp", "mediabasefinder", "cropfinder", "lightdetector", "baseclues", "lightoverlay", "targetesp"]
# modules every server detects at once; counted for the risk meter
RISKY = ["killaura", "multiaura", "crystalaura", "anchoraura", "aimassist", "triggerbot", "reach", "hitboxes", "autoclicker", "autocombo", "crystalmacro", "anchormacro",
         "bowaimbot", "bowspam", "flight", "speed", "highjump", "longjump", "airjump", "spider", "step", "lowgravity", "jesus", "scaffold", "nuker", "xray", "boatfly", "boatnoclip",
         "airstuck", "antiknockback", "shieldbreaker", "autotrap", "selftrap", "autoweb", "selfweb", "wtap", "jumpreset"]

# question order matters. "if" shows a question only when an earlier answer matches.
QUESTIONS = [
 {"id": "cheats", "text": "Is the place you play fine with cheats?", "hint": "Your own world, or a server whose rules allow cheat clients. Say no and only low-risk helpers are offered.", "default": True, "short": "Cheats are allowed where I play"},
 {"id": "sp", "text": "Do you mostly play in your own single-player world?", "hint": "Single-player unlocks the modules that refuse to run on servers.", "default": False, "short": "Mostly single-player", "if": ["cheats"]},
 {"id": "pvp", "text": "Do you fight other players?", "hint": "PvP on a server or in a duel world.", "default": False, "short": "I fight other players", "if": ["cheats"]},
 {"id": "crystal", "text": "Do you use end crystals or respawn anchors?", "hint": "The crystal PvP kit: crystals, holes, totems.", "default": False, "short": "Crystal and anchor PvP", "if": ["cheats", "pvp"]},
 {"id": "ranged", "text": "Do you use bows a lot?", "hint": "Aim help and trajectory lines.", "default": False, "short": "I use bows", "if": ["cheats", "pvp"]},
 {"id": "bases", "text": "Do you want to find other players' bases or hidden storage?", "hint": "Scanners that score chunks and show storage.", "default": False, "short": "Find bases and storage", "if": ["cheats"]},
 {"id": "underground", "text": "Do you search tunnels and caves?", "hint": "Underground bases, ores and dark spots.", "default": False, "short": "Search underground", "if": ["cheats", "bases"]},
 {"id": "donut", "text": "Do you play on DonutSMP?", "hint": "Media Base Finder, Goliath RTP, staff detection and auction helpers.", "default": False, "short": "I play on DonutSMP", "if": ["cheats", "bases"]},
 {"id": "explore", "text": "Do you travel long distances or explore a lot?", "hint": "Elytra navigation, waypoints, compass and breadcrumbs.", "default": False, "short": "I explore and travel"},
 {"id": "mine", "text": "Do you mine a lot?", "hint": "Tunnelling, ore finding, tool switching and lava safety.", "default": False, "short": "I mine a lot"},
 {"id": "build", "text": "Do you build big things or use schematics?", "hint": "Placement helpers, schematic builder and clear views.", "default": False, "short": "I build"},
 {"id": "stream", "text": "Do you stream or record videos?", "hint": "Hides your name and coordinates and keeps the HUD tidy.", "default": False, "short": "I stream or record"},
 {"id": "hud", "text": "Do you want information on screen (armor, potions, keystrokes)?", "hint": "HUD widgets you can move in the HUD editor.", "default": True, "short": "Information HUD"},
 {"id": "auto", "text": "Do you want helpers for small chores?", "hint": "Eat, switch tools, restock, respawn and stay online.", "default": True, "short": "Helpers for small chores"},
 {"id": "fun", "text": "Do you want the fun extras?", "hint": "Knife animations, jump circles, a custom crosshair and weather.", "default": False, "short": "Fun extras"},
 {"id": "lowend", "text": "Is your computer low-end, or do you want the highest frame rate?", "hint": "Leaves out the heaviest scanners and ESPs.", "default": False, "short": "Keep it light"},
]

# a playstyle is a set of answers; anything not listed keeps its default
PLAYSTYLES = [
 {"id": "explorer", "name": "Explorer", "icon": "map", "blurb": "Travel far, see far and never get lost.", "answers": {"explore": True, "hud": True, "auto": True, "bases": False, "pvp": False}},
 {"id": "basehunter", "name": "Base hunter", "icon": "search", "blurb": "Find bases, stashes and hidden storage where scanning is allowed.", "answers": {"bases": True, "underground": True, "explore": True, "hud": True}},
 {"id": "crystal", "name": "Crystal PvP", "icon": "shield", "blurb": "Totems, armor, crystals, holes and info on your target.", "answers": {"pvp": True, "crystal": True, "hud": True, "auto": True}},
 {"id": "melee", "name": "Sword and axe PvP", "icon": "bolt", "blurb": "Melee fights with aim help and the information you need.", "answers": {"pvp": True, "crystal": False, "hud": True, "auto": True}},
 {"id": "legit", "name": "Quiet helper", "icon": "eye", "blurb": "Small conveniences that are hard to notice. Nothing that cheats.", "answers": {"cheats": False, "hud": True, "auto": True, "explore": True}},
 {"id": "builder", "name": "Builder", "icon": "cube", "blurb": "Build faster and cleaner with schematics and a clear view.", "answers": {"build": True, "hud": True, "auto": True}},
 {"id": "streamer", "name": "Streamer", "icon": "play", "blurb": "Keep private details off camera and show a tidy HUD.", "answers": {"stream": True, "hud": True, "cheats": False, "auto": False}},
 {"id": "practice", "name": "Single-player practice", "icon": "gear", "blurb": "Try everything in your own world, even what servers refuse.", "answers": {"sp": True, "pvp": True, "build": True, "mine": True, "explore": True, "fun": True, "hud": True}},
 {"id": "miner", "name": "Miner", "icon": "gear", "blurb": "Dig, find ore and stay safe underground.", "answers": {"mine": True, "underground": True, "auto": True, "hud": True}},
 {"id": "donut", "name": "DonutSMP", "icon": "spark", "blurb": "The DonutSMP toolkit: media bases, Goliath RTP and staff detection.", "answers": {"bases": True, "donut": True, "explore": True, "hud": True, "auto": True}},
]
