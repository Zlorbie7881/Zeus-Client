package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Armor;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One plate over every player: name, health and how much life is left in each piece of armour.
 * The durability numbers are the server's own, not an estimate: equipment packets carry the damage
 * component, and the server re-sends a piece every time it loses a point.
 */
public final class NameTags extends Module {
    private final Setting.Bool name = add(new Setting.Bool("Name", "The player's name, the way the tab list spells it.", true));
    private final Setting.Bool health = add(new Setting.Bool("Health", "Exact health and a bar. The number is what the health bar above their head really is, not a rounded heart count.", true));
    private final Setting.Bool distance = add(new Setting.Bool("Distance", "Metres to the player.", true));
    private final Setting.Bool armor = add(new Setting.Bool("Armour durability", "A line per worn piece showing how close it is to breaking. This is read from the equipment the server sends, so it is the real durability, not a guess.", true));
    private final Setting.Bool percent = add(new Setting.Bool("Durability percent", "Show the exact percentage and the points left next to each bar.", true)).showIf(armor::on);
    private final Setting.Bool onlyPlayers = add(new Setting.Bool("Players only", "Off shows mobs too, with their health and any armour they picked up.", true));
    private final Setting.Bool hideFriends = add(new Setting.Bool("Hide friends", "Friends already have their own tag; this keeps this plate off them.", false));
    private final Setting.Bool hideVanilla = add(new Setting.Bool("Hide the game's tag", "Stops the ordinary name tag from drawing underneath this plate, so there is no doubled text.", true));
    private final Setting.Bool walls = add(new Setting.Bool("Through walls", "Keep plates visible when the player is behind blocks. Off only draws them when you can actually see them.", true));
    private final Setting.Num range = add(new Setting.Num("Range", "How far away plates are drawn.", 48, 8, 128, 1, "m"));
    private final Setting.Num scale = add(new Setting.Num("Scale", "Size of the text and bars.", 100, 70, 160, 5, "%"));

    private static NameTags live;

    public NameTags() {
        super("nametags", "Name Tags", "Name, health and enemy armour durability over every player, with a bar per piece showing how close it is to breaking.", Category.RENDER);
        alias("health tags", "healthtags", "enemy armor", "enemy armour", "armor hud", "overhead", "tags");
    }

    @Override
    protected void onEnable() {
        live = this;
    }

    @Override
    protected void onDisable() {
        if (live == this) live = null;
    }

    /** Called from the name tag renderer so the ordinary tag can step aside for this plate. */
    public static boolean hideVanilla(Entity e) {
        NameTags m = live;
        return m != null && m.enabled() && m.hideVanilla.on() && m.applies(e);
    }

    private boolean applies(Entity e) {
        if (!(e instanceof LivingEntity le) || le == Minecraft.getInstance().player || le.isInvisible()) return false;
        if (onlyPlayers.on() && !(e instanceof Player)) return false;
        return !(hideFriends.on() && Friends.isFriend(e));
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (mc.player == null || mc.level == null) return;
        if (!Projector.prepare(mc, partial, w, h)) return;
        float sc = Math.max(0.7f, Math.min(1.6f, scale.f() / 100f));
        int pad = Math.max(3, Math.round(4 * sc));
        int rowH = Math.round(9 * sc) + 2;
        int barH = Math.max(2, Math.round(3 * sc));
        int armorRowH = Math.round(7 * sc) + 2;
        float[] t = new float[2];
        List<Entity> list = new java.util.ArrayList<>(Env.entities(mc, range.get()));
        // Furthest first, so the nearest plate is drawn last and wins any overlap.
        list.sort(Comparator.comparingDouble((Entity e) -> -mc.player.distanceToSqr(e)));
        for (Entity e : list) {
            if (!applies(e)) continue;
            LivingEntity le = (LivingEntity) e;
            if (!walls.on() && !mc.player.hasLineOfSight(le)) continue;
            double anchorY = le.getY() + le.getBbHeight() + (le.isShiftKeyDown() ? 0.15 : 0.35);
            if (!Projector.project(le.getX(), anchorY, le.getZ(), t)) continue;
            if (t[0] < -80 || t[0] > w + 80 || t[1] < -60 || t[1] > h + 60) continue;
            int x = Math.round(t[0]);
            int y = Math.round(t[1]);
            plate(g, mc, le, x, y, w, h, sc, pad, rowH, barH, armorRowH);
        }
    }

    private void plate(GuiGraphics g, Minecraft mc, LivingEntity le, int cx, int bottom, int w, int h,
                       float sc, int pad, int rowH, int barH, int armorRowH) {
        String title = name.on() ? le.getName().getString() : "";
        float hf = le.getMaxHealth() > 0 ? Math.max(0f, Math.min(1f, le.getHealth() / le.getMaxHealth())) : 0f;
        float absorb = le.getAbsorptionAmount();
        String hpText = health.on()
                ? String.format(Locale.ROOT, "%.1f", le.getHealth()) + (absorb > 0.5f ? "+" + Math.round(absorb) : "")
                : "";
        String distText = distance.on() && mc.player != null
                ? Math.round(Math.sqrt(mc.player.distanceToSqr(le))) + "m"
                : "";
        int nameW = title.isEmpty() ? 0 : Draw.widthS(title, sc);
        int hpW = hpText.isEmpty() ? 0 : Draw.widthS("  " + hpText, sc);
        int distW = distText.isEmpty() ? 0 : Draw.widthS(distText, sc);
        int rows = 0;
        if (armor.on()) {
            for (EquipmentSlot s : Armor.SLOTS) if (Armor.left(le.getItemBySlot(s)) >= 0) rows++;
        }
        int inner = nameW + hpW;
        int boxW = Math.max(Math.round(58 * sc), Math.max(inner, distW + Math.round(26 * sc)) + pad * 2);
        int boxH = pad + rowH + (health.on() ? barH + 4 : 0) + rows * armorRowH + Math.max(0, pad - 2);
        if (bottom - boxH < 2) bottom = 2 + boxH;
        int x = cx - boxW / 2;
        int y = bottom - boxH;
        if (x < 2) x = 2;
        if (x + boxW > w - 2) x = Math.max(2, w - 2 - boxW);

        boolean friend = Friends.isFriend(le);
        int accent = friend ? 0xFF7BE0A0 : Draw.mix(0xFFFF4D5E, 0xFF5EE67A, hf);
        float a = 1f;
        Draw.rrect(g, x, y, boxW, boxH, 3, Draw.rgba(0x07090F, 0.62f * a));
        Draw.rrectLine(g, x, y, boxW, boxH, 3, Draw.rgba(accent, 0.34f * a));
        g.fill(x + 1, y + 2, x + 3, y + boxH - 2, Draw.rgba(accent, 0.85f * a));

        int tx = x + pad;
        int ty = y + pad - 1;
        if (!title.isEmpty()) {
            Draw.textS(g, title, tx, ty, Draw.rgba(friend ? 0xFF7BE0A0 : 0xFFFFFFFF, a), sc);
            tx += nameW;
        }
        if (!hpText.isEmpty()) {
            Draw.textS(g, "  " + hpText, tx, ty, Draw.rgba(accent, a), sc);
        }
        if (!distText.isEmpty()) {
            Draw.textRightS(g, distText, x + boxW - pad, ty, Draw.rgba(0xFF9AA7C7, 0.9f * a), sc);
        }

        int rowY = y + pad + rowH - 2;
        if (health.on()) {
            int full = boxW - pad * 2;
            int filled = Math.round(full * hf);
            g.fill(x + pad, rowY, x + pad + full, rowY + barH, Draw.rgba(0x000000, 0.55f * a));
            g.fill(x + pad, rowY, x + pad + filled, rowY + barH, Draw.rgba(accent, 0.95f * a));
            if (absorb > 0.5f) {
                int aw = Math.min(full - filled, Math.round(full * Math.min(1f, absorb / 20f)));
                if (aw > 0) g.fill(x + pad + filled, rowY, x + pad + filled + aw, rowY + barH, Draw.rgba(0xFFFFD84D, 0.9f * a));
            }
            rowY += barH + 4;
        }

        if (!armor.on()) return;
        for (EquipmentSlot s : Armor.SLOTS) {
            ItemStack stack = le.getItemBySlot(s);
            float frac = Armor.left(stack);
            if (frac < 0) continue;
            int ry = rowY + Math.max(0, (armorRowH - 7) / 2);
            int letterW = Draw.widthS(Armor.letter(s), sc);
            Draw.textS(g, Armor.letter(s), x + pad, ry, Draw.rgba(0xFF9AA7C7, 0.85f * a), sc);
            String right = "";
            if (percent.on()) {
                int left = Armor.remaining(stack);
                right = Math.round(frac * 100f) + "%";
                if (left > 0 && left < 1000) right += " " + left;
            }
            int rightW = right.isEmpty() ? 0 : Draw.widthS(right, sc);
            int barX = x + pad + letterW + 3;
            int barR = x + boxW - pad - (rightW > 0 ? rightW + 3 : 0);
            int barW = Math.max(6, barR - barX);
            int bh = Math.max(2, barH - 1);
            int by = ry + Math.round(2 * sc);
            int col = frac > 0.5f
                    ? Draw.mix(0xFFFFD84D, 0xFF5EE67A, (frac - 0.5f) * 2f)
                    : Draw.mix(0xFFFF4D4D, 0xFFFFD84D, frac * 2f);
            g.fill(barX, by, barX + barW, by + bh, Draw.rgba(0x000000, 0.55f * a));
            g.fill(barX, by, barX + Math.max(1, Math.round(barW * frac)), by + bh, Draw.rgba(col, 0.95f * a));
            if (rightW > 0) {
                Draw.textRightS(g, right, x + boxW - pad, ry, Draw.rgba(col, 0.95f * a), sc);
            }
            rowY += armorRowH;
        }
    }
}
