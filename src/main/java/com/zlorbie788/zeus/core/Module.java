package com.zlorbie788.zeus.core;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * One feature. A module can be switched on and off, owns its settings and a key, and gets a tick and a draw call while it
 * is on. Everything is client side: a module changes what your own game does and draws, never anything on a server.
 */
public abstract class Module {
    public final String id;
    public final String name;
    public final String desc;
    public final Category category;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;
    private boolean startsOff = true;
    private boolean toggleable = true;
    private boolean oneShot;
    private boolean airborne;
    public KeyMapping key;
    /** Set when the module failed, so the panel can say so. */
    public String error;
    /** Nanoseconds this module used since the Performance Monitor last rolled over (only counted while it is on). */
    long profNs;
    /** What this module used of the last second of game time, in percent. Only filled while something asked Prof to measure. */
    public volatile float costPct;
    private boolean light;
    private int skip;

    /** A bag of settings that belongs to the panel itself, not to a module. */
    public static final class Holder {
        private final List<Setting> list = new ArrayList<>();

        public <T extends Setting> T add(T s) {
            list.add(s);
            return s;
        }

        /** Inserts a panel setting at the top so important controls are immediately discoverable. */
        public <T extends Setting> T addFirst(T s) {
            list.add(0, s);
            return s;
        }

        public List<Setting> settings() {
            return list;
        }
    }

    private boolean defaultOn;

    public boolean defaultOn() {
        return defaultOn;
    }

    public Module onByDefault() {
        defaultOn = true;
        return this;
    }

    /** True for the heavy scanners and ESPs that may run less often (and that the Performance Guard may switch off). */
    public boolean isLight() {
        return light;
    }

    public void setEnabledQuiet(boolean on) {
        if (!toggleable || locked || on == enabled) return;
        enabled = on;
        Zeus.activeChanged();
        try {
            if (on) onEnable();
            else onDisable();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Called when a world opens. */
    protected void cleanSlate() {}

    protected Module(String id, String name, String desc, Category category) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    /** The same as {@code add}, for helpers outside the module class. */
    public <T extends Setting> T addSetting(T s) {
        return add(s);
    }

    /** A module whose on/off state is remembered between launches (the interface ones). All others start off every launch. */
    protected Module anywhere() {
        startsOff = false;
        return this;
    }

    private final List<String> aliases = new ArrayList<>();

    /** Other names people know this module by. Search, /zeus commands and the website find the module under them too. */
    protected Module alias(String... names) {
        for (String n : names) if (n != null && !n.isBlank()) aliases.add(n);
        return this;
    }

    public List<String> aliases() {
        return aliases;
    }

    private boolean hudCard;

    /** Marks a module outside the HUD & Info category that draws a box on the screen, so the HUD editor can move and scale it. */
    protected Module hudCard() {
        hudCard = true;
        return this;
    }

    /** True for HUD & Info modules and for any module that called {@code hudCard()}: the ones the HUD editor lists. */
    public boolean isHudWidget() {
        return category == Category.PANELS || hudCard;
    }

    /**
     * Marks a module that mostly scans and then draws what it found: in the Light and Minimal performance modes it is ticked
     * every second, third or fourth game tick instead of every tick. Never use it on something that must react at once
     * (fighting, movement).
     */
    protected Module light() {
        light = true;
        return this;
    }

    /** A module that does one thing when it is switched on and then switches itself off again. */
    protected Module oneShot() {
        oneShot = true;
        return this;
    }

    /** A module that keeps you off the ground without a flying permission (in your own singleplayer world Zeus then dips you a little now and then so the game's own floating check does not kick you). */
    protected Module airborne() {
        airborne = true;
        return this;
    }

    public boolean isAirborne() {
        return airborne;
    }

    /** A page of buttons, not a switch. */
    protected Module panelOnly() {
        toggleable = false;
        return this;
    }

    private boolean locked;
    private String note;

    /** A module that is listed but cannot be switched on yet. {@code why} is what the list shows instead of its description. */
    protected Module locked(String why) {
        locked = true;
        note = why;
        return this;
    }

    /** True for a module that is still in development: the switch does nothing and the row says why. */
    public boolean locked() {
        return locked;
    }

    /** The line the module list shows for a locked module, or null for a normal one. */
    public String note() {
        return note;
    }

    public List<Setting> settings() {
        return settings;
    }

    public boolean enabled() {
        return enabled;
    }

    /** True when this module is switched off at every launch, false when its state is remembered. */
    public boolean startsOff() {
        return startsOff;
    }

    public boolean toggleable() {
        return toggleable;
    }

    public void toggle() {
        boolean was = enabled;
        setEnabled(!enabled);
        if (!was && enabled) Usage.used(this);
    }

    public void setEnabled(boolean on) {
        if (Zeus.destroyed()) return;
        if (!toggleable || locked || on == enabled) return;
        enabled = on;
        Zeus.activeChanged();
        error = null;
        try {
            if (on) onEnable();
            else onDisable();
        } catch (Throwable t) {
            fail(t);
        }
        if (on && oneShot && enabled) {
            enabled = false;
            Zeus.activeChanged();
        }
        if (!oneShot) Zeus.event(name + (enabled ? " switched on" : " switched off"), enabled);
        Zeus.markDirty();
    }

    /** Starts the module over, so it picks up settings that were changed behind its back (a profile was loaded). */
    void restart() {
        if (!enabled) return;
        try {
            onDisable();
            onEnable();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Handles a world transition. Modules explicitly marked with anywhere() are
     * client-persistent (HUD, preferences and other world-independent state), so
     * leave their enabled state intact. World-bound modules are always shut down.
     */
    void quietForWorldExit() {
        if (startsOff) quiet();
    }

    /** Switches off without telling the world (used when leaving a world). */
    void quiet() {
        if (!enabled) return;
        enabled = false;
        Zeus.activeChanged();
        try {
            onDisable();
        } catch (Throwable ignored) {
            // the world is going away anyway
        }
    }

    void fail(Throwable t) {
        Zeus.LOG.warn("Module {} failed and was switched off", id, t);
        enabled = false;
        Zeus.activeChanged();
        error = t.getClass().getSimpleName();
        try {
            onDisable();
        } catch (Throwable ignored) {
            // nothing more to do
        }
        Zeus.toast(name + " hit a problem and was switched off", false);
    }

    /** Called once when switched on. */
    protected void onEnable() {}

    /** Called once when switched off. */
    protected void onDisable() {}

    /** Every game tick while on and in a world. */
    protected void onTick(Minecraft mc) {}

    /** Every frame while on, on top of the game picture. For a HUD widget this is the movable box only. */
    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {}

    /** Drawn every frame while on, outside the widget's own position and scale: world markers and centred screen text. */
    protected void onHudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {}

    final void hudWorld(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        long t0 = Prof.on ? System.nanoTime() : 0L;
        try {
            onHudWorld(g, mc, partial, w, h);
        } catch (Throwable t) {
            fail(t);
        }
        if (t0 != 0L) profNs += System.nanoTime() - t0;
    }

    /** What the module list shows after the name, or null. */
    public String info() {
        return null;
    }

    final void tick(Minecraft mc) {
        if (light) {
            int stride = com.zlorbie788.zeus.ui.Perf.stride();
            if (stride > 1 && (++skip % stride) != 0) return;
        }
        long t0 = Prof.on ? System.nanoTime() : 0L;
        try {
            onTick(mc);
        } catch (Throwable t) {
            fail(t);
        }
        if (t0 != 0L) profNs += System.nanoTime() - t0;
    }

    final void hud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        long t0 = Prof.on ? System.nanoTime() : 0L;
        try {
            onHud(g, mc, partial, w, h);
        } catch (Throwable t) {
            fail(t);
        }
        if (t0 != 0L) profNs += System.nanoTime() - t0;
    }

    /** Public dispatch bridge for composite modules in other packages. */
    public final void tickFromComposite(Minecraft mc) {
        if (enabled()) tick(mc);
    }

    /** Public HUD dispatch bridge for composite modules in other packages. */
    public final void hudFromComposite(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (enabled()) hud(g, mc, partial, w, h);
    }
}
