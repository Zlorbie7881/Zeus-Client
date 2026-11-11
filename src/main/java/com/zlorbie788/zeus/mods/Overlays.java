package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Build;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class Overlays {
    private Overlays() {}

    public static final class HoleEsp extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you it looks.", 10, 4, 20, 1, " blocks"));
        private final Setting.Bool bedrock = add(new Setting.Bool("Bedrock holes", "Holes with only bedrock around (safest).", true));
        private final Setting.Bool obsidian = add(new Setting.Bool("Obsidian holes", "Holes with obsidian or a mix.", true));
        private final Setting.Bool fill = add(new Setting.Bool("Cross", "Draw a cross on the floor of every hole.", true));
        private final Setting.Choice bedrockColor = add(new Setting.Choice("Bedrock colour", "Colour of holes with only bedrock around.", 5, com.zlorbie788.zeus.render.EspKit.COLOR_NAMES));
        private final Setting.Choice obsidianColor = add(new Setting.Choice("Obsidian colour", "Colour of holes with obsidian or a mix.", 4, com.zlorbie788.zeus.render.EspKit.COLOR_NAMES));
        private final Setting.Num width = add(new Setting.Num("Line width", "Thickness of the lines.", 1.2, 0.6, 4.0, 0.2, ""));
        private final Setting.Num opacity = add(new Setting.Num("Opacity", "How strong the lines are.", 100, 20, 100, 5, " %"));
        private final List<int[]> holes = new ArrayList<>();
        private int ticks;

        public HoleEsp() {
            super("holeesp", "Hole ESP", "Marks one-block holes in the ground.", Category.RENDER);
            light();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null || mc.player == null || ++ticks % 10 != 1) return;
            holes.clear();
            BlockPos c = mc.player.blockPosition();
            int r = range.i();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -4; y <= 4; y++) {
                        BlockPos p = c.offset(x, y, z);
                        if (!Build.air(p) || !Build.air(p.above())) continue;
                        int kind = Defense.Hole.kind(p);
                        if (kind == 0 || (kind == 2 && !bedrock.on()) || (kind == 1 && !obsidian.on())) continue;
                        holes.add(new int[] {p.getX(), p.getY(), p.getZ(), kind});
                    }
                }
            }
        }

        @Override
        public String info() {
            return holes.isEmpty() ? null : Integer.toString(holes.size());
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || holes.isEmpty() || !Projector.prepare(mc, partial, w, h)) return;
            for (int[] p : holes) {
                int col = p[3] == 2 ? com.zlorbie788.zeus.render.EspKit.pick(bedrockColor, 0xFF52F28A) : com.zlorbie788.zeus.render.EspKit.pick(obsidianColor, 0xFFFFC857);
                col = Draw.rgba(col, opacity.f() / 100f);
                Wire.floor(g, p[0] + 0.06, p[1] + 0.02, p[2] + 0.06, 0.88, width.f(), col);
                if (fill.on()) {
                    Wire.line(g, p[0] + 0.06, p[1] + 0.02, p[2] + 0.06, p[0] + 0.94, p[1] + 0.02, p[2] + 0.94, width.f() * 0.85f, Draw.rgba(col, 0.7f), false);
                    Wire.line(g, p[0] + 0.94, p[1] + 0.02, p[2] + 0.06, p[0] + 0.06, p[1] + 0.02, p[2] + 0.94, width.f() * 0.85f, Draw.rgba(col, 0.7f), false);
                }
            }
        }
    }

    public static final class LightOverlay extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you it looks.", 12, 4, 24, 1, " blocks"));
        private final List<int[]> spots = new ArrayList<>();
        private int ticks;

        public LightOverlay() {
            super("lightoverlay", "Light Overlay", "Marks floors where monsters can spawn: red anytime, yellow at night.", Category.RENDER);
            light();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null || mc.player == null || ++ticks % 10 != 3) return;
            spots.clear();
            BlockPos c = mc.player.blockPosition();
            int r = range.i();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -5; y <= 5; y++) {
                        BlockPos p = c.offset(x, y, z);
                        if (!Build.solid(p.below()) || !Build.air(p) || Build.solid(p) || !Build.air(p.above())) continue;
                        int block = mc.level.getBrightness(LightLayer.BLOCK, p);
                        if (block > 0) continue;
                        int sky = mc.level.getBrightness(LightLayer.SKY, p);
                        spots.add(new int[] {p.getX(), p.getY(), p.getZ(), sky == 0 ? 0 : 1});
                    }
                }
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || spots.isEmpty() || !Projector.prepare(mc, partial, w, h)) return;
            for (int[] p : spots) {
                int col = p[3] == 0 ? 0xFFFF5A5A : 0xFFFFD24D;
                double y = p[1] + 0.03;
                Wire.line(g, p[0] + 0.2, y, p[2] + 0.2, p[0] + 0.8, y, p[2] + 0.8, 1f, col, false);
                Wire.line(g, p[0] + 0.8, y, p[2] + 0.2, p[0] + 0.2, y, p[2] + 0.8, 1f, col, false);
            }
        }
    }

    public static final class Trajectories extends Module {
        private final Setting.Bool landing = add(new Setting.Bool("Landing marker", "A box where it hits.", true));
        private final Setting.Choice colour = add(new Setting.Choice("Colour", "Colour of the path.", 0, "Blue", "White", "Gold"));

        public Trajectories() {
            super("trajectories", "Trajectories", "Shows the flight path of bows, pearls, snowballs and potions.", Category.RENDER);
            super.add(new Setting.Info("Note", () -> List.of("Still in development.")));
        }

        private static double[] physics(Minecraft mc, ItemStack s) {
            if (s.is(Items.BOW)) {
                if (!mc.player.isUsingItem()) return null;
                float f = mc.player.getTicksUsingItem() / 20f;
                f = (f * f + f * 2f) / 3f;
                if (f < 0.1f) return null;
                return new double[] {Math.min(1f, f) * 3.0, 0.05};
            }
            if (s.is(Items.CROSSBOW)) return new double[] {3.15, 0.05};
            if (s.is(Items.TRIDENT)) return new double[] {2.5, 0.05};
            if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)) return new double[] {1.5, 0.03};
            if (s.is(Items.EXPERIENCE_BOTTLE)) return new double[] {0.7, 0.07};
            if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new double[] {0.5, 0.05};
            return null;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null || !Projector.prepare(mc, partial, w, h)) return;
            double[] ph = physics(mc, mc.player.getMainHandItem());
            if (ph == null) ph = physics(mc, mc.player.getOffhandItem());
            if (ph == null) return;
            Vec3 look = mc.player.getViewVector(partial);
            Vec3 pos = mc.player.getEyePosition(partial).add(look.scale(0.3)).add(0, -0.1, 0);
            Vec3 vel = look.scale(ph[0]);
            int col = switch (colour.index()) {
                case 1 -> 0xFFFFFFFF;
                case 2 -> 0xFFFFD24D;
                default -> 0xFF5FD4FF;
            };
            HitResult end = null;
            for (int i = 0; i < 200; i++) {
                Vec3 next = pos.add(vel);
                HitResult r = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                boolean hit = r.getType() != HitResult.Type.MISS;
                Vec3 to = hit ? r.getLocation() : next;
                float fade = 1f - i / 260f;
                Wire.line(g, pos.x, pos.y, pos.z, to.x, to.y, to.z, 1.4f, Draw.rgba(col, Math.max(0.35f, fade)), i % 2 == 0);
                if (hit) {
                    end = r;
                    break;
                }
                pos = next;
                vel = new Vec3(vel.x * 0.99, vel.y * 0.99 - ph[1], vel.z * 0.99);
                if (pos.y < mc.level.getMinY() - 5) break;
            }
            if (end != null && landing.on()) {
                Vec3 p = end.getLocation();
                Wire.box(g, p.x - 0.25, p.y - 0.05, p.z - 0.25, p.x + 0.25, p.y + 0.45, p.z + 0.25, 1.4f, 0xFFFF5A5A, true);
            }
        }
    }

    public static final class Breadcrumbs extends Module {
        private final Setting.Num length = add(new Setting.Num("Length", "How many points of the path are kept.", 200, 20, 1000, 20, ""));
        private final List<double[]> trail = new ArrayList<>();
        private int ticks;

        public Breadcrumbs() {
            super("breadcrumbs", "Breadcrumbs", "Draws a line along the way you walked.", Category.RENDER);
        }

        @Override
        protected void onEnable() {
            trail.clear();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || ++ticks % 3 != 0) return;
            double[] last = trail.isEmpty() ? null : trail.get(trail.size() - 1);
            double x = mc.player.getX();
            double y = mc.player.getY() + 0.1;
            double z = mc.player.getZ();
            if (last != null && Math.abs(last[0] - x) + Math.abs(last[1] - y) + Math.abs(last[2] - z) < 0.3) return;
            trail.add(new double[] {x, y, z});
            while (trail.size() > length.i()) trail.remove(0);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (trail.size() < 2 || !Projector.prepare(mc, partial, w, h)) return;
            for (int i = 1; i < trail.size(); i++) {
                double[] a = trail.get(i - 1);
                double[] b = trail.get(i);
                float t = i / (float) trail.size();
                Wire.line(g, a[0], a[1], a[2], b[0], b[1], b[2], 1.3f, Draw.rgba(Draw.mix(0xFF2E6BFF, 0xFFBFE6FF, t), 0.25f + 0.7f * t), false);
            }
        }
    }
}
