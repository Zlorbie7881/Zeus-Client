package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Highlights dropped items. It works from a list of items with a colour each: only the listed items
 * are marked (diamonds, netherite, elytra, totems...) and each one has the colour you gave it. Switch "Only listed items"
 * off to mark every dropped item. Items get the game's glow outline through walls, a box and an optional tracer.
 *
 * <p>The list is plain text: {@code diamond:cyan, netherite_ingot:purple, shulker_box:#FF66CC}. A name is the item id without
 * {@code minecraft:}; it may end with a star to mean "starts with" ({@code diamond_*}) or start with one ({@code *_ore}).
 * A colour is one of the colour names of the other ESPs or a hex code. A name with no colour uses the default colour.</p>
 */
public final class ItemEsp extends Module {
    public static final String DEFAULT_LIST = "diamond:cyan, emerald:green, netherite_ingot:purple, ancient_debris:purple, elytra:orange, totem_of_undying:yellow, trident:cyan, "
            + "enchanted_golden_apple:pink, golden_apple:yellow, nether_star:white, heart_of_the_sea:cyan, end_crystal:pink, shulker_box:pink, gold_ingot:yellow, gold_block:yellow, gold_nugget:yellow";

    private static volatile ItemEsp self;

    private final Setting.Num range = add(new Setting.Num("Range", "Maximum distance for dropped-item highlights.", 64, 8, 256, 8, " blocks"));
    private final Setting.Num maxItems = add(new Setting.Num("Maximum items", "Hard limit on rendered dropped-item entities per frame to protect performance.", 40, 8, 128, 8, ""));
    private final Setting.Bool onlyListed = add(new Setting.Bool("Only listed items", "Mark only the items in the list below. Off marks every dropped item (enchanted ones in purple).", true));
    private final Setting.Text list = add(new Setting.Text("Item colours", "Comma separated: item:colour. Colours are names (white, red, orange, yellow, green, cyan, blue, purple, pink) or #RRGGBB. Use * at the start or end of a name.", DEFAULT_LIST, 1200)).showIf(onlyListed::on);
    private final Setting.Bool outline = add(new Setting.Bool("Outline", "The game's glow outline around the item, visible through walls.", true));
    private final Setting.Bool labels = add(new Setting.Bool("Item names", "Show item names and stack counts.", false));
    private final Setting.Bool distance = add(new Setting.Bool("Distance", "Show distance beside each item.", false));
    private final EspKit kit;
    private int shown;
    private String parsedFrom = "\u0000";
    private final Map<String, Integer> parsed = new LinkedHashMap<>();

    public ItemEsp() {
        super("itemesp", "Item ESP", "Highlights dropped items, with a colour for each kind you pick.", Category.RENDER);
        light();
        kit = new EspKit(this, 0, false);
        kit.autoReveal = false;
        self = this;
    }

    @Override public String info() { return shown == 0 ? null : Integer.toString(shown); }

    // --------------------------------------------------------------- the list

    private static int colorByName(String word) {
        if (word.startsWith("#")) {
            try {
                return 0xFF000000 | Integer.parseInt(word.substring(1), 16);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        for (int i = 1; i < EspKit.COLOR_NAMES.length; i++) {
            if (EspKit.COLOR_NAMES[i].equalsIgnoreCase(word)) return EspKit.COLOR_VALUES[i];
        }
        return 0;
    }

    private void parse() {
        String text = list.get();
        if (text.equals(parsedFrom)) return;
        parsedFrom = text;
        parsed.clear();
        for (String part : text.split(",")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            int c = p.indexOf(':');
            String id = c < 0 ? p : p.substring(0, c).trim();
            if (id.startsWith("minecraft:")) id = id.substring(10);
            int color = c < 0 ? 0 : colorByName(p.substring(c + 1).trim());
            if (!id.isEmpty()) parsed.put(id, color == 0 ? 0xFF70D6FF : color);
        }
    }

    private static String idOf(ItemEntity item) {
        try {
            String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
            return id.substring(id.indexOf(':') + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The colour for a dropped item, or 0 when it is not marked. */
    private int colorOf(ItemEntity item) {
        if (item.isRemoved() || !item.isAlive()) return 0;
        if (!onlyListed.on()) return item.getItem().isEnchanted() ? 0xFFB06BFF : 0xFF70D6FF;
        parse();
        String id = idOf(item);
        Integer exact = parsed.get(id);
        if (exact != null) return exact;
        for (Map.Entry<String, Integer> e : parsed.entrySet()) {
            String key = e.getKey();
            boolean starts = key.endsWith("*");
            boolean ends = key.startsWith("*");
            if (starts && ends) {
                if (key.length() > 2 && id.contains(key.substring(1, key.length() - 1))) return e.getValue();
            } else if (starts) {
                if (id.startsWith(key.substring(0, key.length() - 1))) return e.getValue();
            } else if (ends) {
                if (id.endsWith(key.substring(1))) return e.getValue();
            }
        }
        return 0;
    }

    private boolean inRange(Minecraft mc, Entity e) {
        double r = range.get();
        return mc.player != null && mc.player.distanceToSqr(e) <= r * r;
    }

    // --------------------------------------------------------------- the glow hooks (called through Esp)

    public static boolean outlines(Entity e) {
        ItemEsp s = self;
        if (s == null || !s.enabled() || !s.outline.on() || !(e instanceof ItemEntity item)) return false;
        return s.inRange(Minecraft.getInstance(), e) && s.colorOf(item) != 0;
    }

    public static int outlineColor(Entity e) {
        ItemEsp s = self;
        if (s == null || !s.enabled() || !s.outline.on() || !(e instanceof ItemEntity item)) return 0;
        return s.inRange(Minecraft.getInstance(), e) ? s.colorOf(item) & 0xFFFFFF : 0;
    }

    // --------------------------------------------------------------- the overlay

    @Override protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        shown = 0;
        if (mc.level == null || mc.player == null || !Projector.prepare(mc, partial, w, h)) return;
        for (var entity : Env.entities(mc, range.get())) {
            if (!(entity instanceof ItemEntity item)) continue;
            int color = colorOf(item);
            if (color == 0) continue;
            double dist = Math.sqrt(mc.player.distanceToSqr(item));
            if (dist > range.get()) continue;
            if (shown >= maxItems.i()) break;
            shown++;
            AABB bb = item.getBoundingBox();
            float alpha = kit.alphaFor(dist, range.get());
            kit.box(g, bb, color, alpha, shown <= 12);
            kit.tracer(g, w, h, item.getX(), item.getY() + 0.15, item.getZ(), color, alpha);
            if (labels.on() || distance.on()) {
                float[] rect = Wire.bounds(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                if (rect == null) continue;
                String name = labels.on() ? item.getItem().getHoverName().getString() + (item.getItem().getCount() > 1 ? " x" + item.getItem().getCount() : "") : "";
                String right = distance.on() ? Math.round(dist) + "m" : "";
                if (name.isEmpty()) { name = right; right = ""; }
                kit.tag(g, name, right, Math.round((rect[0] + rect[2]) / 2f), Math.round(rect[1]) - 14, color, alpha, w, h);
            }
        }
    }
}
