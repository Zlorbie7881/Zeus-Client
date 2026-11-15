"""Makes the extra cloud pictures of the main menu that make the sky move and the storm cover all of it.

  cloud_far_b, cloud_mid_b, cloud_near_b   the same three banks as menu_clouds.py, but every noise field moved 45 degrees along a
                                           second field: the same big shapes with changed details. The menu fades between a bank
                                           and its twin, so the clouds slowly change shape and are not only pushed sideways.
  storm_full_a, storm_full_b               2560 x 1000 a heavy, dark, almost unbroken cover that fills the whole sky (a and b are twins)
  storm_scud                               2560 x 520 ragged low cloud that races along under the storm

Usage: python3 tools/menu_overcast.py <output folder>
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import menu_clouds as mc
from menu_clouds import cloud, save

TWIN = 45.0


def banks(out, phase, suffix):
    mc.PHASE = phase
    H = 890
    c, a = cloud(H, 11, 4.6, (12, 20, 42), (40, 56, 90), (100, 126, 172), -0.15, 0.70, 80.0, 0.42, 0.40, 0.16)
    save(os.path.join(out, "cloud_far%s.png" % suffix), c, a * 0.78)
    c, a = cloud(H, 23, 3.3, (7, 12, 29), (34, 50, 82), (116, 144, 190), -0.22, 0.68, 95.0, 0.38, 0.55, 0.18)
    save(os.path.join(out, "cloud_mid%s.png" % suffix), c, a * 0.92)
    c, a = cloud(H, 37, 2.3, (5, 9, 23), (30, 44, 74), (130, 158, 204), -0.28, 0.66, 110.0, 0.34, 0.70, 0.20)
    save(os.path.join(out, "cloud_near%s.png" % suffix), c, a * 0.96)


def full(out, phase, suffix):
    mc.PHASE = phase
    c, a = cloud(1000, 91, 3.0, (6, 9, 20), (46, 58, 86), (92, 116, 160), -1.55, 0.20, 130.0, 0.0001, 0.30, 0.22,
                 fade_top=0.0, fade_bot=0.92, lift_amount=0.30)
    save(os.path.join(out, "storm_full%s.png" % suffix), c, a * 0.98)


def scud(out):
    mc.PHASE = 0.0
    c, a = cloud(520, 117, 5.5, (8, 12, 26), (52, 66, 98), (110, 134, 178), -0.35, 0.55, 90.0, 0.55, 0.45, 0.20,
                 fade_top=0.25, fade_bot=0.85, lift_amount=0.35)
    save(os.path.join(out, "storm_scud.png"), c, a * 0.90)


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    which = sys.argv[2] if len(sys.argv) > 2 else "all"
    if which in ("all", "banks"):
        banks(out, TWIN, "_b")
    if which in ("all", "full"):
        full(out, 0.0, "_a")
        full(out, TWIN, "_b")
    if which in ("all", "scud"):
        scud(out)
    print("done")
