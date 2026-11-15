package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

final class Targets {
    private Targets() {}

    static final class Pick {
        final Setting.Bool players;
        final Setting.Bool monsters;
        final Setting.Bool animals;

        Pick(Setting.Bool players, Setting.Bool monsters, Setting.Bool animals) {
            this.players = players;
            this.monsters = monsters;
            this.animals = animals;
        }
    }

    static Pick pick(java.util.function.Function<Setting.Bool, Setting.Bool> add, boolean players, boolean monsters, boolean animals) {
        return new Pick(
                add.apply(new Setting.Bool("Target players", "Aim at other players.", players)),
                add.apply(new Setting.Bool("Target monsters", "Aim at hostile mobs.", monsters)),
                add.apply(new Setting.Bool("Target animals", "Aim at passive mobs. Tamed pets are always spared.", animals)));
    }

    static boolean accepts(Minecraft mc, Pick p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || e.getType() == EntityType.ARMOR_STAND) return false;
        if (le.getHealth() <= 0f) return false;
        if (e instanceof Player) return p.players.on() && !Friends.protects(e);
        if (e instanceof Enemy) return p.monsters.on();
        return p.animals.on() && !(e instanceof TamableAnimal t && t.isTame());
    }

    static LivingEntity nearest(Minecraft mc, Pick p, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (Entity e : Env.entities(mc, range)) {
            if (!accepts(mc, p, e)) continue;
            double d = mc.player.distanceToSqr(e);
            if (d < bestD) {
                best = (LivingEntity) e;
                bestD = d;
            }
        }
        return best;
    }
}
