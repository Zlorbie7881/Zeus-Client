package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;

public final class Morph extends Module {

    private static final String[] WANTED = {
        "allay", "armadillo", "axolotl", "bat", "bee", "blaze", "bogged", "breeze", "camel", "camel_husk", "cat", "cave_spider",
        "chicken", "copper_golem", "cow", "creaking", "creeper", "dolphin", "donkey", "drowned", "elder_guardian", "enderman",
        "endermite", "evoker", "fox", "frog", "ghast", "giant", "glow_squid", "goat", "guardian", "happy_ghast", "hoglin", "horse",
        "husk", "illusioner", "iron_golem", "llama", "magma_cube", "mooshroom", "mule", "nautilus", "ocelot", "panda", "parched",
        "parrot", "phantom", "pig", "piglin", "piglin_brute", "pillager", "polar_bear", "rabbit", "ravager", "sheep", "shulker",
        "silverfish", "skeleton", "skeleton_horse", "slime", "sniffer", "snow_golem", "spider", "squid", "stray", "strider",
        "trader_llama", "turtle", "vex", "villager", "vindicator", "wandering_trader", "warden", "witch", "wither",
        "wither_skeleton", "wolf", "zoglin", "zombie", "zombie_horse", "zombie_nautilus", "zombie_villager", "zombified_piglin",
        "armor_stand"
    };

    private static final EquipmentSlot[] WORN = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private static volatile Morph self;

    private final String[] ids;
    private final Setting.Pick mob;
    private final Random random = new Random();

    private volatile LivingEntity ghost;
    private String ghostId = "";
    private Object ghostLevel;
    private String status;

    public Morph() {
        super("morph", "Morph", "Look like any mob. Visual only: only you see it, in third person (F5).", Category.PLAYER);
        List<String> ok = new ArrayList<>();
        for (String id : WANTED) {
            try {
                Optional<EntityType<?>> t = EntityType.byString(id);
                if (t.isPresent()) ok.add(id);
            } catch (RuntimeException | LinkageError e) {
                ok.add(id);
            }
        }
        if (ok.isEmpty()) ok.add("zombie");
        ids = ok.toArray(new String[0]);
        String[] labels = new String[ids.length];
        for (int i = 0; i < ids.length; i++) labels[i] = pretty(ids[i]);
        mob = add(new Setting.Pick("Mob", "Pick what you turn into. The change is instant.", ids.length > 0 && ok.contains("creeper") ? "creeper" : ids[0], ids, labels));
        add(new Setting.Action("Random mob", "Pick one at random", () -> mob.random(random)));
        self = this;
    }

    static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : id.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return sb.toString();
    }

    @Override
    public String info() {
        return status != null ? status : pretty(mob.id()).toLowerCase(Locale.ROOT);
    }

    public static Entity swap(Entity e) {
        Morph m = self;
        if (m == null || e == null || !m.enabled()) return e;
        LivingEntity g = m.ghost;
        if (g == null) return e;
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player ? g : e;
    }

    @Override
    protected void onEnable() {
        status = null;
        ghost = null;
        ghostId = "";
    }

    @Override
    protected void onDisable() {
        ghost = null;
        ghostId = "";
        ghostLevel = null;
        status = null;
    }

    @Override
    protected void onTick(Minecraft mc) {
        Player p = mc.player;
        if (p == null || mc.level == null) {
            ghost = null;
            return;
        }
        String want = mob.id();
        if (ghost == null || !want.equals(ghostId) || ghostLevel != mc.level) build(mc, want);
        LivingEntity g = ghost;
        if (g == null) return;
        sync(g, p);
    }

    private void build(Minecraft mc, String id) {
        ghost = null;
        ghostId = id;
        ghostLevel = mc.level;
        status = null;
        try {
            Optional<EntityType<?>> type = EntityType.byString(id);
            if (type.isEmpty()) {
                status = "unknown mob";
                return;
            }
            Entity made = type.get().create(mc.level, EntitySpawnReason.COMMAND);
            if (made instanceof LivingEntity living) {
                ghost = living;
                sync(living, mc.player);
                living.xo = living.xOld = mc.player.getX();
                living.yo = living.yOld = mc.player.getY();
                living.zo = living.zOld = mc.player.getZ();
            } else {
                status = "cannot morph";
                Zeus.toast("Morph: " + pretty(id) + " cannot be copied here", false);
            }
        } catch (Throwable t) {
            status = "failed";
            Zeus.LOG.warn("Morph could not build {}", id, t);
            Zeus.toast("Morph: " + pretty(id) + " failed to load", false);
        }
    }

    private static void sync(LivingEntity g, Player p) {
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        g.setPos(p.getX(), p.getY(), p.getZ());
        g.xo = p.xo;
        g.yo = p.yo;
        g.zo = p.zo;
        g.xOld = p.xOld;
        g.yOld = p.yOld;
        g.zOld = p.zOld;
        g.setYRot(p.getYRot());
        g.setXRot(p.getXRot());
        g.yRotO = p.yRotO;
        g.xRotO = p.xRotO;
        g.yBodyRot = p.yBodyRot;
        g.yBodyRotO = p.yBodyRotO;
        g.yHeadRot = p.yHeadRot;
        g.yHeadRotO = p.yHeadRotO;
        g.setOnGround(p.onGround());
        g.setPose(p.getPose());
        g.attackAnim = p.attackAnim;
        g.oAttackAnim = p.oAttackAnim;
        g.hurtTime = p.hurtTime;
        g.deathTime = p.deathTime;
        g.tickCount++;
        float speed = (float) Math.min(Math.sqrt(dx * dx + dz * dz) * 4.0, 1.0);
        g.walkAnimation.update(speed, 0.4f, 1.0f);
        for (EquipmentSlot s : WORN) {
            ItemStack want = p.getItemBySlot(s);
            if (g.getItemBySlot(s) != want) g.setItemSlot(s, want);
        }
    }
}
