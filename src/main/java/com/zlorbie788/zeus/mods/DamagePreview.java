package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Dmg;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the blast would do, before you commit to it. Crystal and anchor auras refuse to act when a
 * blast would hurt them more than the target, and until now that refusal was silent: the aura just
 * did nothing. This shows both numbers on the crosshair, and turns the whole card red when the
 * blast would kill you, so "it does not work" becomes "of course it does not, it would kill me".
 */
public final class DamagePreview extends Module {
    private final Setting.Num radius = add(new Setting.Num("Look for enemies",
            "How far around the blast a target is looked for when the crosshair is on a crystal or a charged anchor.",
            12, 4, 32, 2, " blocks"));
    private final Setting.Bool bars = add(new Setting.Bool("Health bars",
            "Draw a small bar under each number, so the damage reads against the health it is compared to.", true));
    private final Setting.Num scale = add(new Setting.Num("Size", "Size of the card.", 100, 70, 160, 10, " %"));
    private final Setting.Bool danger = add(new Setting.Bool("Warn when it kills me",
            "Turns the card red when the blast would take your last heart.", true));

    private Vec3 blast;
    private float self;
    private float theirs;
    private String who;

    public DamagePreview() {
        super("damagepreview", "Damage Preview",
                "While you look at an end crystal or a charged respawn anchor, shows the damage it would do to the nearest enemy and to you.",
                Category.COMBAT);
        alias("dmgpreview", "blast preview", "crystal damage");
        light();
        anywhere();
        onByDefault();
    }

    private static Vec3 crystal(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e.getType() == EntityType.END_CRYSTAL && e.isAlive()) return e.position();
            return null;
        }
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && mc.level != null) {
            BlockPos p = bhr.getBlockPos();
            BlockState st = mc.level.getBlockState(p);
            if (st.is(Blocks.RESPAWN_ANCHOR)) {
                int charges = 0;
                try {
                    charges = st.getValue(RespawnAnchorBlock.CHARGE);
                } catch (RuntimeException ignored) {
                }
                if (charges > 0) return Vec3.atCenterOf(p);
            }
        }
        return null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        blast = null;
        who = null;
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        Vec3 c = crystal(mc);
        if (c == null) return;
        boolean anchor = mc.hitResult instanceof BlockHitResult;
        float power = anchor ? 5f : 6f;
        float mine = Dmg.explosion(mc, c, mc.player, power);
        LivingEntity best = null;
        float bestDmg = -1f;
        for (Entity e : Env.entities(mc, radius.get())) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive() || le.isSpectator()) continue;
            float d = Dmg.explosion(mc, c, le, power);
            if (d <= 0f) continue;
            if (d > bestDmg) {
                bestDmg = d;
                best = le;
            }
        }
        blast = c;
        self = mine;
        theirs = Math.max(0f, bestDmg);
        who = best == null ? null : best.getName().getString();
    }

    private static float health(LivingEntity e) {
        return e.getHealth() + e.getAbsorptionAmount();
    }

    @Override
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (blast == null || mc.player == null) return;
        float sc = scale.f() / 100f;
        boolean lethal = danger.on() && self >= health(mc.player);
        int rows = who == null ? 1 : 2;
        int cw = Math.round(126 * sc);
        int ch = Math.round((14 + rows * 15) * sc);
        int x = w / 2 - cw / 2;
        int y = h / 2 + Math.round(26 * sc);
        if (y + ch > h - 6) y = h / 2 - ch - Math.round(26 * sc);

        int edge = lethal ? 0xFFFF3B4D : self > 0 ? 0xFFFFC44D : 0xFF3C92FF;
        Draw.rrect(g, x, y, cw, ch, 5, lethal ? 0xF01A0509 : 0xE60B1220);
        Draw.rrectLine(g, x, y, cw, ch, 5, Draw.rgba(edge, 0.85f));

        String head = lethal ? "FATAL TO YOU" : self > 0 ? "BLAST" : "SAFE BLAST";
        Draw.text(g, head, x + 6, y + 4, lethal ? 0xFFFF6B78 : 0xFFE6EDFA);
        String crystals = mc.hitResult instanceof BlockHitResult ? "anchor" : "crystal";
        Draw.text(g, crystals, x + cw - 6 - Draw.width(crystals), y + 4, 0xFF7F93BD);

        int ry = y + Math.round(16 * sc);
        if (who != null) {
            int col = theirs >= health(mc.player) ? 0xFFFF9AA4 : 0xFF5EE67A;
            Draw.text(g, Draw.fit(who, Math.round(cw * 0.55f)), x + 6, ry, 0xFFC3D2F2);
            String val = String.valueOf(Math.round(theirs));
            Draw.text(g, val, x + cw - 6 - Draw.width(val), ry, col);
            if (bars.on()) {
                LivingEntity best = nearest(mc);
                float total = best == null ? 20f : Math.max(1f, best.getMaxHealth());
                float frac = best == null ? 0f : Math.min(1f, theirs / total);
                int bw = cw - 12;
                g.fill(x + 6, ry + 9, x + 6 + bw, ry + 11, 0x66000000);
                g.fill(x + 6, ry + 9, x + 6 + Math.round(bw * frac), ry + 11, Draw.rgba(col, 0.9f));
            }
            ry += Math.round(15 * sc);
        }
        float hp = health(mc.player);
        int meCol = lethal ? 0xFFFF3B4D : self > hp * 0.6f ? 0xFFFFC44D : 0xFFE6EDFA;
        Draw.text(g, "you", x + 6, ry, 0xFFC3D2F2);
        String meVal = Math.round(self) + " / " + Math.round(hp);
        Draw.text(g, meVal, x + cw - 6 - Draw.width(meVal), ry, meCol);
        if (bars.on()) {
            float frac = Math.min(1f, self / Math.max(1f, hp));
            int bw = cw - 12;
            g.fill(x + 6, ry + 9, x + 6 + bw, ry + 11, 0x66000000);
            g.fill(x + 6, ry + 9, x + 6 + Math.round(bw * frac), ry + 11, Draw.rgba(meCol, 0.9f));
        }
    }

    private LivingEntity nearest(Minecraft mc) {
        if (blast == null || mc.player == null) return null;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : Env.entities(mc, radius.get())) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) continue;
            double d = le.distanceToSqr(blast);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }
}
