package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class SoundEsp extends Module {

    private record Sound(Identifier id, SoundSource source, double x, double y, double z,
                         float volume, long at, long block, boolean alert) {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static volatile SoundEsp HOOK;

    private static final int MAX_STORED = 600;

    private final ConcurrentLinkedDeque<Sound> recent = new ConcurrentLinkedDeque<>();

    private final Setting.Num range = add(new Setting.Num("Range",
            "Only sounds this close are marked.", 96, 16, 256, 16, " blocks"));

    private final Setting.Num lifetime = add(new Setting.Num("Marker lifetime",
            "How long a box stays after the sound was heard.", 6, 1, 30, 1, " s"));

    private final Setting.Num maxMarkers = add(new Setting.Num("Max markers",
            "The nearest ones are kept when there are more.", 60, 5, 200, 5, ""));

    private final Setting.Num minVolume = add(new Setting.Num("Min volume",
            "Ignore sounds played quieter than this. 0 keeps everything.", 0.05, 0.0, 2.0, 0.05, ""));

    private final Setting.Bool blocks = add(new Setting.Bool("Block sounds",
            "Levers, buttons, pistons, chests, note blocks, dispensers and every other block sound.", true));

    private final Setting.Bool hostile = add(new Setting.Bool("Hostile mobs",
            "Zombies, skeletons, creepers and every other hostile mob.", true));

    private final Setting.Bool neutral = add(new Setting.Bool("Neutral mobs",
            "Villagers, animals, golems and everything else that is not hostile.", true));

    private final Setting.Bool players = add(new Setting.Bool("Players",
            "Other players stepping, eating, breaking blocks and so on.", true));

    private final Setting.Bool ambient = add(new Setting.Bool("Ambient and weather",
            "Cave ambience, rain, thunder and similar.", false));

    private final Setting.Bool music = add(new Setting.Bool("Music and records",
            "Background music and jukebox records.", false));

    private final Setting.Bool interfaceSounds = add(new Setting.Bool("Interface",
            "Menu clicks and other screen sounds. They have no real position, so this is off by default.", false));

    private final Setting.Text only = add(new Setting.Text("Only these sounds",
            "Comma separated parts of sound ids, for example: entity.villager,block.lever. Empty means every sound that "
                    + "passes the switches above.", "", 200));

    private final Setting.Text ignore = add(new Setting.Text("Ignore these sounds",
            "Comma separated parts of sound ids to never mark, for example: entity.player.attack,step.", "", 200));

    private final Setting.Bool ignoreSelf = add(new Setting.Bool("Ignore my own sounds",
            "Do not mark sounds that are played by you.", true));

    private final Setting.Num repeat = add(new Setting.Num("Repeat window",
            "The same sound at the same block within this many milliseconds is only marked once, so birds and villagers "
                    + "cannot flood the screen.", 400, 0, 3000, 50, " ms"));

    private final Setting.Bool labels = add(new Setting.Bool("Labels",
            "Name and distance on the nearest sounds.", true));

    private final Setting.Num labelDistance = add(new Setting.Num("Label distance",
            "Only sounds this close get a name tag.", 40, 8, 128, 8, " blocks"));

    private final Setting.Bool miningAlert = add(new Setting.Bool("Mining alert",
            "Always shows blocks being broken by someone else, in red with a pickaxe, even when they would be filtered "
                    + "out. The fastest way to notice another player digging nearby.", true));

    private final Setting.Bool playerAlert = add(new Setting.Bool("Player alert",
            "Always shows sounds made by other players, in red and prefixed, even when the filters would hide them. "
                    + "Footsteps, jumps, eating and mining all count.", true));

    private final Setting.Bool byCategory = add(new Setting.Bool("Colour by category",
            "Blocks, mobs, players and ambience each get their own colour. Off uses the colour picked in the look "
                    + "settings.", true));

    private final EspKit kit = new EspKit(this, 1, false);

    private final Setting.Action presetVillager = add(new Setting.Action("Preset: villagers",
            "Only marks villager sounds.", () -> applyPreset("entity.villager")));

    private final Setting.Action presetRedstone = add(new Setting.Action("Preset: redstone",
            "Levers, buttons, plates, pistons, dispensers, droppers, comparators, repeaters and redstone torches.",
            () -> applyPreset("block.lever,block.stone_button,block.wooden_button,block.piston,block.pressure_plate,"
                    + "block.tripwire,block.dispenser,block.dropper,block.comparator,block.repeater,"
                    + "block.redstone_torch")));

    private final Setting.Action presetChests = add(new Setting.Action("Preset: chests and barrels",
            "Chests, trapped chests, barrels, shulker boxes and ender chests opening and closing.",
            () -> applyPreset("block.chest,block.barrel,block.shulker_box,block.ender_chest")));

    private final Setting.Action presetPlayers = add(new Setting.Action("Preset: players",
            "Only marks sounds made by other players.", () -> applyPreset("entity.player")));

    private final Setting.Action presetHostile = add(new Setting.Action("Preset: hostile mobs",
            "Only marks the common hostile mobs.", () -> applyPreset("entity.zombie,entity.skeleton,entity.creeper,"
                    + "entity.spider,entity.enderman,entity.witch,entity.blaze,entity.phantom,entity.slime,"
                    + "entity.warden,entity.pillager,entity.ravager,entity.hoglin,entity.piglin,entity.ghast,"
                    + "entity.wither_skeleton")));

    private final Setting.Action presetClear = add(new Setting.Action("Clear the sound filter",
            "Goes back to every sound that passes the switches.", () -> applyPreset("")));

    private final Setting.Action logRecent = add(new Setting.Action("Log the sounds heard",
            "Writes the ids of the sounds heard lately to chat, so you can copy the exact name into a filter.",
            this::logRecent));

    private final Setting.Action clear = add(new Setting.Action("Clear markers",
            "Removes every marker that is showing now.", recent::clear));

    public SoundEsp() {
        super("soundesp", "Sound ESP",
                "Shows where sounds come from: villagers, mobs, players, levers and pistons, chests, anything you filter "
                        + "for, boxed in the world for a few seconds after it is heard.",
                Category.RENDER);
        HOOK = this;
    }

    public static void capture(Entity except, double x, double y, double z, Identifier id, SoundSource source,
                               float volume, float pitch) {
        SoundEsp hook = HOOK;
        if (hook == null || id == null || source == null) return;
        try {
            if (!hook.enabled()) return;
            hook.note(except, x, y, z, id, source, volume);
        } catch (Throwable ignored) {
        }
    }

    private void note(Entity except, double x, double y, double z, Identifier id, SoundSource source, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (volume < minVolume.f()) return;
        if (ignoreSelf.on() && except == mc.player) return;

        double dx = x - mc.player.getX();
        double dy = y - mc.player.getY();
        double dz = z - mc.player.getZ();
        double r = range.get();
        if (dx * dx + dy * dy + dz * dz > r * r) return;

        String path = id.getPath().toLowerCase(Locale.ROOT);
        boolean mining = path.startsWith("block.") && path.endsWith(".break");
        boolean fromPlayer = path.startsWith("entity.player.");
        boolean alert = (mining && miningAlert.on()) || (fromPlayer && playerAlert.on());
        if (!alert) {
            if (!categoryOn(source)) return;
            String onlyText = only.get();
            if (!onlyText.isBlank() && !matches(onlyText, path)) return;
            if (matches(ignore.get(), path)) return;
        }

        long now = System.currentTimeMillis();
        long block = BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        Sound last = recent.peekLast();
        long window = repeat.i();
        if (last != null && window > 0 && last.block() == block && last.id().equals(id)
                && now - last.at() < window) {
            return;
        }

        recent.addLast(new Sound(id, source, x, y, z, volume, now, block, alert));
        while (recent.size() > MAX_STORED) recent.pollFirst();
    }

    private boolean categoryOn(SoundSource source) {
        return switch (source) {
            case BLOCKS -> blocks.on();
            case HOSTILE -> hostile.on();
            case NEUTRAL -> neutral.on();
            case PLAYERS -> players.on();
            case AMBIENT, WEATHER -> ambient.on();
            case MUSIC, RECORDS -> music.on();
            case MASTER, UI, VOICE -> interfaceSounds.on();
        };
    }

    private static boolean matches(String list, String path) {
        if (list == null || list.isBlank()) return false;
        for (String part : list.split(",")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (!p.isEmpty() && path.contains(p)) return true;
        }
        return false;
    }

    private void applyPreset(String filter) {
        only.set(filter);
        Zeus.toast(filter.isBlank() ? "Sound ESP: showing every sound" : "Sound ESP: filter set", true);
    }

    private void logRecent() {
        Minecraft mc = Minecraft.getInstance();
        Set<String> ids = new LinkedHashSet<>();
        for (Sound s : recent) {
            ids.add(s.id().toString());
            if (ids.size() >= 24) break;
        }
        if (ids.isEmpty()) {
            Zeus.toast("Sound ESP: nothing heard yet", false);
            return;
        }
        String line = String.join(", ", ids);
        Zeus.LOG.info("Sound ESP heard: {}", line);
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("[Zeus] sounds: " + line).withStyle(ChatFormatting.GRAY), false);
        }
    }

    @Override
    protected void onDisable() {
        recent.clear();
    }

    @Override
    protected void cleanSlate() {
        recent.clear();
    }

    @Override
    public String info() {
        return live().size() + " sound(s)";
    }

    @Override
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.level == null || mc.player == null) return;
        List<Sound> live = live();
        if (live.isEmpty()) return;
        if (!Projector.prepare(mc, partial, w, h)) return;

        long now = System.currentTimeMillis();
        long life = Math.max(500L, (long) (lifetime.f() * 1000f));
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        double r = range.get();
        double r2 = r * r;
        double label2 = labelDistance.f() * labelDistance.f();
        int cap = maxMarkers.i();

        List<Sound> sorted = new ArrayList<>();
        for (Sound s : live) {
            if (dist2(s, px, py, pz) <= r2) sorted.add(s);
        }
        if (sorted.isEmpty()) return;
        sorted.sort(Comparator.comparingDouble(s -> dist2(s, px, py, pz)));
        if (sorted.size() > cap) sorted = new ArrayList<>(sorted.subList(0, cap));

        for (int i = sorted.size() - 1; i >= 0; i--) {
            Sound s = sorted.get(i);
            long age = now - s.at();
            float fade = 1f - Math.min(1f, age / (float) life);
            float alpha = 0.15f + 0.85f * fade;
            int color = s.alert() ? 0xFFFF3B3B : colour(s.source());
            AABB box = new AABB(s.x() - 0.35, s.y() - 0.35, s.z() - 0.35, s.x() + 0.35, s.y() + 0.35, s.z() + 0.35);
            kit.box(g, box, color, alpha, i < 25);
            double d2 = dist2(s, px, py, pz);
            if (labels.on() && i < 8 && d2 < label2) {
                float[] onScreen = new float[2];
                if (Projector.project(s.x(), box.maxY + 0.15, s.z(), onScreen)) {
                    String tag = s.id().getPath().startsWith("entity.player.") ? "PLAYER " : "MINING ";
                    String name = (s.alert() ? tag : "") + shortName(s.id());
                    kit.tag(g, name, Math.round(Math.sqrt(d2)) + "m " + arrowTo(mc, s.x(), s.z()),
                            Math.round(onScreen[0]), Math.round(onScreen[1]), color, alpha, w, h);
                }
            }
            kit.tracer(g, w, h, s.x(), s.y(), s.z(), color, alpha);
        }
    }

    private List<Sound> live() {
        long life = Math.max(500L, (long) (lifetime.f() * 1000f));
        long now = System.currentTimeMillis();
        List<Sound> out = new ArrayList<>();
        for (Sound s : recent) {
            if (now - s.at() <= life) out.add(s);
            else recent.remove(s);
        }
        return out;
    }

    private int colour(SoundSource source) {
        if (!byCategory.on()) return 0xFFFFFFFF;
        return switch (source) {
            case BLOCKS -> 0xFFFF9F43;
            case HOSTILE -> 0xFFFF4D5E;
            case NEUTRAL -> 0xFF4DFF88;
            case PLAYERS -> 0xFF4DA3FF;
            case AMBIENT, WEATHER -> 0xFFB06BFF;
            case MUSIC, RECORDS -> 0xFFFF7AB6;
            case MASTER, UI, VOICE -> 0xFFFFFFFF;
        };
    }

    private static String arrowTo(Minecraft mc, double x, double z) {
        if (mc.player == null) return "";
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();
        double rel = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        rel = ((rel % 360) + 540) % 360 - 180;
        return ARROWS[Math.floorMod((int) Math.round(rel / 45.0), 8)];
    }

    private static String shortName(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("entity.")) path = path.substring(7);
        else if (path.startsWith("block.")) path = path.substring(6);
        else if (path.startsWith("item.")) path = path.substring(5);
        else if (path.startsWith("ambient.")) path = path.substring(8);
        return path;
    }

    private static double dist2(Sound s, double x, double y, double z) {
        double dx = s.x() - x;
        double dy = s.y() - y;
        double dz = s.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
