package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.ui.Accent;
import com.zlorbie788.zeus.core.Aim;
import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Hits;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Inv;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Names;
import com.zlorbie788.zeus.core.Setting;
import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.render.EspKit;
import com.zlorbie788.zeus.render.Projector;
import com.zlorbie788.zeus.render.Wire;
import com.zlorbie788.zeus.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class Parity {
    private Parity() {}

    private static final Random RNG = new Random();

    private static boolean ready(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.screen == null;
    }

    public static final class Triggerbot extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num delay = add(new Setting.Num("Reaction delay", "How long the target must stay under the crosshair before the hit, like a human reaction.", 90, 0, 400, 10, " ms"));
        private final Setting.Num charge = add(new Setting.Num("Swing charge", "How charged the swing must be (1 is full).", 0.92, 0.1, 1.0, 0.02, ""));
        private final Setting.Bool weaponOnly = add(new Setting.Bool("Only with a weapon", "Only hit while holding a sword, axe, mace or trident.", true));
        private final Setting.Bool pauseUse = add(new Setting.Bool("Pause while eating / blocking", "Do nothing while you are using an item.", true));
        private Entity watching;
        private long since;
        private String shown;

        public Triggerbot() {
            super("triggerbot", "Triggerbot", "Hits the entity under your crosshair once the swing is charged, after a reaction delay.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            shown = null;
            if (!ready(mc)) {
                watching = null;
                return;
            }
            var me = mc.player;
            if (pauseUse.on() && me.isUsingItem()) return;
            if (weaponOnly.on() && !Inv.holding(s -> {
                String n = Names.item(s);
                return n.endsWith("_sword") || n.endsWith("_axe") || n.equals("mace") || n.equals("trident");
            })) return;
            if (!(mc.hitResult instanceof EntityHitResult ehr) || !Targets.accepts(mc, who, ehr.getEntity())) {
                watching = null;
                return;
            }
            Entity e = ehr.getEntity();
            long now = System.currentTimeMillis();
            if (e != watching) {
                watching = e;
                since = now;
            }
            shown = e.getName().getString();
            if (now - since < delay.get() || me.getAttackStrengthScale(0f) < charge.get()) return;
            if (Phantom.miss()) return;
            mc.gameMode.attack(me, e);
            me.swing(InteractionHand.MAIN_HAND);
            since = now + 60;
        }

        @Override
        public String info() {
            return shown;
        }
    }

    public static final class WTap extends Module {
        private final Setting.Num ticks = add(new Setting.Num("Release time", "How many ticks the sprint stays off after a hit.", 1, 1, 5, 1, " ticks"));
        private final Setting.Num chance = add(new Setting.Num("Chance", "How often it taps.", 100, 10, 100, 5, " %"));
        private static volatile WTap live;
        private boolean attackWas;
        private boolean sprintWas;
        private int off;

        public WTap() {
            super("wtap", "W Tap", "Lets go of sprint right after a sprinting hit, so the next hit gets the sprint knockback again.", Category.COMBAT);
            live = this;
        }

        /**
         * Called from the attack hook before the hit leaves: the sprint flag has to be off in the
         * same tick as the swing, because a tick later the knockback has already been decided. The
         * old version only watched the mouse button, which never fires for aura or bot hits.
         */
        public static boolean beforeSwing(Player attacker) {
            WTap w = live;
            if (w == null || !w.enabled() || attacker == null) return false;
            if (!attacker.isSprinting()) return false;
            if (RNG.nextFloat() * 100f >= w.chance.f()) return false;
            w.tap();
            return true;
        }

        private void tap() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.setSprinting(false);
            if (mc.options != null) {
                sprintWas = mc.options.keySprint.isDown();
                // Vanilla starts sprinting again the moment the sprint key is held and you move,
                // so the key has to come off too or the tap lasts one tick at most.
                mc.options.keySprint.setDown(false);
            }
            off = Math.max(off, ticks.i());
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) {
                off = 0;
                attackWas = false;
                return;
            }
            var me = mc.player;
            boolean attack = mc.options.keyAttack.isDown();
            if (attack && !attackWas && me.isSprinting() && mc.hitResult instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity && RNG.nextInt(100) < chance.i()) {
                tap();
            }
            attackWas = attack;
            if (off > 0) {
                off--;
                me.setSprinting(false);
                mc.options.keySprint.setDown(false);
                if (off == 0) {
                    boolean want = sprintWas || mc.options.keyUp.isDown();
                    mc.options.keySprint.setDown(want);
                    if (want) me.setSprinting(true);
                }
            }
        }

        @Override
        protected void onDisable() {
            off = 0;
            attackWas = false;
        }
    }

    public static final class JumpReset extends Module {
        private final Setting.Num chance = add(new Setting.Num("Chance", "How often it jumps when you are hit.", 100, 10, 100, 5, " %"));
        private int lastHurt;

        public JumpReset() {
            super("jumpreset", "Jump Reset", "Jumps the tick a hit lands, to take less of the knockback.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) return;
            var me = mc.player;
            int hurt = me.hurtTime;
            boolean fresh = hurt > 0 && hurt >= lastHurt && hurt >= 8;
            lastHurt = hurt;
            if (!fresh || !me.onGround() || me.isInWater() || me.onClimbable() || me.isPassenger()) return;
            if (RNG.nextInt(100) >= chance.i()) return;
            Vec3 v = me.getDeltaMovement();
            me.setDeltaMovement(v.x, Math.max(v.y, 0.42), v.z);
        }
    }

    public static final class ShieldBreaker extends Module {
        private final Setting.Bool autoHit = add(new Setting.Bool("Hit for me", "Break the shield and swing even without a click.", false));
        private final Setting.Bool mace = add(new Setting.Bool("Follow with the mace", "The axe knocks the shield down and the mace lands on the open target in the same tick.", true));
        private final Setting.Bool back = add(new Setting.Bool("Switch back", "Go back to the slot you had afterwards.", true));
        private final Setting.Num range = add(new Setting.Num("Hit from", "How far the shielded target can be. The Reach module raises this by itself.", 3.0, 2.5, 6.0, 0.1, " blocks"));
        private int previous = -1;
        private int hold;
        private boolean attackWas;
        private long didAt;
        private String note;

        public ShieldBreaker() {
            super("shieldbreaker", "Shield Breaker", "Left click into a raised shield: an axe hit takes the shield down and a mace swing follows it in the same moment.", Category.COMBAT);
            alias("shieldbreak", "shieldbreak combo");
        }

        @Override
        public String info() {
            return note;
        }

        private LivingEntity shielded(Minecraft mc) {
            if (!(mc.hitResult instanceof EntityHitResult ehr)) return null;
            if (!(ehr.getEntity() instanceof LivingEntity le) || le == mc.player || !le.isAlive()) return null;
            if (Friends.protects(le)) return null;
            return le.isBlocking() || le.isUsingItem() && le.getUseItem().is(Items.SHIELD) ? le : null;
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            var me = mc.player;
            boolean clicked = mc.options.keyAttack.isDown() && !attackWas;
            attackWas = mc.options.keyAttack.isDown();
            LivingEntity shield = shielded(mc);
            if (shield == null) {
                if (hold > 0 && --hold == 0 && previous >= 0) {
                    if (back.on()) Inv.select(previous);
                    previous = -1;
                }
                note = null;
                return;
            }
            hold = 6;
            double reach = Math.max(range.get(), Combat.Reach.entityRange() - 0.02);
            if (Aim.reachTo(shield) > reach + 0.4) {
                note = "too far for the combo";
                return;
            }
            note = shield.getName().getString() + " is blocking";
            int axe = Inv.hotbar(s -> Names.item(s).endsWith("_axe"));
            if (axe < 0) {
                note = "no axe in your hotbar";
                return;
            }
            if (previous < 0) previous = Inv.selected();
            boolean go = clicked || (autoHit.on() && me.getAttackStrengthScale(0f) >= 0.95f
                    && System.currentTimeMillis() - didAt > 140);
            if (!go) {
                if (axe != Inv.selected()) Inv.select(axe);
                return;
            }
            if (MaceMods.MaceAura.busy()) return;
            breakCombo(mc, shield, axe);
        }

        /** Axe first, mace second, both before the server answers: the shield is down for the mace. */
        private void breakCombo(Minecraft mc, LivingEntity target, int axe) {
            var me = mc.player;
            Aim.look(target.getBoundingBox().getCenter());
            Inv.select(axe);
            Hits.attempt(target);
            mc.gameMode.attack(me, target);
            me.swing(InteractionHand.MAIN_HAND);
            didAt = System.currentTimeMillis();
            if (!mace.on()) return;
            int slot = Inv.hotbar(s -> s.is(Items.MACE));
            if (slot < 0) {
                note = "shield broken, no mace to follow";
                return;
            }
            Inv.select(slot);
            MaceMods.MaceDamage.beforeAttack(me, target);
            Hits.attempt(target);
            mc.gameMode.attack(me, target);
            me.swing(InteractionHand.MAIN_HAND);
            note = "shield broken and maced";
        }

        @Override
        protected void onDisable() {
            previous = -1;
            hold = 0;
            note = null;
        }
    }

    public static final class MaceSwap extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Num minFall = add(new Setting.Num("Fall at least", "Swap once you have fallen this far.", 1.5, 0.5, 8, 0.5, " blocks"));
        private final Setting.Bool back = add(new Setting.Bool("Switch back", "Go back to the slot you had after landing.", true));
        private int previous = -1;

        public MaceSwap() {
            super("maceswap", "Mace Swap", "Swaps to your mace when you fall towards a target, for the smash hit.", Category.COMBAT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (!ready(mc)) return;
            var me = mc.player;
            boolean falling = !me.onGround() && me.fallDistance >= minFall.get() && me.getDeltaMovement().y < 0 && !me.isInWater();
            boolean target = mc.hitResult instanceof EntityHitResult ehr && Targets.accepts(mc, who, ehr.getEntity());
            if (falling && target) {
                int mace = Inv.hotbar(s -> Names.item(s).equals("mace"));
                if (mace >= 0 && mace != Inv.selected()) {
                    if (previous < 0) previous = Inv.selected();
                    Inv.select(mace);
                }
            } else if (me.onGround() && previous >= 0) {
                if (back.on()) Inv.select(previous);
                previous = -1;
            }
        }

        @Override
        protected void onDisable() {
            previous = -1;
        }
    }

    public static final class KeyThrow extends Module {
        private final Setting.Choice what = add(new Setting.Choice("Throw", "What to throw.", 0, "Ender pearl", "Wind charge"));

        public KeyThrow() {
            super("keythrow", "Key Throw", "Throws a pearl or a wind charge with a key and swaps back. Put it on a key.", Category.COMBAT);
            oneShot();
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return;
            Item item = what.index() == 0 ? Items.ENDER_PEARL : Items.WIND_CHARGE;
            Predicate<net.minecraft.world.item.ItemStack> p = s -> s.is(item);
            if (Inv.offhand(p)) {
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                return;
            }
            int slot = Inv.hotbar(p);
            if (slot < 0) {
                Zeus.toast("Key Throw: no " + what.value().toLowerCase(Locale.ROOT) + " in your hotbar", false);
                return;
            }
            Inv.with(slot, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        }
    }

    public static final class AirStuck extends Module {
        public AirStuck() {
            super("airstuck", "Air Stuck", "Freezes you in place, even mid-air.", Category.MOVEMENT);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null) return;
            mc.player.setDeltaMovement(0, 0, 0);
            mc.player.setNoGravity(true);
        }

        @Override
        protected void onDisable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.setNoGravity(false);
        }
    }

    public static final class LowHealthWarning extends Module {
        private final Setting.Num below = add(new Setting.Num("Below", "The warning starts at this many hearts.", 6, 1, 19, 1, " hearts"));
        private final Setting.Num strength = add(new Setting.Num("Strength", "How strong the red edge is.", 70, 10, 100, 5, " %"));
        private final Setting.Bool pulse = add(new Setting.Bool("Pulse", "The edge breathes instead of staying still.", true));

        public LowHealthWarning() {
            super("lowhealth", "Low Health Warning", "Flashes the screen edges when your health is low.", Category.RENDER);
            anywhere();
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;
            float hearts = (mc.player.getHealth() + mc.player.getAbsorptionAmount()) / 2f;
            if (hearts > below.get()) return;
            float need = Math.max(0.1f, 1f - hearts / below.f());
            float breathe = pulse.on() ? 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / (260.0 - 120.0 * need)) : 1f;
            float a = Math.min(1f, strength.f() / 100f * (0.35f + 0.65f * need) * breathe);
            int edge = Math.max(14, Math.round(Math.min(w, h) * (0.10f + 0.10f * need)));
            int strong = Draw.rgba(0xFFFF2B3A, a);
            int none = Draw.rgba(0xFFFF2B3A, 0f);
            g.fillGradient(0, 0, w, edge, strong, none);
            g.fillGradient(0, h - edge, w, h, none, strong);
            for (int i = 0; i < edge; i += 2) {
                int c = Draw.rgba(0xFFFF2B3A, a * (1f - i / (float) edge));
                g.fill(i, edge, i + 2, h - edge, c);
                g.fill(w - i - 2, edge, w - i, h - edge, c);
            }
        }
    }

    public static final class Nametags extends Module {
        private final Setting.Num range = add(new Setting.Num("Range", "Furthest player that gets a tag.", 64, 8, 256, 8, " blocks"));
        private final Setting.Bool health = add(new Setting.Bool("Health", "Show the health.", true));
        private final Setting.Bool ping = add(new Setting.Bool("Ping", "Show the ping.", true));
        private final Setting.Bool distance = add(new Setting.Bool("Distance", "Show how far away the player is.", true));
        private final Setting.Bool held = add(new Setting.Bool("Held item", "Show the item in the main hand.", true));
        private final Setting.Bool friends = add(new Setting.Bool("Mark friends", "Friends from the Friends module get a green name.", true));
        private final Setting.Bool spectators = add(new Setting.Bool("Skip spectators", "No tag for players in spectator mode.", true));
        private final Setting.Num size = add(new Setting.Num("Size", "Size of the tags.", 100, 60, 200, 10, " %"));
        private final Setting.Num maxTags = add(new Setting.Num("Most tags", "Nearest players first.", 20, 4, 60, 2, ""));
        private int shown;

        public Nametags() {
            super("nametags", "Nametags", "Player nametags with held item, health, ping and distance.", Category.RENDER);
        }

        @Override
        public String info() {
            return shown > 0 ? Integer.toString(shown) : null;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            shown = 0;
            if (mc.level == null || mc.player == null || !Projector.prepare(mc, partial, w, h)) return;
            List<Player> list = new ArrayList<>();
            for (Entity e : Env.entities(mc, range.get())) {
                if (e instanceof Player p && p != mc.player && p.isAlive() && !(spectators.on() && p.isSpectator())) list.add(p);
            }
            list.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a), mc.player.distanceToSqr(b)));
            float sc = size.f() / 100f;
            float[] t = new float[2];
            for (Player p : list) {
                if (shown >= maxTags.i()) break;
                double dist = Math.sqrt(mc.player.distanceToSqr(p));
                if (dist > range.get()) continue;
                double x = p.xo + (p.getX() - p.xo) * partial;
                double y = p.yo + (p.getY() - p.yo) * partial;
                double z = p.zo + (p.getZ() - p.zo) * partial;
                if (!Projector.project(x, y + p.getBbHeight() + 0.55, z, t)) continue;
                if (t[0] < -100 || t[0] > w + 100 || t[1] < -40 || t[1] > h + 40) continue;
                shown++;
                float hp = p.getHealth() + p.getAbsorptionAmount();
                int hpColor = hp > 14 ? 0xFF5EE67A : hp > 7 ? 0xFFFFD84D : 0xFFFF4D5E;
                StringBuilder tail = new StringBuilder();
                if (health.on()) tail.append(' ').append(Math.round(hp));
                String name = p.getName().getString();
                boolean friend = friends.on() && Friends.isFriend(p);
                String right = "";
                if (ping.on()) {
                    PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.getUUID());
                    if (info != null) right += info.getLatency() + "ms ";
                }
                if (distance.on()) right += Math.round(dist) + "m";
                String item = held.on() && !p.getMainHandItem().isEmpty() ? p.getMainHandItem().getHoverName().getString() : "";
                int nameW = Draw.widthS(name, sc);
                int tailW = Draw.widthS(tail.toString(), sc);
                int bw = Math.max(nameW + tailW, Math.max(Draw.widthS(right, sc * 0.8f), Draw.widthS(item, sc * 0.8f))) + Math.round(10 * sc);
                int lines = 1 + (right.isEmpty() ? 0 : 1) + (item.isEmpty() ? 0 : 1);
                int bh = Math.round((12 + (lines - 1) * 9) * sc) + 2;
                int bx = Math.round(t[0] - bw / 2f);
                int by = Math.round(t[1]) - bh;
                float fade = (float) Math.max(0.45, 1.0 - dist / (range.get() * 1.3));
                Draw.rrect(g, bx, by, bw, bh, 4, Draw.rgba(0xFF0B0F1A, 0.72f * fade), Draw.rgba(0xFF111827, 0.72f * fade));
                int x0 = bx + Math.round(5 * sc);
                int yy = by + Math.round(2 * sc);
                Draw.textS(g, name, x0, yy, Draw.rgba(friend ? 0xFF5EE67A : 0xFFFFFFFF, fade), sc);
                if (health.on()) Draw.textS(g, tail.toString(), x0 + nameW, yy, Draw.rgba(hpColor, fade), sc);
                yy += Math.round(10 * sc);
                if (!right.isEmpty()) {
                    Draw.textS(g, right, x0, yy, Draw.rgba(0xFF9AA3B5, fade), sc * 0.8f);
                    yy += Math.round(9 * sc);
                }
                if (!item.isEmpty()) Draw.textS(g, Draw.fit(item, Math.round(bw / Math.max(0.5f, sc * 0.8f))), x0, yy, Draw.rgba(0xFFFFD76A, fade), sc * 0.8f);
            }
        }
    }

    public static final class TargetEsp extends Module {
        private final Targets.Pick who = Targets.pick(this::add, true, true, false);
        private final Setting.Choice color = add(new Setting.Choice("Colour", "Automatic: green to red with the target's health.", 0, EspKit.COLOR_NAMES));
        private final Setting.Num radius = add(new Setting.Num("Ring size", "Size of the ring.", 1.0, 0.5, 2.5, 0.1, ""));
        private final Setting.Num hold = add(new Setting.Num("Keep for", "How long the ring stays after your last hit.", 2.5, 0.5, 8, 0.5, " s"));
        private LivingEntity target;
        private long until;

        public TargetEsp() {
            super("targetesp", "Target ESP", "Draws a ring on the entity you are fighting.", Category.RENDER);
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.player == null || mc.level == null) {
                target = null;
                return;
            }
            long now = System.currentTimeMillis();
            if (mc.options.keyAttack.isDown() && mc.hitResult instanceof EntityHitResult ehr && Targets.accepts(mc, who, ehr.getEntity())) {
                target = (LivingEntity) ehr.getEntity();
                until = now + (long) (hold.get() * 1000);
            }
            if (target != null && (now > until || !target.isAlive())) target = null;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            LivingEntity e = target;
            if (e == null || mc.player == null || !Projector.prepare(mc, partial, w, h)) return;
            double cx = e.xo + (e.getX() - e.xo) * partial;
            double cy = e.yo + (e.getY() - e.yo) * partial;
            double cz = e.zo + (e.getZ() - e.zo) * partial;
            float frac = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
            int col = EspKit.pick(color, Draw.mix(0xFFFF4D5E, 0xFF5EE67A, frac));
            double t = System.currentTimeMillis() / 1000.0;
            double r = radius.get() * Math.max(0.4, e.getBbWidth());
            double bob = e.getBbHeight() * (0.5 + 0.45 * Math.sin(t * 2.2));
            ring(g, cx, cy + bob, cz, r, t * 2.0, col, 1f);
            ring(g, cx, cy + e.getBbHeight() * (0.5 - 0.45 * Math.sin(t * 2.2)), cz, r * 0.85, -t * 1.6, col, 0.45f);
        }
    }

    private static void ring(GuiGraphics g, double x, double y, double z, double r, double spin, int color, float alpha) {
        int n = 28;
        for (int i = 0; i < n; i++) {
            double a1 = spin + i * (Math.PI * 2 / n);
            double a2 = spin + (i + 1) * (Math.PI * 2 / n);
            float fade = (0.35f + 0.65f * (i / (float) n)) * alpha;
            Wire.line(g, x + Math.cos(a1) * r, y, z + Math.sin(a1) * r, x + Math.cos(a2) * r, y, z + Math.sin(a2) * r, 1.8f, Draw.rgba(color, fade), true);
        }
    }

    private static volatile CustomCrosshair crosshair;

    public static boolean hideCrosshair() {
        CustomCrosshair c = crosshair;
        return c != null && c.enabled() && c.hideVanilla.on();
    }

    public static final class CustomCrosshair extends Module {
        private final Setting.Choice style = add(new Setting.Choice("Style", "The shape.", 0, "Cross", "Dot", "Circle", "Cross and dot"));
        private final Setting.Num size = add(new Setting.Num("Size", "Length of each arm.", 6, 1, 20, 1, " px"));
        private final Setting.Num gap = add(new Setting.Num("Gap", "Empty space in the middle.", 3, 0, 12, 1, " px"));
        private final Setting.Num thick = add(new Setting.Num("Thickness", "Line thickness.", 1, 1, 5, 1, " px"));
        private final Setting.Choice color = add(new Setting.Choice("Colour", "The colour.", 1, EspKit.COLOR_NAMES));
        private final Setting.Bool outline = add(new Setting.Bool("Outline", "A dark edge so it reads on any background.", true));
        private final Setting.Bool dynamic = add(new Setting.Bool("Open when moving", "The gap grows while you move.", false));
        private final Setting.Bool redOnTarget = add(new Setting.Bool("Red on target", "Turns red while an entity is under it.", true));
        final Setting.Bool hideVanilla = add(new Setting.Bool("Hide vanilla crosshair", "Do not draw the normal crosshair.", true));

        public CustomCrosshair() {
            super("customcrosshair", "Custom Crosshair", "Draws a configurable crosshair in place of the vanilla one.", Category.RENDER);
            anywhere();
            crosshair = this;
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
            int cx = w / 2;
            int cy = h / 2;
            int c = EspKit.pick(color, 0xFFFFFFFF);
            if (redOnTarget.on() && mc.hitResult instanceof EntityHitResult) c = 0xFFFF4D5E;
            int gp = gap.i();
            if (dynamic.on() && mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0004) gp += 3;
            int len = size.i();
            int t = thick.i();
            int lo = t / 2;
            int hi = t - lo;
            int main = Draw.rgba(c, 1f);
            int edge = Draw.rgba(0xFF000000, 0.8f);
            int st = style.index();
            if (st == 0 || st == 3) {
                int[][] arms = {{cx - gp - len, cy - lo, cx - gp, cy + hi}, {cx + gp, cy - lo, cx + gp + len, cy + hi}, {cx - lo, cy - gp - len, cx + hi, cy - gp}, {cx - lo, cy + gp, cx + hi, cy + gp + len}};
                if (outline.on()) for (int[] a : arms) g.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, edge);
                for (int[] a : arms) g.fill(a[0], a[1], a[2], a[3], main);
            }
            if (st == 1 || st == 3) {
                int d = Math.max(1, t);
                if (outline.on()) g.fill(cx - lo - 1, cy - lo - 1, cx + hi + 1, cy + hi + 1, edge);
                g.fill(cx - lo, cy - lo, cx + hi, cy + hi, main);
            }
            if (st == 2) {
                int rr = gp + len / 2 + 2;
                for (int i = 0; i < 24; i++) {
                    double a1 = i * Math.PI / 12;
                    double a2 = (i + 1) * Math.PI / 12;
                    if (outline.on()) Draw.line(g, (float) (cx + Math.cos(a1) * rr), (float) (cy + Math.sin(a1) * rr), (float) (cx + Math.cos(a2) * rr), (float) (cy + Math.sin(a2) * rr), t + 2f, edge);
                }
                for (int i = 0; i < 24; i++) {
                    double a1 = i * Math.PI / 12;
                    double a2 = (i + 1) * Math.PI / 12;
                    Draw.line(g, (float) (cx + Math.cos(a1) * rr), (float) (cy + Math.sin(a1) * rr), (float) (cx + Math.cos(a2) * rr), (float) (cy + Math.sin(a2) * rr), t, main);
                }
            }
        }
    }

    public static final class RegionMap extends Module {
        private final Setting.Choice corner = Hud.corner(this, 3);
        private final Setting.Num cell = add(new Setting.Num("Cell size", "Pixels per grid square.", 4, 2, 8, 1, " px"));
        private final Setting.Bool label = add(new Setting.Bool("Goliath name", "Write the name of your Goliath under the map.", true));
        private final Setting.Num opacity = add(new Setting.Num("Background", "How solid the card is.", 85, 30, 100, 5, " %"));
        private static final int[] REGION_COLORS = {0xFF4DA3FF, 0xFF35E0D0, 0xFF8C7BFF, 0xFFFF9F43, 0xFFFFD84D, Accent.BAD, 0xFF5EE67A};

        public RegionMap() {
            super("regionmap", "Region Map", "DonutSMP Goliath map with a dot for where you are.", Category.PANELS);
            anywhere();
        }

        @Override
        protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
            if (mc.player == null || mc.level == null) return;
            int cs = cell.i();
            int n = GoliathGrid.GRID;
            int pad = 6;
            int mapW = n * cs;
            int head = 14;
            int foot = label.on() ? 12 : 0;
            int bw = mapW + pad * 2;
            int bh = head + mapW + pad + foot;
            int[] at = Hud.place(corner.index(), bw, bh, w, h);
            float bg = opacity.f() / 100f;
            int ac = Hud.accent();
            Hud.panel(g, at[0], at[1], bw, bh);
            Draw.rrectLine(g, at[0], at[1], bw, bh, 7, Draw.rgba(ac, 0.55f));
            Draw.boldS(g, "REGION MAP", at[0] + pad, at[1] + 4, 0xFFFFFFFF, 0.9f);
            int ox = at[0] + pad;
            int oy = at[1] + head;
            int here = MediaBaseFinder.overworld(mc) ? GoliathGrid.at(mc.player.getX(), mc.player.getZ()) : -1;
            for (int z = 0; z < n; z++) {
                int x = 0;
                while (x < n) {
                    int code = GoliathGrid.square(x, z);
                    int run = 1;
                    while (x + run < n && GoliathGrid.square(x + run, z) == code) run++;
                    if (code >= 0) {
                        int region = Math.max(0, Math.min(REGION_COLORS.length - 1, code / 1000));
                        float shade = (code % 2 == 0) ? 0.55f : 0.40f;
                        float a = code == here ? 0.95f : shade;
                        g.fill(ox + x * cs, oy + z * cs, ox + (x + run) * cs, oy + (z + 1) * cs, Draw.rgba(REGION_COLORS[region], a));
                    }
                    x += run;
                }
            }
            if (here >= 0) {
                int[] b = GoliathGrid.bounds(here);
                if (b != null) Draw.outline(g, ox + b[0] * cs, oy + b[1] * cs, ox + b[2] * cs, oy + b[3] * cs, 0xFFFFFFFF);
            }
            double gx = (mc.player.getX() + GoliathGrid.HALF) / GoliathGrid.SQUARE;
            double gz = (mc.player.getZ() + GoliathGrid.HALF) / GoliathGrid.SQUARE;
            if (gx >= 0 && gz >= 0 && gx < n && gz < n) {
                int px = ox + (int) Math.round(gx * cs);
                int py = oy + (int) Math.round(gz * cs);
                double yaw = Math.toRadians(mc.player.getYRot());
                Draw.line(g, px, py, (float) (px - Math.sin(yaw) * 7), (float) (py + Math.cos(yaw) * 7), 1.4f, 0xFFFFFFFF);
                g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFF3B4D);
                Draw.outline(g, px - 2, py - 2, px + 3, py + 3, 0xFF000000);
            }
            if (label.on()) Draw.textCenterS(g, GoliathGrid.label(here), at[0] + bw / 2, oy + mapW + 3, 0xFFC9D4EC, 0.85f);
        }
    }

    public static final class BlockNotifier extends Module {
        private final Setting.Text blocks = add(new Setting.Text("Blocks", "Block ids separated by commas, e.g. spawner, ancient_debris, beacon. A * at the start or end of a name matches more.", "spawner, trial_spawner, ancient_debris", 400));
        private final Setting.Num range = add(new Setting.Num("Range", "How far around you it looks.", 48, 16, 128, 8, " blocks"));
        private final Setting.Num gap = add(new Setting.Num("Seconds between toasts", "So a big find does not flood the screen.", 3, 1, 30, 1, " s"));
        private final Set<Long> told = new HashSet<>();
        private final Map<Block, Boolean> wanted = new ConcurrentHashMap<>();
        private final List<String> keys = new ArrayList<>();
        private String parsedFrom = "\u0000";
        private int tick;
        private long lastToast;
        private List<long[]> queue = new ArrayList<>();
        private final Map<Long, Long> scannedAt = new HashMap<>();

        public BlockNotifier() {
            super("blocknotifier", "Block Notifier", "Shows a toast with the block and its coordinates when one of your chosen blocks shows up.", Category.UTILITY);
            blocks.onChange(() -> {
                told.clear();
                scannedAt.clear();
            });
        }

        @Override
        protected void onEnable() {
            told.clear();
            scannedAt.clear();
            queue = new ArrayList<>();
        }

        private void parse() {
            String text = blocks.get();
            if (text.equals(parsedFrom)) return;
            parsedFrom = text;
            keys.clear();
            wanted.clear();
            for (String p : text.split(",")) {
                String k = p.trim().toLowerCase(Locale.ROOT);
                if (k.startsWith("minecraft:")) k = k.substring(10);
                if (!k.isEmpty()) keys.add(k);
            }
        }

        private boolean isWanted(Block b) {
            return wanted.computeIfAbsent(b, block -> {
                try {
                    String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                    id = id.substring(id.indexOf(58) + 1);
                    for (String k : keys) {
                        boolean start = k.endsWith("*");
                        boolean end = k.startsWith("*");
                        boolean hit = start && end ? k.length() > 2 && id.contains(k.substring(1, k.length() - 1))
                                : start ? id.startsWith(k.substring(0, k.length() - 1))
                                : end ? id.endsWith(k.substring(1)) : id.equals(k);
                        if (hit) return true;
                    }
                } catch (RuntimeException ignored) {
                    return false;
                }
                return false;
            });
        }

        @Override
        protected void onTick(Minecraft mc) {
            if (mc.level == null || mc.player == null) return;
            parse();
            if (keys.isEmpty()) return;
            int pcx = mc.player.getBlockX() >> 4;
            int pcz = mc.player.getBlockZ() >> 4;
            int rc = (int) Math.ceil(range.get() / 16.0);
            long now = System.currentTimeMillis();
            if (tick++ % 40 == 0) {
                queue = new ArrayList<>();
                for (int dx = -rc; dx <= rc; dx++) {
                    for (int dz = -rc; dz <= rc; dz++) {
                        long k = (long) (pcx + dx) << 32 | (long) (pcz + dz) & 0xFFFFFFFFL;
                        Long at = scannedAt.get(k);
                        if (at != null && now - at <= 15000L) continue;
                        queue.add(new long[] {dx * dx + dz * dz, pcx + dx, pcz + dz});
                    }
                }
                queue.sort((a, b) -> Long.compare(a[0], b[0]));
                if (told.size() > 2000) told.clear();
            }
            long deadline = System.nanoTime() + 1_500_000L;
            while (!queue.isEmpty() && System.nanoTime() < deadline) {
                long[] c = queue.remove(0);
                scan(mc, (int) c[1], (int) c[2], now);
            }
        }

        private void scan(Minecraft mc, int cx, int cz, long now) {
            LevelChunk chunk = Env.chunk(mc, cx, cz);
            if (chunk == null) return;
            scannedAt.put((long) cx << 32 | (long) cz & 0xFFFFFFFFL, now);
            LevelChunkSection[] sections = chunk.getSections();
            int minY = chunk.getMinSectionY() << 4;
            double r2 = range.get() * range.get();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(st -> isWanted(st.getBlock()))) continue;
                int by = minY + i * 16;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            Block b = sec.getBlockState(x, y, z).getBlock();
                            if (!isWanted(b)) continue;
                            int wx = (cx << 4) + x;
                            int wy = by + y;
                            int wz = (cz << 4) + z;
                            if (mc.player.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5) > r2) continue;
                            long key = BlockPos.asLong(wx, wy, wz);
                            if (!told.add(key)) continue;
                            if ((double) (now - lastToast) < gap.get() * 1000.0) continue;
                            lastToast = now;
                            Zeus.toast(b.getName().getString() + " at " + wx + " " + wy + " " + wz, true);
                        }
                    }
                }
            }
        }
    }

    public static final class CoordSnapper extends Module {
        private final Setting.Choice format = add(new Setting.Choice("Format", "How the numbers are written.", 0, "x y z", "x, y, z", "/tp x y z", "x z"));

        public CoordSnapper() {
            super("coordsnapper", "Coord Snapper", "Copies the coordinates you look at or stand on with one key.", Category.UTILITY);
            oneShot();
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            HitResult hit = mc.hitResult;
            BlockPos p = hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS ? bhr.getBlockPos() : mc.player.blockPosition();
            String text = switch (format.index()) {
                case 1 -> p.getX() + ", " + p.getY() + ", " + p.getZ();
                case 2 -> "/tp " + p.getX() + " " + p.getY() + " " + p.getZ();
                case 3 -> p.getX() + " " + p.getZ();
                default -> p.getX() + " " + p.getY() + " " + p.getZ();
            };
            try {
                mc.keyboardHandler.setClipboard(text);
                Zeus.toast("Copied " + text, true);
            } catch (RuntimeException e) {
                Zeus.toast("Could not reach the clipboard", false);
            }
        }
    }

    public static final class ChatMacro extends Module {
        private final Setting.Text message = add(new Setting.Text("Message", "What to send. Start with / for a command.", "", 256));

        public ChatMacro(int n) {
            super("chatmacro" + n, "Chat Macro " + n, "Sends a chat message or a command with a key press. Put it on a key.", Category.UTILITY);
            oneShot();
        }

        @Override
        protected void onEnable() {
            Minecraft mc = Minecraft.getInstance();
            String m = message.get().trim();
            if (m.isEmpty() || mc.getConnection() == null) {
                Zeus.toast("Chat Macro: nothing to send", false);
                return;
            }
            if (m.startsWith("/")) mc.getConnection().sendCommand(m.substring(1));
            else mc.getConnection().sendChat(m);
        }
    }

    public static List<Module> all() {
        List<Module> m = new ArrayList<>();
        m.add(new Triggerbot());
        m.add(new WTap());
        m.add(new JumpReset());
        m.add(new ShieldBreaker());
        m.add(new MaceSwap());
        m.add(new KeyThrow());
        m.add(new AirStuck());
        m.add(new LowHealthWarning());
        m.add(new Nametags());
        m.add(new TargetEsp());
        m.add(new CustomCrosshair());
        m.add(new RegionMap());
        m.add(new BlockNotifier());
        m.add(new CoordSnapper());
        m.add(new ChatMacro(1));
        m.add(new ChatMacro(2));
        m.add(new ChatMacro(3));
        return m;
    }
}
