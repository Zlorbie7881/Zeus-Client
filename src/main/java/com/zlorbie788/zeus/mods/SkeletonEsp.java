package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * A stick figure of bones for every player: spine, arms, legs and a head, drawn through walls.
 * Positions are interpolated for the current frame, so the figure does not stutter at low frame
 * rates, and the limbs follow a crouch by shortening with the real hitbox height.
 */
public final class SkeletonEsp extends Module {
    private final Setting.Num range = add(new Setting.Num("Range", "How far away players are drawn.", 64, 8, 200, 4, " blocks"));
    private final Setting.Num thickness = add(new Setting.Num("Thickness", "How thick the bones are.", 1.8, 0.6, 4.0, 0.2, " px"));
    private final Setting.Num opacity = add(new Setting.Num("Opacity", "How strong the bones are.", 75, 20, 100, 5, " %"));
    private final Setting.Bool head = add(new Setting.Bool("Head box", "Draw a small box for the head as well as the spine.", true));
    private final Setting.Bool self = add(new Setting.Bool("Show yourself", "Draw your own skeleton too.", false));

    public SkeletonEsp() {
        super("skeletonesp", "Skeleton ESP", "Draws the bones of every player through walls, so you can read their pose and aim before you see them.", Category.RENDER);
        alias("skeleton", "bones", "stick figure");
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.level == null || mc.player == null) return;
        if (!Projector.prepare(mc, partial, w, h)) return;
        double limit = range.get();
        double limit2 = limit * limit;
        int base = 0xFF57C8FF;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player && !self.on()) continue;
            if (p.isSpectator()) continue;
            double x = Mth.lerp(partial, p.xo, p.getX());
            double y = Mth.lerp(partial, p.yo, p.getY());
            double z = Mth.lerp(partial, p.zo, p.getZ());
            double dx = x - Projector.eyeX();
            double dy = y + 0.9 - Projector.eyeY();
            double dz = z - Projector.eyeZ();
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > limit2) continue;
            float fade = (float) Math.max(0.12, Math.min(1.0, 1.0 - Math.sqrt(d2) / limit));
            int color = Draw.rgba(base, (float) (opacity.get() / 100.0) * fade);
            if (Projector.depth(x, y + 0.9, z) < -1.5) continue;
            bones(g, p, partial, x, y, z, color);
        }
    }

    private void bones(GuiGraphics g, AbstractClientPlayer p, float partial, double x, double y, double z, int color) {
        float thick = thickness.f();
        float yaw = (float) Math.toRadians(Mth.lerp(partial, p.yRotO, p.getYRot()));
        double sideX = Math.cos(yaw) * 0.20;
        double sideZ = Math.sin(yaw) * 0.20;
        double s = Mth.clamp(p.getBbHeight() / 1.8, 0.7, 1.4);
        double feet = y + 0.06;
        double knee = y + 0.48 * s;
        double hip = y + 0.88 * s;
        double chest = y + 1.26 * s;
        double neck = y + 1.46 * s;
        double skull = y + 1.62 * s;

        // spine
        Wire.line(g, x, hip, z, x, chest, z, thick, color, true);
        Wire.line(g, x, chest, z, x, neck, z, thick, color, true);

        // legs
        Wire.line(g, x - sideX * 0.55, hip, z - sideZ * 0.55, x - sideX * 0.55, knee, z - sideZ * 0.55, thick, color, true);
        Wire.line(g, x - sideX * 0.55, knee, z - sideZ * 0.55, x - sideX * 0.55, feet, z - sideZ * 0.55, thick, color, true);
        Wire.line(g, x + sideX * 0.55, hip, z + sideZ * 0.55, x + sideX * 0.55, knee, z + sideZ * 0.55, thick, color, true);
        Wire.line(g, x + sideX * 0.55, knee, z + sideZ * 0.55, x + sideX * 0.55, feet, z + sideZ * 0.55, thick, color, true);

        // arms, out at the shoulders and down to the hands
        Wire.line(g, x, chest, z, x - sideX * 1.6, chest, z - sideZ * 1.6, thick, color, true);
        Wire.line(g, x - sideX * 1.6, chest, z - sideZ * 1.6, x - sideX * 1.8, hip + 0.05, z - sideZ * 1.8, thick, color, true);
        Wire.line(g, x, chest, z, x + sideX * 1.6, chest, z + sideZ * 1.6, thick, color, true);
        Wire.line(g, x + sideX * 1.6, chest, z + sideZ * 1.6, x + sideX * 1.8, hip + 0.05, z + sideZ * 1.8, thick, color, true);

        if (head.on()) {
            Wire.box(g, x - 0.22, skull - 0.22, z - 0.22, x + 0.22, skull + 0.22, z + 0.22, thick * 0.85f, color, true);
            Wire.line(g, x, neck, z, x, skull - 0.22, z, thick, color, true);
        }
    }
}
