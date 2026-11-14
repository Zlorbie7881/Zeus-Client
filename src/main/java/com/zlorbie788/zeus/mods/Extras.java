package com.zlorbie788.zeus.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.ui.Draw;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Extras {
    private Extras() {}

    public static final class TimeChanger extends Module {
        private final Setting.Choice preset = add(new Setting.Choice("Time", "The time of day to show.", 1, "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom"));
        private final Setting.Num custom = add(new Setting.Num("Custom time", "Ticks, 0 to 24000. 6000 is noon, 18000 is midnight.", 6000, 0, 24000, 250, "").showIf(() -> preset.index() == 6));
        private final Setting.Bool freeze = add(new Setting.Bool("Freeze the sky", "The sun and moon stop. Off: the day keeps moving from that time.", true));
        private static final long[] VALUES = {0L, 1000L, 6000L, 12000L, 13000L, 18000L};
        private long clock;

        public TimeChanger() {
            super("timechanger", "Time Changer", "Choose the time of day you see. Only your screen changes.", Category.RENDER);
        }

        private long wanted() {
            return preset.index() == 6 ? custom.i() : VALUES[preset.index()];
        }

        @Override
        protected void onEnable() {
            clock = wanted();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null) return;
            if (!freeze.on()) clock++;
            else clock = wanted();
            mc.level.setTimeFromServer(mc.level.getGameTime(), clock, false);
        }

        @Override
        public String info() {
            return preset.value();
        }
    }

    public static final class WeatherChanger extends Module {
        private final Setting.Choice mode = add(new Setting.Choice("Weather", "The weather you see.", 0, "Clear", "Rain", "Thunderstorm"));

        public WeatherChanger() {
            super("weatherchanger", "Weather Changer", "Choose the weather you see. Only your screen changes.", Category.RENDER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null) return;
            var weather = (com.zlorbie788.zeus.mixin.LevelWeatherAccessor) (Object) mc.level;
            weather.zeus$setRain(mode.index() == 0 ? 0f : 1f);
            weather.zeus$setThunder(mode.index() == 2 ? 1f : 0f);
        }

        @Override
        public String info() {
            return mode.value();
        }
    }

    public static final class ChunkBorders extends Module {
        private final Setting.Num radius = add(new Setting.Num("Chunks around", "0 shows only the chunk you are in.", 1, 0, 3, 1, ""));
        private final Setting.Num height = add(new Setting.Num("Height", "How far up and down the lines go.", 32, 8, 96, 8, " blocks"));
        private final Setting.Bool levels = add(new Setting.Bool("Section lines", "Rings every 16 blocks up the corners of your own chunk.", true));

        public ChunkBorders() {
            super("chunkborders", "Chunk Borders", "Draws the edges of chunks in the world.", Category.RENDER);
            light();
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || !Projector.prepare(mc, partial, w, h)) return;
            int pcx = mc.player.getBlockX() >> 4;
            int pcz = mc.player.getBlockZ() >> 4;
            int r = radius.i();
            double y0 = mc.player.getY() - height.get();
            double y1 = mc.player.getY() + height.get();
            int accent = Accent.main();
            for (int dx = -r; dx <= r + 1; dx++) {
                for (int dz = -r; dz <= r + 1; dz++) {
                    boolean own = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                    double x = (pcx + dx) * 16.0;
                    double z = (pcz + dz) * 16.0;
                    Wire.line(g, x, y0, z, x, y1, z, own ? 1.4f : 1f, Draw.rgba(own ? Accent.WARN : accent, own ? 0.9f : 0.5f), false);
                }
            }
            if (levels.on()) {
                double x1 = pcx * 16.0;
                double z1 = pcz * 16.0;
                int first = (int) (Math.floor(y0 / 16.0) * 16);
                for (int y = first; y <= y1; y += 16) {
                    Wire.floor(g, x1, y, z1, 16, 1f, Draw.rgba(Accent.WARN, 0.55f));
                }
            }
        }
    }

    public static final class BlockOutline extends Module {
        private final Setting.Num thick = add(new Setting.Num("Line width", "Thickness of the outline.", 1.6, 1, 4, 0.2, " px"));
        private final Setting.Bool rainbow = add(new Setting.Bool("Colour shift", "The outline slowly changes colour.", true));

        public BlockOutline() {
            super("blockoutline", "Block Outline", "A bright outline around the block you are looking at.", Category.RENDER);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null || mc.screen != null) return;
            if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return;
            if (!Projector.prepare(mc, partial, w, h)) return;
            BlockPos pos = bhr.getBlockPos();
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
            if (shape.isEmpty()) return;
            AABB b = shape.bounds();
            float t = (System.currentTimeMillis() % 4000L) / 4000f;
            int col = rainbow.on() ? Draw.mix(Accent.main(), Accent.second(), 0.5f + 0.5f * (float) Math.sin(t * 6.2832)) : Accent.main();
            double e = 0.002;
            Wire.box(g, pos.getX() + b.minX - e, pos.getY() + b.minY - e, pos.getZ() + b.minZ - e,
                    pos.getX() + b.maxX + e, pos.getY() + b.maxY + e, pos.getZ() + b.maxZ + e, thick.f(), Draw.rgba(col, 0.95f), true);
        }
    }

    public static final class Zoom extends Module {

        private static volatile float live = 1f;

        public static float factorNow() {
            return live;
        }

        private final Setting.Num factor = add(new Setting.Num("Zoom", "How much closer.", 3, 1.5, 8, 0.5, "x"));
        private final Setting.Bool smooth = add(new Setting.Bool("Smooth", "Slide in and out instead of snapping, like a real scope.", true));
        private final Setting.Num sense = add(new Setting.Num("Mouse sensitivity while zoomed", "Scales your mouse sensitivity while the zoom is in, so aiming stays precise. 100 keeps it exactly as it is.", 45, 10, 100, 5, " %"));
        private final Setting.Bool keepThird = add(new Setting.Bool("Works in third person", "The zoom is a field of view change, so it is the same in first and third person. Turn this off to have the zoom switch to first person while it is in.", true));
        private double originalSense = -1;
        private float shown = 1f;
        private long lastMs;
        private int savedPerspective = -1;
        private boolean enabled;
        private int frames;
        private int originalFov = -1;

        public Zoom() {
            super("zoom", "Zoom", "Look closer. Bind a key to hold it with the key on its card.", Category.UTILITY);
            alias("scope", "spyglass", "magnify");
            sense.onChange(this::applySense);
        }

        private void applySense() {
            Minecraft mc = Minecraft.getInstance();
            if (!enabled || originalSense < 0) return;
            double want = originalSense * (sense.f() / 100.0);
            if (Math.abs(mc.options.sensitivity().get() - want) > 1e-4) mc.options.sensitivity().set(want);
        }

        private void frame(Minecraft mc) {
            long now = System.nanoTime() / 1_000_000L;
            float dt = lastMs == 0 ? 0.016f : Math.min(0.1f, (now - lastMs) / 1000f);
            lastMs = now;
            float goal = factor.f();
            if (!smooth.on()) shown = goal;
            else {
                shown += (goal - shown) * (1f - (float) Math.exp(-dt * 16f));
                if (Math.abs(goal - shown) < 0.004f) shown = goal;
            }
            frames++;
            if (com.zlorbie788.zeus.render.Projector.hookSeen()) {
                live = shown;
                if (originalFov >= 0) {
                    mc.options.fov().set(originalFov);
                    originalFov = -1;
                }
            } else if (frames > 40) {
                if (originalFov < 0) originalFov = mc.options.fov().get();
                int fov = Math.round((float) Math.max(30.0, originalFov / shown));
                if (fov != mc.options.fov().get()) mc.options.fov().set(fov);
            }
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            enabled = true;
            originalSense = mc.options.sensitivity().get();
            shown = 1f;
            lastMs = 0;
            frames = 0;
            savedPerspective = -1;
            if (!keepThird.on() && mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON) {
                savedPerspective = mc.options.getCameraType().ordinal();
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
            applySense();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!enabled) return;
            if (!smooth.on() || mc.screen != null) frame(mc);
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (enabled && mc.screen == null && smooth.on()) frame(mc);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            enabled = false;
            live = 1f;
            if (originalFov >= 0) mc.options.fov().set(originalFov);
            if (originalSense >= 0) mc.options.sensitivity().set(originalSense);
            if (savedPerspective >= 0) {
                net.minecraft.client.CameraType[] all = net.minecraft.client.CameraType.values();
                if (savedPerspective < all.length) mc.options.setCameraType(all[savedPerspective]);
            }
            originalFov = -1;
            originalSense = -1;
            shown = 1f;
            savedPerspective = -1;
        }

        @Override
        public String info() {
            return factor.text();
        }
    }

    public static final class Waypoints extends Module {
        private record Wp(String name, String dim, double x, double y, double z, int color) {}

        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final int[] COLORS = {0xFF3DB8FF, Accent.OK, Accent.WARN, 0xFFFF7A86, 0xFFB77BFF, Accent.HIGH};
        private final List<Wp> points = new ArrayList<>();
        private final Setting.Bool deaths = add(new Setting.Bool("Mark where you die", "Adds a waypoint at the place you died.", true));
        private final Setting.Text customName = add(new Setting.Text("Waypoint name", "Name for the coordinate waypoint.", "Custom waypoint", 32).temporary());
        private final Setting.Text customX = add(new Setting.Text("Waypoint X", "World X coordinate.", "0", 16).temporary());
        private final Setting.Text customY = add(new Setting.Text("Waypoint Y", "World Y coordinate.", "64", 16).temporary());
        private final Setting.Text customZ = add(new Setting.Text("Waypoint Z", "World Z coordinate.", "0", 16).temporary());
        private final Setting.Num fade = add(new Setting.Num("Hide when closer than", "Markers vanish when you are this close.", 4, 0, 20, 1, " blocks"));
        private final Setting.Bool labels = add(new Setting.Bool("Names and distance", "Text under every marker.", true));
        private boolean wasDead;
        private boolean loaded;

        public Waypoints() {
            super("waypoints", "Waypoints", "Save places and see them through walls.", Category.UTILITY);
            add(new Setting.Action("Add waypoint here", "Saves your current position.", this::addHere));
            add(new Setting.Action("Add waypoint at coordinates", "Uses the X, Y and Z fields above in the current dimension.", this::addCoordinates));
            add(new Setting.Action("Remove the nearest", "Deletes the waypoint closest to you in this dimension.", this::removeNearest));
            add(new Setting.Action("Remove all", "Deletes every waypoint in this dimension.", this::clearHere));
            add(new Setting.Info("Saved places", this::lines));
        }

        private static String dim(Minecraft mc) {
            return mc.level == null ? "" : mc.level.dimension().toString();
        }

        private static Path file() {
            return FabricLoader.getInstance().getConfigDir().resolve(serverFile());
        }
        private static String serverFile() {
            String s = Brain.ctx().server;
            return s.isEmpty() || s.equals("singleplayer") ? "zeus-waypoints.json" : "zeus-waypoints-" + s.replaceAll("[^a-z0-9_-]+", "-") + ".json";
        }

        private void load() {
            if (loaded) return;
            loaded = true;
            try {
                if (!Files.isRegularFile(file())) return;
                JsonElement e = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
                if (!e.isJsonArray()) return;
                for (JsonElement el : e.getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    points.add(new Wp(o.get("name").getAsString(), o.get("dim").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("color").getAsInt()));
                }
            } catch (IOException | RuntimeException ex) {
                Zeus.LOG.warn("Could not read the waypoints", ex);
            }
        }

        private void save() {
            try {
                JsonArray a = new JsonArray();
                for (Wp w : points) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", w.name());
                    o.addProperty("dim", w.dim());
                    o.addProperty("x", w.x());
                    o.addProperty("y", w.y());
                    o.addProperty("z", w.z());
                    o.addProperty("color", w.color());
                    a.add(o);
                }
                Files.writeString(file(), GSON.toJson(a), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException ex) {
                Zeus.LOG.warn("Could not save the waypoints", ex);
            }
        }

        private void addPoint(String name, int color) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            load();
            points.add(new Wp(name, dim(mc), mc.player.getX(), mc.player.getY(), mc.player.getZ(), color));
            save();
        }

        public boolean addAt(String name, double x, double y, double z) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return false;
            load();
            String d = dim(mc);
            for (Wp w : points) if (w.dim().equals(d) && Math.abs(w.x() - x) < 24 && Math.abs(w.z() - z) < 24) return false;
            points.add(new Wp(name, d, x, y, z, COLORS[points.size() % COLORS.length]));
            save();
            return true;
        }

        private void addHere() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            load();
            int n = 1;
            for (Wp w : points) if (w.name().startsWith("Waypoint")) n++;
            addPoint("Waypoint " + n, COLORS[n % COLORS.length]);
            Zeus.toast("Waypoint saved", true);
        }

        private void addCoordinates() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) { Zeus.toast("Join a world before adding a waypoint", false); return; }
            try {
                double x = Double.parseDouble(customX.get().trim());
                double y = Double.parseDouble(customY.get().trim());
                double z = Double.parseDouble(customZ.get().trim());
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < mc.level.getMinY() - 64 || y > mc.level.getMaxY() + 64) throw new NumberFormatException();
                String name = customName.get().trim();
                if (name.isEmpty()) name = "Custom waypoint";
                boolean added = addAt(name, x, y, z);
                Zeus.toast(added ? "Waypoint added at " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z) : "A waypoint is already saved nearby", added);
            } catch (NumberFormatException ex) {
                Zeus.toast("Enter valid X, Y and Z coordinates", false);
            }
        }

        private void removeNearest() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            load();
            Wp best = null;
            double bd = Double.MAX_VALUE;
            for (Wp w : points) {
                if (!w.dim().equals(dim(mc))) continue;
                double d = mc.player.distanceToSqr(w.x(), w.y(), w.z());
                if (d < bd) {
                    bd = d;
                    best = w;
                }
            }
            if (best != null) {
                points.remove(best);
                save();
                Zeus.toast("Removed " + best.name(), true);
            }
        }

        private void clearHere() {
            Minecraft mc = Minecraft.getInstance();
            load();
            points.removeIf(w -> w.dim().equals(dim(mc)));
            save();
            Zeus.toast("Waypoints cleared", true);
        }

        /** A saved place in this dimension by name (case and spaces ignored), as {x, y, z}, or null. */
        public double[] findNamed(String name) {
            Minecraft mc = Minecraft.getInstance();
            load();
            String want = name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
            for (Wp w : points) {
                if (!w.dim().equals(dim(mc))) continue;
                if (w.name().toLowerCase(java.util.Locale.ROOT).replace(" ", "").equals(want)) return new double[] {w.x(), w.y(), w.z()};
            }
            return null;
        }

        /** Saved places in this dimension, nearest first, for the AI's context: "name at x y z, N blocks". */
        public List<String> forAi(int max) {
            Minecraft mc = Minecraft.getInstance();
            load();
            List<Wp> here = new ArrayList<>();
            for (Wp w : points) if (w.dim().equals(dim(mc))) here.add(w);
            if (mc.player != null) here.sort(java.util.Comparator.comparingDouble(w -> mc.player.distanceToSqr(w.x(), w.y(), w.z())));
            List<String> out = new ArrayList<>();
            for (Wp w : here) {
                if (out.size() >= max) break;
                String d = mc.player == null ? "" : ", " + Math.round(Math.sqrt(mc.player.distanceToSqr(w.x(), w.y(), w.z()))) + " blocks";
                out.add(w.name() + " at " + Math.round(w.x()) + " " + Math.round(w.y()) + " " + Math.round(w.z()) + d);
            }
            return out;
        }

        private List<String> lines() {
            Minecraft mc = Minecraft.getInstance();
            load();
            List<String> out = new ArrayList<>();
            for (Wp w : points) {
                if (!w.dim().equals(dim(mc))) continue;
                String d = mc.player == null ? "" : "  " + Math.round(Math.sqrt(mc.player.distanceToSqr(w.x(), w.y(), w.z()))) + " m";
                out.add(w.name() + "  " + Math.round(w.x()) + " " + Math.round(w.y()) + " " + Math.round(w.z()) + d);
                if (out.size() >= 8) break;
            }
            if (out.isEmpty()) out.add("None here yet");
            return out;
        }

        @Override
        protected void onEnable() {
            load();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            boolean dead = mc.player.isDeadOrDying();
            if (dead && !wasDead && deaths.on()) addPoint("Death", Accent.BAD);
            wasDead = dead;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || points.isEmpty() || !Projector.prepare(mc, partial, w, h)) return;
            String here = dim(mc);
            float[] p = new float[2];
            double ex = Projector.eyeX();
            double ey = Projector.eyeY();
            double ez = Projector.eyeZ();
            for (Wp wp : points) {
                if (!wp.dim().equals(here)) continue;
                double dx = wp.x() - ex;
                double dy = wp.y() + 1 - ey;
                double dz = wp.z() - ez;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < fade.get() || !Projector.project(wp.x(), wp.y() + 1.0, wp.z(), p)) continue;
                int x = Math.round(p[0]);
                int y = Math.round(p[1]);
                if (x < -20 || x > w + 20 || y < -20 || y > h + 20) continue;
                int c = wp.color();
                Draw.line(g, x, y - 6, x + 5, y, 1.4f, c);
                Draw.line(g, x + 5, y, x, y + 6, 1.4f, c);
                Draw.line(g, x, y + 6, x - 5, y, 1.4f, c);
                Draw.line(g, x - 5, y, x, y - 6, 1.4f, c);
                g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);
                if (labels.on()) {
                    String text = wp.name() + "  " + Math.round(dist) + " m";
                    int tw = Draw.width(text);
                    Draw.rrect(g, x - tw / 2 - 3, y + 9, tw + 6, 12, 4, 0xB00B0E16);
                    Draw.text(g, text, x - tw / 2, y + 11, c);
                }
            }
        }

        @Override
        public String info() {
            Minecraft mc = Minecraft.getInstance();
            int n = 0;
            for (Wp w : points) if (w.dim().equals(dim(mc))) n++;
            return n > 0 ? Integer.toString(n) : null;
        }
    }

    public static final class AutoReplenish extends Module {
        private final Setting.Num left = add(new Setting.Num("Refill at", "Swap in a new stack when this many or fewer are left.", 2, 1, 32, 1, " items"));
        private int cooldown;

        public AutoReplenish() {
            super("autoreplenish", "Auto Replenish", "Swaps in a full stack of the same item when your hand runs low.", Category.UTILITY);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (cooldown-- > 0) return;
            int slot = Inv.selected();
            ItemStack cur = Inv.get(slot);
            if (cur.isEmpty() || cur.getMaxStackSize() <= 1 || cur.getCount() > left.i()) return;
            int best = -1;
            int bestCount = cur.getCount();
            for (int i = 9; i < 36; i++) {
                ItemStack s = Inv.get(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, cur) && s.getCount() > bestCount) {
                    best = i;
                    bestCount = s.getCount();
                }
            }
            if (best < 0) return;
            Inv.click(Inv.menuSlot(best), slot, ClickType.SWAP);
            cooldown = 6;
        }
    }

    public static final class DurabilityAlert extends Module {
        private final Setting.Num percent = add(new Setting.Num("Warn below", "Percent of durability left.", 10, 2, 50, 1, "%"));
        private final Set<String> warned = new HashSet<>();
        private int ticks;

        public DurabilityAlert() {
            super("durabilityalert", "Durability Alert", "A message when your tool or armor is about to break.", Category.UTILITY);
        }

        @Override
        protected void onEnable() {
            warned.clear();
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || ++ticks % 20 != 0) return;
            check("hand", mc.player.getMainHandItem());
            for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                check(s.name(), mc.player.getItemBySlot(s));
            }
        }

        private void check(String key, ItemStack s) {
            if (s.isEmpty() || !s.isDamaged()) {
                warned.remove(key);
                return;
            }
            double left = (s.getMaxDamage() - s.getDamageValue()) * 100.0 / Math.max(1, s.getMaxDamage());
            if (left <= percent.get()) {
                if (warned.add(key)) Zeus.toast(s.getHoverName().getString() + " is almost broken (" + String.format(Locale.ROOT, "%.0f", left) + "%)", false);
            } else if (left > percent.get() + 5) {
                warned.remove(key);
            }
        }
    }
}
