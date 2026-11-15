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
    private final Setting.Bool armor = add(new Setting.Bool("Armour durability", "Each worn piece as an icon with a bar showing how close it is to breaking. Read from the equipment the server sends, so it is the real durability, not a guess.", true));
    private final Setting.Bool percent = add(new Setting.Bool("Durability percent", "Show the percentage left under each piece, in small numbers.", true)).showIf(armor::on);
    private final Setting.Bool face = add(new Setting.Bool("Player head", "The player's face from their skin at the left of the plate.", true));
    private final Setting.Bool held = add(new Setting.Bool("Held item", "The item in their main hand next to their armour.", true));
    private final Setting.Bool closeFade = add(new Setting.Bool("Fade up close", "Plates of players right next to you turn see-through, so they never cover the fight.", true));
    private final Setting.Bool targetOnly = add(new Setting.Bool("Only my target", "Show a plate only for who you are looking at or hit in the last few seconds.", false));
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
        tickAnim();
        List<Entity> list = Env.entities(mc, range.get());
        // Furthest first, so the nearest plate is drawn last and wins any overlap.
        list.sort(Comparator.comparingDouble((Entity e) -> -mc.player.distanceToSqr(e)));
        Entity looked = mc.crosshairPickEntity;
        LivingEntity hit = mc.player.getLastHurtMob();
        boolean recent = hit != null && mc.player.tickCount - mc.player.getLastHurtMobTimestamp() < 100;
        for (Entity e : list) {
            if (!applies(e)) continue;
            if (targetOnly.on() && e != looked && !(recent && e == hit)) continue;
            LivingEntity le = (LivingEntity) e;
            if (!walls.on() && !mc.player.hasLineOfSight(le)) continue;
            double anchorY = le.getY() + le.getBbHeight() + (le.isShiftKeyDown() ? 0.15 : 0.35);
            if (!Projector.project(le.getX(), anchorY, le.getZ(), t)) continue;
            if (t[0] < -80 || t[0] > w + 80 || t[1] < -60 || t[1] > h + 60) continue;
            int x = Math.round(t[0]);
            int y = Math.round(t[1]);
            plate(g, mc, le, x, y, w, h, sc, pad, rowH, barH, armorRowH);
            // the next (nearer) plate starts a new layer, so its card covers this plate's text and items
            g.nextStratum();
        }
    }

    // ------------------------------------------------------------------ the plate
    /** Per entity: health as drawn (eases toward the real value), the white damage trail, last time seen. */
    private final java.util.HashMap<Integer, float[]> anim = new java.util.HashMap<>();
    private long lastFrame;

    private float[] anim(LivingEntity le, float hf) {
        float[] a = anim.get(le.getId());
        if (a == null) {
            a = new float[] {hf, hf, 0f};
            anim.put(le.getId(), a);
        }
        a[2] = 1f;
        return a;
    }

    private void tickAnim() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        // forget plates not drawn last frame
        anim.values().removeIf(a -> a[2] <= 0f);
        for (float[] a : anim.values()) a[2] = 0f;
        frameDt = dt;
    }

    private float frameDt = 0.016f;

    private static final int CARD_TOP = 0xE8161D2E;
    private static final int CARD_BOTTOM = 0xE80A0F1A;
    private static final int RIM = 0x2EFFFFFF;
    private static final int SUB = 0xFF8E9BB8;

    private void plate(GuiGraphics g, Minecraft mc, LivingEntity le, int cx, int bottom, int w, int h,
                       float sc, int pad, int rowH, int barH, int armorRowH) {
        float u = sc;
        double dist = mc.player != null ? Math.sqrt(mc.player.distanceToSqr(le)) : 0;
        float far = (float) Math.max(0, Math.min(1, (dist - range.get() * 0.6) / Math.max(1, range.get() * 0.4)));
        float a = 1f - 0.4f * far;
        if (closeFade.on()) a *= (float) Math.max(0.28, Math.min(1.0, (dist - 1.5) / 2.5));

        float max = Math.max(1f, le.getMaxHealth());
        float hf = Math.max(0f, Math.min(1f, le.getHealth() / max));
        float absorb = le.getAbsorptionAmount();
        float[] an = anim(le, hf);
        float k = 1f - (float) Math.exp(-frameDt * 14f);
        an[0] += (hf - an[0]) * k;
        if (hf >= an[1]) an[1] = hf;                       // healing: the trail follows at once
        else an[1] += (hf - an[1]) * (1f - (float) Math.exp(-frameDt * 2.4f));   // damage: it lingers, then slides down

        boolean friend = Friends.isFriend(le);
        int accent = friend ? 0xFF6EE7A8 : hf > 0.5f ? Draw.mix(0xFFFFC94D, 0xFF4ADE80, (hf - 0.5f) * 2f) : Draw.mix(0xFFFF4D5E, 0xFFFFC94D, hf * 2f);

        boolean isPlayer = le instanceof net.minecraft.client.player.AbstractClientPlayer;
        boolean showFace = face.on() && isPlayer;
        String title = name.on() ? le.getName().getString() : "";
        String hp = health.on() ? String.format(Locale.ROOT, "%.1f", le.getHealth()) : "";
        String abs = health.on() && absorb > 0.5f ? "+" + Math.round(absorb) : "";
        String sub = distance.on() ? Math.round(dist) + "m" : "";

        // equipment cells: armour then the held item
        java.util.List<ItemStack> gear = new java.util.ArrayList<>(5);
        if (armor.on()) {
            for (EquipmentSlot slot : Armor.SLOTS) {   // helmet first, boots last
                ItemStack st = le.getItemBySlot(slot);
                if (!st.isEmpty()) gear.add(st);
            }
        }
        if (held.on() && !le.getMainHandItem().isEmpty()) gear.add(le.getMainHandItem());

        // compact card: one header row, a thin health bar, a row of small item icons
        int p = Math.max(2, Math.round(3 * u));
        int gap = Math.max(1, Math.round(2 * u));
        int faceS = showFace ? Math.round(9 * u) : 0;
        float nameSc = sc * 0.9f;
        float smallSc = sc * 0.7f;
        int nameW = title.isEmpty() ? 0 : Draw.widthBoldS(title, nameSc);
        int hpW = hp.isEmpty() ? 0 : Draw.widthS(hp, nameSc);
        int absW = abs.isEmpty() ? 0 : Draw.widthS(abs, smallSc) + gap;
        int subW = sub.isEmpty() ? 0 : Draw.widthS(sub, smallSc) + Math.round(4 * u);
        int headH = Math.max(faceS, Math.round(8 * u));
        int headW = (faceS > 0 ? faceS + Math.round(4 * u) : 0) + nameW + Math.round(6 * u) + subW + hpW + absW;
        int cell = Math.round(12 * u);
        int gearW = gear.size() * cell;
        int boxW = Math.max(Math.round(54 * u), Math.max(headW, gearW) + 2 * p);
        int barT = health.on() ? Math.max(2, Math.round(2 * u)) : 0;
        int gearH = gear.isEmpty() ? 0 : Math.round((percent.on() ? 17 : 12) * u);
        int boxH = p + headH + (barT > 0 ? gap + barT : 0) + (gearH > 0 ? gap + gearH : 0) + p;

        if (bottom - boxH < 2) bottom = 2 + boxH;
        int x = Math.max(2, Math.min(w - 2 - boxW, cx - boxW / 2));
        int y = bottom - boxH;
        int r = Math.max(2, Math.round(3 * u));

        Draw.glow(g, x, y, boxW, boxH, r, accent, Math.round(4 * u), 0.05f * a);
        Draw.rrect(g, x, y, boxW, boxH, r, Draw.rgba(CARD_TOP, a), Draw.rgba(CARD_BOTTOM, a));
        Draw.rrectLine(g, x, y, boxW, boxH, r, Draw.rgba(RIM, a));
        g.fill(x + r, y, x + boxW - r, y + 1, Draw.rgba(accent, 0.75f * a));

        int cy = y + p;
        int tx = x + p;
        int textY = cy + (headH - Math.round(7 * u)) / 2;
        if (showFace) {
            try {
                var skin = ((net.minecraft.client.player.AbstractClientPlayer) le).getSkin();
                net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, tx, cy + (headH - faceS) / 2, faceS, Draw.rgba(0xFFFFFFFF, a));
            } catch (Throwable ignored) {
                // no skin yet: the plate still reads fine without the face
            }
            tx += faceS + Math.round(4 * u);
        }
        int right = x + boxW - p;
        if (!abs.isEmpty()) {
            Draw.textRightS(g, abs, right, textY + 1, Draw.rgba(0xFFFFD84D, a), smallSc);
            right -= absW;
        }
        if (!hp.isEmpty()) {
            Draw.textRightS(g, hp, right, textY, Draw.rgba(accent, a), nameSc);
            right -= hpW;
        }
        if (!sub.isEmpty()) Draw.textRightS(g, sub, right - Math.round(4 * u), textY + 1, Draw.rgba(SUB, a), smallSc);
        if (!title.isEmpty()) {
            int room = Math.max(16, right - subW - tx - Math.round(2 * u));
            Draw.boldS(g, Draw.fit(title, Math.round(room / nameSc)), tx, textY, Draw.rgba(friend ? 0xFF8EF0BE : 0xFFFFFFFF, a), nameSc);
        }
        cy += headH;

        if (barT > 0) {
            cy += gap;
            int bx = x + p, bw = boxW - 2 * p;
            int br = Math.max(1, barT / 2);
            Draw.rrect(g, bx, cy, bw, barT, br, Draw.rgba(0x33FFFFFF, a));
            int trail = Math.round(bw * an[1]);
            if (trail > Math.round(bw * an[0]) + 1) Draw.rrect(g, bx, cy, trail, barT, br, Draw.rgba(0xCCFFFFFF, a));
            int fill = Math.max(barT, Math.round(bw * an[0]));
            Draw.rrect(g, bx, cy, fill, barT, br, Draw.rgba(Draw.lighten(accent, 0.2f), a), Draw.rgba(accent, a));
            if (absorb > 0.5f) {
                int aw = Math.round(bw * Math.min(1f, absorb / max));
                Draw.rrect(g, bx + bw - aw, cy, aw, barT, br, Draw.rgba(0xE6FFD84D, a));
            }
            cy += barT;
        }

        if (!gear.isEmpty()) {
            cy += gap;
            int gx = x + (boxW - gearW) / 2;
            float is = 10f * u;
            for (ItemStack st : gear) {
                int ccx = gx + cell / 2;
                g.pose().pushMatrix();
                g.pose().translate(ccx - is / 2f, cy);
                g.pose().scale(is / 16f, is / 16f);
                g.renderItem(st, 0, 0);
                g.pose().popMatrix();
                if (st.getMaxDamage() > 0) {
                    float frac = Armor.left(st);
                    int col = frac > 0.5f ? Draw.mix(0xFFFFC94D, 0xFF4ADE80, (frac - 0.5f) * 2f) : Draw.mix(0xFFFF4D5E, 0xFFFFC94D, frac * 2f);
                    int bw = cell - Math.round(3 * u);
                    int bx = ccx - bw / 2;
                    int by = cy + Math.round(11 * u);
                    int bh = Math.max(1, Math.round(1 * u));
                    g.fill(bx, by, bx + bw, by + bh, Draw.rgba(0x55000000, a));
                    g.fill(bx, by, bx + Math.max(1, Math.round(bw * frac)), by + bh, Draw.rgba(col, a));
                    if (percent.on()) {
                        Draw.textCenterS(g, String.valueOf(Math.round(frac * 100f)), ccx, cy + Math.round(13 * u), Draw.rgba(col, a), sc * 0.5f);
                    }
                }
                gx += cell;
            }
        }
    }
}
