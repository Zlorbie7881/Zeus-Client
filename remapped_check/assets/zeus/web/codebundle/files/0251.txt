Screenshots and clips for the module drawers on the Features page
==================================================================

Put the files in this folder and list them in ../media.json, or let the helper script do both:

    python3 tools/add_media.py <module id> <file> "Short caption"
    python3 tools/add_media.py killaura ~/Videos/killaura.mp4 "Kill Aura on a zombie" --poster ~/Pictures/killaura.jpg

Module ids are the lower-case names used by /zeus on <id> (killaura, xray, freecam, ...). The full list is in ../features.json.

Formats:   images .jpg .jpeg .png .webp .gif      clips .mp4 .webm
Sizes:     keep screenshots under about 400 KB (1280x720 is plenty) and clips under about 8 MB (10 to 20 seconds, no sound needed).
           The game's jar and the website both include everything in this folder.

Media does not need the source bundle step: just rebuild the website (and the jar) afterwards.
