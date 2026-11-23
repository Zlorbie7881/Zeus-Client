package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Turns a hit thrown while standing on the ground into a critical one.
 *
 * <p>The server decides a hit is critical from the position it has for you, and a fall of a few
 * centimetres is enough. Two position packets are sent just before the attack: one a fraction above
 * your feet and one back on them, so the server sees a short fall. Nothing about your real position
 * changes, and on the client you stay exactly where you were.
 */
public final class Criticals extends Module {
    private static volatile Criticals self;

    private final Setting.Num step = add(new Setting.Num("Packet height", "How far above your feet the first packet puts you. A little is enough and a lot looks wrong.", 0.0625, 0.01, 0.30, 0.01, " blocks"));
    private final Setting.Bool onlyWhenCharged = add(new Setting.Bool("Only when fully charged", "Skips the spoof while your attack is still charging, so the packets line up with real hits.", true));

    public Criticals() {
        super("criticals", "Criticals", "Every hit you land while standing on the ground lands as a critical hit, without jumping.", Category.COMBAT);
        alias("crit", "always crit", "ground crits");
        self = this;
    }

    /** Called from the attack hook, before the hit packet is sent. */
    public static void beforeAttack(Player player, Entity target) {
        Criticals c = self;
        if (c == null || !c.enabled() || !(target instanceof LivingEntity)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player != player || mc.getConnection() == null) return;
        if (Env.local()) return;
        Player p = mc.player;
        if (!p.onGround() || p.isPassenger() || p.isFallFlying() || p.isInWater() || p.isInLava()) return;
        if (c.onlyWhenCharged.on() && p.getAttackStrengthScale(0.5f) < 0.92f) return;
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        boolean collision = p.horizontalCollision;
        try {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + c.step.get(), z, false, collision));
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, collision));
        } catch (RuntimeException ignored) {
        }
    }
}
