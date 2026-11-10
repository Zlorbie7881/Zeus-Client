import io

p = "tools/site/timeline_data.py"
s = io.open(p, encoding="utf-8").read()

entry = """    {"v":"0.9.9.0","title":"Vanilla spoof, a fake lag you can leave on, and an auction sniper that actually snipes",
     "text":"Vanilla Spoof tells a server you are the ordinary game (vanilla brand, no mod channel list, nothing extra sent) and never adds anything to the stream. Fake Lag now holds and releases your packets in order with five modes and a capped queue, and it keeps answers, pings, client info, resource packs and custom payloads out of it; the old card is now Fake Lag Monitor. The AH Sniper reads every listing, pages through the auction house, refreshes and can search on command, compares each listing with your rule and with the prices the client has learned for that item, buys the biggest discount, can shift-click, confirms with token management, checks the item is really in your inventory and checks your remaining budget before clicking.","extras":[{"k":"Vanilla Spoof"},{"k":"Fake Lag"},{"k":"Auction House Tweaks"},{"k":"Storage ESP"},{"k":"ESP"},{"k":"Zoom"},{"k":"Freecam"}],
     "st":[{"sh":"Fake Lag: Mode, Delay, Jitter, Pulse, Spike, Adaptive, What waits, Only while fighting, Release everything when I am hit.","shs":"Setting"},{"sh":"Fake Lag Monitor: #.","shs":"Setting"},{"sh":"Vanilla Spoof: Vanilla client brand, Hide mod channels, Hide other mod payloads.","shs":"Setting"},
           {"sh":"AH Sniper: use learned prices, buy below market, reaction time, how to buy, search command, pages to turn, check the item arrived.","shs":"Setting"},{"sh":"Zoom: Smooth, Mouse sensitivity while zoomed, Works in third person.","shs":"Setting"},{"sh":"ESP: Tracer from (My body).","shs":"Setting"},{"sh":"Storage ESP: Discoveries card only after a base is found.","shs":"Setting"},{"sh":"Chat: /zeus find and /zeus settings.","shs":"Added"}],
    "arch":"listing","imm":"listings","hash":"upAnotherChin, everyPimicOne"},\n"""

if entry not in s:
    at = s.index("# insert")
    s = s[:at] + entry + s[at:]
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("timeline patched")
