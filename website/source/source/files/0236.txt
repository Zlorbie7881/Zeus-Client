"""Playstyle presets for the "Build a config" page. Each preset lists the module ids to switch on (ids come from features.json).
The page turns them into a zeus.json file and a profile share code. No setting values are written, so every module keeps the defaults."""
PRESETS = [
 {"id": "explorer", "name": "Explorer", "icon": "map", "blurb": "Travel far, see far and never get lost. Mostly visual and movement helpers.",
  "mods": ["fullbright", "zoom", "waypoints", "breadcrumbs", "compass", "elytrawaypoint", "elytraboost", "autoeat", "autosprint", "nofall", "freecam", "infohud", "radar"]},
 {"id": "basehunter", "name": "Base hunter", "icon": "search", "blurb": "Find bases, stashes and hidden storage on servers you are allowed to scan.",
  "mods": ["suspicious", "basefinder", "mediabasefinder", "tunnelbasefinder", "storageesp", "spawneresp", "blockesp", "chunkborders", "caveesp", "xray", "waypoints", "chestmemory", "brain"]},
 {"id": "crystal", "name": "Crystal PvP", "icon": "shield", "blurb": "The usual crystal PvP kit: totems, armor, crystals, holes and info on your target.",
  "mods": ["autototem", "autoarmor", "crystalaura", "surround", "holefiller", "holeesp", "targethud", "popcounter", "threatradar", "autoeat", "maceswap", "attributeswap", "armorhud", "potionhud"]},
 {"id": "legit", "name": "Quiet helper", "icon": "eye", "blurb": "Small conveniences that are hard to notice: HUD, auto tool, auto eat and gentle aim help.",
  "mods": ["autotool", "autoeat", "autosprint", "fullbright", "keystrokes", "infohud", "armorhud", "potionhud", "aimassist", "nametags", "durabilityalert", "chestmemory"]},
 {"id": "builder", "name": "Builder", "icon": "cube", "blurb": "Build faster and cleaner: placement help, schematics, zoom and a clear view.",
  "mods": ["fastplace", "scaffold", "schematicbuilder", "schematiclibrary", "autoreplenish", "coordsnapper", "blockoutline", "lightoverlay", "zoom", "freecam", "autotool", "fullbright"]},
 {"id": "streamer", "name": "Streamer", "icon": "play", "blurb": "Keep private details off camera and show a tidy HUD.",
  "mods": ["streamerprivacy", "keystrokes", "infohud", "watermark", "modulelist", "session", "netinfo", "armorhud"]},
 {"id": "practice", "name": "Single-player practice", "icon": "bolt", "blurb": "Try things in your own world, including the modules that refuse to run on servers.",
  "mods": ["killaura", "multiaura", "triggerbot", "reach", "hitboxes", "autoclicker", "speed", "flight", "treebot", "tunnelminer", "nuker", "swordpvpcoach"]},
 {"id": "miner", "name": "Miner", "icon": "gear", "blurb": "Dig, find ore and keep safe: X-Ray, tunnel tools, lava safety and light.",
  "mods": ["xray", "tunnelminer", "autotool", "fullbright", "lightoverlay", "fastbreak", "antivoid", "autoeat", "autoreplenish", "nofall", "waypoints", "storageesp", "orecompass", "inventorycleaner"]},
]
