"""Puts each FAQ entry into a category for the Help page. First matching rule wins; anything left is "About Zeus"."""
import re

ORDER = ["Getting started", "Safety & rules", "Troubleshooting", "Compatibility", "Panel, HUD & themes",
         "Settings & profiles", "Website & control center", "Base finding", "Modules & PvP", "Minecraft terms", "About Zeus"]

RULES = [
    ("Safety & rules", r"download zeus from|from github|genuine|website official|is this website|control center safe"),
    ("Panel, HUD & themes", r"favourite|favorite|sort the module"),
    ("Website & control center", r"profile on the website|source code|troubleshooting wizard|did that help|screenshots or clips|what was added in each"),
    ("Modules & PvP", r"jesus|knife"),
    ("Minecraft terms", r"^what is (an? )?(esp|aura|surround|hole in|anticheat|chunk|stash|schematic|a discord webhook|tps|a macro)|^what are rubber|^what is a (hole|chunk|stash|schematic|macro|discord)|^what is tps"),
    ("Troubleshooting", r"does nothing|not working|dropped|crash|black screen|does not appear|not showing|can't see|cannot see|disconnected|will not open|rubber|stuck|missing|does not exist|two zeus jars|switch itself|says zeus needs|says it must|says fabric|shows nothing|find nothing|why does|why did|why can't|is not showing|report a bug|contact support|lag my game"),
    ("Safety & rules", r"safe\b|ban|virus|steal|collect data|telemetry|cheat client|anti-?cheat|bypass|server i do not control|realm|cracked|affiliated|branded|sent to the server|less obvious|install zeus on the server|dangerous|donutsmp"),
    ("Compatibility", r"sodium|iris|lithium|optifine|shaders|litematica|xaero|journeymap|replaymod|lunar|badlion|feather|forge|neoforge|quilt|bedrock|mobile|mac and linux|single-player|singleplayer|which minecraft version|newer minecraft|other clients"),
    ("Getting started", r"install|mods folder|fabric|java|^how do i update|uninstall|launcher|ram|usb|^is zeus free|^what is zeus client|^how many modules|^how do i open the zeus menu"),
    ("Modules & PvP", r"staff hud"),
    ("About Zeus", r"who came up|who made|what did|licen[cs]e|open source|source code|redistribute|youtube|copy of|references|sounds, art"),
    ("Settings & profiles", r"settings|profile|preset|config|reset|back up|backup|share|turn on automatically|auto.?enable|language|read every setting"),
    ("Website & control center", r"website|control center|token|\bport\b|publish|host|https|download link|discord link|sha-?256|domain|presets on the website|search the whole"),
    ("Panel, HUD & themes", r"hud|widget|panel|menu|bind|key\b|search for a module|categories|colour|color|intro film|status line|module id|without opening|turn a module on|which modules are on|panic|screenshots or recordings|streamer|align|move|switch everything off"),
    ("Base finding", r"base|scanner|suspicious|media base|stash|spawner|farms|portal|underground|x-?ray|region map|goliath|see through|owner|whose|schematic|waypoint|anti-xray"),
    ("Modules & PvP", r"aura|totem|esp|surround|elytra|mace|scaffold|fall|fly|auction|crystal|combat|eat|mine|afk|sense|threat|pop counter|anti vanish|chest memory|scoreboard|lag monitor|screenshot|webhook|friends|staff hud|zeus brain|commands"),
]


def category(q):
    s = q.lower()
    for name, rx in RULES:
        if re.search(rx, s):
            return name
    return "About Zeus"


if __name__ == "__main__":
    import sys
    sys.path.insert(0, ".")
    from help_data import FAQ
    from collections import defaultdict
    d = defaultdict(list)
    for q, *_ in FAQ:
        d[category(q)].append(q)
    for c in ORDER:
        print("==", c, len(d[c]))
        for q in d[c]:
            print("   ", q)
