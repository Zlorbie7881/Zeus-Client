# Knife files the user attached (saved 2026-11-13)

The user attached five source files and said more are coming. They are *reference material
only* — nothing in them has been applied to this workspace yet. The full text stays in the
chat's attachment store and can be re-read by id in any later turn; ask for a file to be
materialised into `src/` (or anywhere) when it is time to work on it.

| name | bytes | attachment id | role |
|---|---|---|---|
| KnifeSwing.java | 72,639 | `0077b8f11186fe1c0cbcf7ddc3fbcebf243a1fd5bbd032d34eb25957d98d7824` | the `knife` module: settings, trick scripts, pose engine, HUD draw |
| KnifeArt.java | 49,908 | `d048dac462dcdcdf0e6be1c743c3ee02049ad6e61bf62365ca4883c4dab70b73` | `Look`, pixel `Rig` drawing, `SMODELS` table, karambit art |
| KnifeHD.java | 21,959 | `b3a4dff510e2b88dbd0393544e6f1b216f51148acb93aec027819d4ad960627b` | HD layered-slab knife builder (quads + studio lighting) |
| KnifeHDGui.java | 8,121 | `2e009d61871639156975875b852384f25ca6cb333836333ccea491a038cf39b4` | submits `KnifeHD` quads as GUI geometry, gallery showcase |
| tactical.json | 16,650 | `d22ce5fd0e29e7f60f2eb26628261419548ba2032c383317638f557ed1cc3496` | 8 HD tactical knives: sheet length, parts, centre lines |

## What they establish

- **`KnifeHD` model**: `len` 28.4, `half` 3.55, `zmax` 1.4, 7 atlas cells, 3 detail levels
  (1024×256, 512×128, 256×64). Each knife is a list of parts (`pommel`/`ring`/`handle`/`guard`/`blade`)
  with `u0`, `u1`, `t` (half thickness) and `r` (rounding). Sheets are
  `textures/knives/hd/<id>_<level>.png`, where the cell row holds `FACE, RIM50, RIM13, SIDE,
  GLOSS, EDGE, PROFILE`; alpha in the sheet *is* the outline, so saw teeth, slots and rings come
  from the artwork at full resolution.
- **`tactical.json`** knives: `tac_damascus, tac_slaughter, tac_case, tac_fade, tac_crimson,
  tac_tiger, tac_doppler, tac_urban`, each with `centre[]` samples along u, a `tip` [u,v] and a
  glow colour. Geometry proportion inside these: handle to ~12.7, guard to ~13.3, blade to 28.2
  — i.e. roughly 45% handle, 55% blade on the sheet.
- **`KnifeArt`**: `Look { handle[4], blade[3], glow, shape, design, finish, mirror, fine }`;
  `SMODELS` ends with the eight HD entries and `HD_COUNT = 8`, matched by `hdOf(key)` /
  `hdType(type)`; `gripOf(key)` returns `KnifeHD.grip(...)` for HD knives.
- **`KnifeSwing`**: `showcase`/`hd()` path draws HD knives through `KnifeHDGui.draw`, so they
  get `T_INSPECT`, `T_REVERSE`, `T_ROLL`, `T_XCUT` tricks; `safeDraw` forces the pixel knife;
  `meshReady()` gates the quad models (`KnifeMesh`).

## Attached reference sheets (images, 2026-11-13)

Eight images were attached, three knife themes at three sheet sizes each (plus the extra
rows). They are HD knife sheets in the layout `tactical.json` expects: a column of rows on
white, one row per atlas level, and the last rows holding the plain and collapsed states.

Common layout of every sheet, top to bottom:

1. finished knife, full detail (the 1024 wide level)
2. the same knife at half size (512 level)
3. the same knife at quarter size (256 level)
4. the same knife with a flat grey steel blade (no skin) and no pattern
5. an empty white band
6. the handle with the blade collapsed to a thin rod (the finished/profiled edge state)

**Knife A — case hardened.** Black ribbed rubber grip; square brass pommel cap; brass guard
made of two stacked rings; blade mottled blue and gold case hardening with a gold spine,
saw-tooth serrations along the spine at the ricasso, a long thin fuller slot, `ZEUS` engraved
near the tip, clip-point tip with a dark shadow along the edge.

**Knife B — crimson web.** Black grip with a brass lanyard ring at the pommel, two brass
rivets, vertical grip grooves; blade black with a red spiderweb pattern, fine serrations at
the ricasso, long fuller slot, drop-point tip. Spine of the blade is a lighter grey strip.

**Knife C — damascus.** Black grip; brass pommel with a lanyard hole; orange/brass ferrule
ring between grip and guard; single brass oval guard; wavy damascus blade with serrations at
the ricasso, long fuller slot and `ZEUS` near the tip.

### Second batch of sheets

Same six row layout as above (finished / half / quarter / flat grey / blank / collapsed rod),
four more knives:

**Damascus (small sheet only).** Black grip, brass pommel with a lanyard hole, square brass
ferrule, brass oval guard, dark wavy damascus blade with ricasso serrations and the long
fuller slot.

**Doppler · Sapphire (three sizes).** The whole knife is skinned — handle *and* blade — in deep
blue with near-black clouds and small pink/violet spots. A thin vertical brass guard rod
instead of a wide guard; `ZEUS` printed on the blade; the ricasso serrations sit on the
cutting-edge side and point down towards the handle; pointed tip.

**Fade (three sizes).** Handle is dark red, wood/checkered, with three rivets and a lanyard
hole at the pommel; blade runs red → magenta → near-black → purple → blue towards the tip;
thin brass guard; spine serrations at the ricasso, long fuller slot, dagger-style pointed tip.

**Slaughter (one large sheet).** Orange-red textured grip with black inlay panels and a
lanyard hole; grey/red mottled blade with X-stitch marks, ricasso serrations, fuller slot and
`ZEUS` near the tip; small dark guard.

Note: the image bytes are not on this workspace's disk (attachments live in the chat, not in
the repo), so they cannot be copied into `assets/` from here. Drop the PNGs into
`src/main/resources/assets/zeus/textures/knives/hd/` when they should be used.

## Implemented (2026-11-13)

The HD sheet system is now in this workspace and wired into the module:

- `tools/knife_hd_sheets.py` draws the eight tactical knives (case hardened, crimson web,
  damascus, sapphire, fade, slaughter, tiger tooth, urban masked) into 7 cell atlases at the
  three levels, and writes `assets/zeus/knives/hd/tactical.json` from the same silhouettes.
- `ui/KnifeHd.java` loads that table and draws the knives as layered slabs, picking the sheet
  level from the on screen length and shading each quad with Minecraft's face brightness.
- `ui/KnifeArt.java` gained the eight `Tactical · ...` straight knife entries, `HD_COUNT`,
  `hdOf`, `hdType` and HD aware `gripOf`.
- `mods/KnifeSwing.java` draws HD knives (and their motion ghosts) through `KnifeHd` before
  the quad mesh and pixel fallbacks.
- `tools/knife_hd_preview.py` is the offline check: it rasterises the same quads straight from
  the atlas, so the cells, the alpha outline and the part transforms can be seen without the
  game.
