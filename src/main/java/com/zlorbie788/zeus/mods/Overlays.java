package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.ui.Accent;
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
                int col = p[3] == 2 ? com.zlorbie788.zeus.render.EspKit.pick(bedrockColor, 0xFF52F28A) : com.zlorbie788.zeus.render.EspKit.pick(obsidianColor, Accent.WARN);
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
        private final Setting.Choice colour = add(new Setting.Choice("Colour",
                "Cyan, white, gold or red. Theme follows your accent, rainbow fades along the flight.", 0,
                "Cyan", "White", "Gold", "Red", "Theme", "Rainbow"));
        private final Setting.Num width = add(new Setting.Num("Line width", "Thickness of the arc.", 1.6, 0.6, 4.0, 0.2, ""));
        private final Setting.Bool smooth = add(new Setting.Bool("Smooth arc",
                "Runs the physics twice per tick and blends the two, so the path is a curve instead of a chain of jumps.", true));
        private final Setting.Bool landing = add(new Setting.Bool("Landing marker", "Marks where the shot lands.", true));
        private final Setting.Choice marker = add(new Setting.Choice("Marker", "A box, a cross on the ground, or both.", 2,
                "Box", "Cross", "Both")).showIf(() -> landing.on());
        private final Setting.Bool readout = add(new Setting.Bool("Distance readout",
                "Shows how far the landing spot is and how long the shot takes.", true));
        private final Setting.Bool targets = add(new Setting.Bool("Show what it would hit",
                "Outlines the first player or mob in the way and names them.", true));
        private final Setting.Num flight = add(new Setting.Num("Maximum flight", "Ticks to simulate before giving up.", 220, 40, 400, 10, " ticks"));
        private final Setting.Bool water = add(new Setting.Bool("Water slows the shot",
                "Arrows, pearls and potions lose speed and height once they enter water.", true));
        private final Setting.Info now = add(new Setting.Info("Path", this::lines));

        private final List<double[]> pts = new ArrayList<>();
        private final List<net.minecraft.world.entity.LivingEntity> candidates = new ArrayList<>();
        private volatile String note = "Hold a bow, crossbow or a throwable";

        public Trajectories() {
            super("trajectories", "Trajectories",
                    "The true flight path of your bow, crossbow, trident, pearls, snowballs, eggs and potions, with the landing spot, the distance and whatever is standing in the way.",
                    Category.RENDER);
        }

        @Override
        protected void onDisable() {
            pts.clear();
            note = "Off";
        }

        private List<String> lines() {
            return List.of(note);
        }

        private static final class Ammo {
            final double speed;
            final double gravity;
            final double drag;
            final int arrows;

            Ammo(double speed, double gravity, double drag, int arrows) {
                this.speed = speed;
                this.gravity = gravity;
                this.drag = drag;
                this.arrows = arrows;
            }
        }

        private Ammo ammo(Minecraft mc, ItemStack s, float partial) {
            if (s.isEmpty()) return null;
            if (s.is(Items.BOW)) {
                if (!mc.player.isUsingItem() || !mc.player.getUseItem().is(Items.BOW)) return null;
                float charge = Math.min(1f, (mc.player.getTicksUsingItem() + partial) / 20f);
                if (charge < 0.12f) return null;
                double f = (charge * charge + charge * 2f) / 3.0;
                return new Ammo(f * 3.0, 0.05, 0.99, 1);
            }
            if (s.is(Items.CROSSBOW)) {
                if (!s.has(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES)) return null;
                boolean multi = false;
                for (var held : s.getEnchantments().keySet()) {
                    if (held.is(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT)) multi = true;
                }
                return new Ammo(3.15, 0.05, 0.99, multi ? 3 : 1);
            }
            if (s.is(Items.TRIDENT)) return new Ammo(2.5, 0.05, 0.99, 1);
            if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.WIND_CHARGE)) {
                return new Ammo(1.5, 0.03, 0.99, 1);
            }
            if (s.is(Items.EXPERIENCE_BOTTLE)) return new Ammo(0.7, 0.07, 0.99, 1);
            if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Ammo(0.5, 0.05, 0.99, 1);
            return null;
        }

        private int baseColour(float t) {
            return switch (colour.index()) {
                case 1 -> 0xFFFFFFFF;
                case 2 -> 0xFFFFD24D;
                case 3 -> 0xFFFF6B6B;
                case 4 -> com.zlorbie788.zeus.ui.Accent.main();
                case 5 -> hsv(t * 0.72f, 0.62f, 1f);
                default -> 0xFF5FD4FF;
            };
        }

        private static int hsv(float h, float s, float v) {
            float f = (h - (float) Math.floor(h)) * 6f;
            int i = (int) f;
            float p = v * (1f - s);
            float q = v * (1f - s * (f - i));
            float t = v * (1f - s * (1f - (f - i)));
            float r;
            float g;
            float b;
            switch (i % 6) {
                case 0 -> { r = v; g = t; b = p; }
                case 1 -> { r = q; g = v; b = p; }
                case 2 -> { r = p; g = v; b = t; }
                case 3 -> { r = p; g = q; b = v; }
                case 4 -> { r = t; g = p; b = v; }
                default -> { r = v; g = p; b = q; }
            }
            return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null || mc.screen != null) return;
            if (!Projector.prepare(mc, partial, w, h)) return;
            Ammo ammo = ammo(mc, mc.player.getMainHandItem(), partial);
            if (ammo == null) ammo = ammo(mc, mc.player.getOffhandItem(), partial);
            if (ammo == null) {
                note = "Hold a bow, crossbow or a throwable";
                return;
            }
            Vec3 look = mc.player.getViewVector(partial);
            Vec3 start = mc.player.getEyePosition(partial).add(look.scale(0.18)).add(0, -0.12, 0);

            candidates.clear();
            if (targets.on()) {
                for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(72.0))) {
                    if (e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive() && e != mc.player) candidates.add(le);
                }
            }

            pts.clear();
            Vec3 hitPoint = null;
            net.minecraft.world.entity.LivingEntity hitEntity = null;
            boolean hitBlock = false;
            int steps = smooth.on() ? 2 : 1;
            double subDrag = Math.pow(ammo.drag, 1.0 / steps);
            double subGravity = ammo.gravity / steps;
            int max = flight.i();
            Vec3 centreHit = null;
            int centreTicks = 0;

            for (int arrow = 0; arrow < ammo.arrows; arrow++) {
                Vec3 dir = look;
                if (ammo.arrows > 1 && arrow > 0) {
                    double yaw = Math.toRadians(mc.player.getViewYRot(partial) + (arrow == 1 ? 10.0 : -10.0));
                    double pitch = Math.toRadians(mc.player.getViewXRot(partial));
                    double cp = Math.cos(pitch);
                    dir = new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
                }
                pts.clear();
                Vec3 pos = start;
                Vec3 vel = dir.scale(ammo.speed);
                Vec3 hit = null;
                net.minecraft.world.entity.LivingEntity who = null;
                boolean block = false;
                boolean done = false;
                int used = 0;
                for (int t = 0; t < max && !done; t++) {
                    used = t;
                    for (int k = 0; k < steps && !done; k++) {
                        Vec3 next = pos.add(vel.scale(1.0 / steps));
                        if (targets.on()) {
                            for (var le : candidates) {
                                var clipped = le.getBoundingBox().inflate(0.3).clip(pos, next);
                                if (clipped.isPresent()) {
                                    hit = clipped.get();
                                    who = le;
                                    pts.add(new double[] {hit.x, hit.y, hit.z});
                                    done = true;
                                    break;
                                }
                            }
                        }
                        if (done) break;
                        HitResult r = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                        if (r.getType() != HitResult.Type.MISS) {
                            hit = r.getLocation();
                            block = true;
                            pts.add(new double[] {hit.x, hit.y, hit.z});
                            done = true;
                            break;
                        }
                        pos = next;
                        pts.add(new double[] {pos.x, pos.y, pos.z});
                        var fluid = mc.level.getBlockState(BlockPos.containing(pos)).getFluidState();
                        if (water.on() && !fluid.isEmpty()) {
                            vel = vel.scale(0.72).add(0, -subGravity * 0.3, 0);
                        } else {
                            vel = vel.scale(subDrag).add(0, -subGravity, 0);
                        }
                        if (pos.y < mc.level.getMinY() - 12) done = true;
                    }
                }
                if (arrow == 0) {
                    centreHit = hit;
                    centreTicks = used;
                }
                render(g, mc, hit, who, block);
            }

            if (centreHit != null) {
                double dist = mc.player.position().distanceTo(centreHit);
                note = String.format(java.util.Locale.ROOT, "%s · %.1f m away · %.1f s flight", label(mc), dist, centreTicks / 20.0);
            } else {
                note = label(mc);
            }
        }

        private String label(Minecraft mc) {
            ItemStack s = mc.player.getMainHandItem();
            if (s.isEmpty() || ammo(mc, s, 0f) == null) s = mc.player.getOffhandItem();
            String name = com.zlorbie788.zeus.core.Names.item(s).replace('_', ' ');
            if (name.isEmpty()) name = "shot";
            if (s.is(Items.BOW) && mc.player.isUsingItem()) {
                int pct = Math.round(Math.min(1f, mc.player.getTicksUsingItem() / 20f) * 100f);
                return "Bow at " + pct + "%";
            }
            if (s.is(Items.CROSSBOW)) return "Crossbow";
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        private void render(GuiGraphics g, Minecraft mc, Vec3 hitPoint, net.minecraft.world.entity.LivingEntity hitEntity,
                            boolean hitBlock) {
            if (pts.size() < 2) return;
            float thick = width.f();
            for (int i = 1; i < pts.size(); i++) {
                double[] a = pts.get(i - 1);
                double[] b = pts.get(i);
                float t = i / (float) pts.size();
                int c = baseColour(t);
                float alpha = 0.92f - 0.5f * t;
                Wire.line(g, a[0], a[1], a[2], b[0], b[1], b[2], thick * (1f - 0.35f * t), Draw.rgba(c, alpha), i % 3 == 0);
            }
            if (hitPoint == null) return;
            int c = baseColour(0.75f);
            if (landing.on()) {
                if (marker.index() != 1) {
                    Wire.box(g, hitPoint.x - 0.22, hitPoint.y - 0.03, hitPoint.z - 0.22,
                            hitPoint.x + 0.22, hitPoint.y + 0.42, hitPoint.z + 0.22, thick, Draw.rgba(c, 0.95f), true);
                }
                if (marker.index() != 0) {
                    Wire.floor(g, hitPoint.x - 0.26, hitPoint.y + 0.015, hitPoint.z - 0.26, 0.52, thick, Draw.rgba(c, 0.9f));
                }
            }
            if (hitEntity != null && targets.on()) {
                var box = hitEntity.getBoundingBox().inflate(0.06);
                Wire.box(g, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, thick * 0.9f, 0xFFFF5A5A, true);
            }
            if (!readout.on() || !hitBlock) return;
            float[] screen = new float[2];
            if (Projector.screenPoint(hitPoint.x, hitPoint.y + 0.55, hitPoint.z, 20f, screen) < 0) return;
            String read = String.format(java.util.Locale.ROOT, "%.1f m", mc.player.position().distanceTo(hitPoint));

            Draw.text(g, read, Math.round(screen[0]) + 4, Math.round(screen[1]) - 4, Draw.rgba(c, 0.95f));
        }
    }

    public static final class JumpCircles extends Module {
        private final Setting.Num size = add(new Setting.Num("Size", "Radius the ring grows to.", 0.9, 0.3, 3.0, 0.1, " blocks"));
        private final Setting.Num duration = add(new Setting.Num("Duration", "How long one ring lives.", 0.6, 0.2, 2.0, 0.05, " s"));
        private final Setting.Num intensity = add(new Setting.Num("Intensity", "How strong the rings are.", 80, 10, 100, 5, " %"));
        private final Setting.Num count = add(new Setting.Num("Rings", "How many rings each jump throws.", 2, 1, 4, 1, ""));
        private final Setting.Choice colour = add(new Setting.Choice("Colour", "Ring colour.", 4,
                "Cyan", "White", "Gold", "Purple", "Theme"));
        private final Setting.Bool land = add(new Setting.Bool("Also on landing", "Yes for a ring when you touch down.", true));

        private final List<double[]> rings = new ArrayList<>();
        private boolean wasGround = true;

        public JumpCircles() {
            super("jumpcircles", "Jump Circles",
                    "A short ring of light at your feet every time you jump or land, so movement reads at a glance.",
                    Category.RENDER);
            alias("circles", "jumpring");
        }

        @Override
        protected void onEnable() {
            rings.clear();
            wasGround = true;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) return;
            boolean ground = mc.player.onGround();
            if (ground != wasGround) {
                if (!ground || land.on()) spawn(mc);
                wasGround = ground;
            }
            long now = System.currentTimeMillis();
            rings.removeIf(r -> now - (long) r[3] > duration.get() * 1000.0);
        }

        private void spawn(Minecraft mc) {
            for (int i = 0; i < count.i(); i++) {
                rings.add(new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        System.currentTimeMillis() + i * 70.0});
            }
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (rings.isEmpty() || mc.player == null) return;
            if (!Projector.prepare(mc, partial, w, h)) return;
            long now = System.currentTimeMillis();
            double life = duration.get() * 1000.0;
            int base = switch (colour.index()) {
                case 0 -> 0xFF5FD4FF;
                case 1 -> 0xFFFFFFFF;
                case 2 -> 0xFFFFD24D;
                case 3 -> 0xFFB77BFF;
                default -> com.zlorbie788.zeus.ui.Accent.main();
            };
            for (double[] r : rings) {
                double age = now - r[3];
                if (age < 0 || age > life) continue;
                float t = (float) (age / life);
                float radius = (float) (size.get() * (0.35 + t * 0.9));
                float alpha = (1f - t) * (intensity.f() / 100f);
                float thick = Math.max(0.8f, 2.2f * (1f - t));
                int seg = 26;
                for (int i = 0; i < seg; i++) {
                    double a1 = Math.PI * 2 * i / seg;
                    double a2 = Math.PI * 2 * (i + 1) / seg;
                    Wire.line(g,
                            r[0] + Math.cos(a1) * radius, r[1] + 0.04, r[2] + Math.sin(a1) * radius,
                            r[0] + Math.cos(a2) * radius, r[1] + 0.04, r[2] + Math.sin(a2) * radius,
                            thick, Draw.rgba(base, alpha), false);
                }
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
