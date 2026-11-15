package com.zlorbie788.zeus.core;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

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

    public String error;

    long profNs;

    public volatile float costPct;
    private boolean light;
    private int skip;

    public static final class Holder {
        private final List<Setting> list = new ArrayList<>();

        public <T extends Setting> T add(T s) {
            list.add(s);
            return s;
        }

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

    public <T extends Setting> T addSetting(T s) {
        return add(s);
    }

    protected Module anywhere() {
        startsOff = false;
        return this;
    }

    private final List<String> aliases = new ArrayList<>();

    protected Module alias(String... names) {
        for (String n : names) if (n != null && !n.isBlank()) aliases.add(n);
        return this;
    }

    public List<String> aliases() {
        return aliases;
    }

    private boolean hudCard;

    protected Module hudCard() {
        hudCard = true;
        return this;
    }

    public boolean isHudWidget() {
        return category == Category.PANELS || hudCard;
    }

    protected Module light() {
        light = true;
        return this;
    }

    protected Module oneShot() {
        oneShot = true;
        return this;
    }

    protected Module airborne() {
        airborne = true;
        return this;
    }

    public boolean isAirborne() {
        return airborne;
    }

    protected Module panelOnly() {
        toggleable = false;
        return this;
    }

    private boolean locked;
    private String note;

    protected Module locked(String why) {
        locked = true;
        note = why;
        return this;
    }

    public boolean locked() {
        return locked;
    }

    public String note() {
        return note;
    }

    public List<Setting> settings() {
        return settings;
    }

    public boolean enabled() {
        return enabled;
    }

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

    void restart() {
        if (!enabled) return;
        try {
            onDisable();
            onEnable();
        } catch (Throwable t) {
            fail(t);
        }
    }

    void quietForWorldExit() {
        if (startsOff) quiet();
    }

    void quiet() {
        if (!enabled) return;
        enabled = false;
        Zeus.activeChanged();
        try {
            onDisable();
        } catch (Throwable ignored) {
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
        }
        Zeus.toast(name + " hit a problem and was switched off", false);
    }

    protected void onEnable() {}

    protected void onDisable() {}

    protected void onTick(Minecraft mc) {}

    protected void onHud(GuiGraphics g, Minecraft mc, float partial, int w, int h) {}

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

    public final void tickFromComposite(Minecraft mc) {
        if (enabled()) tick(mc);
    }

    public final void hudFromComposite(GuiGraphics g, Minecraft mc, float partial, int w, int h) {
        if (enabled()) hud(g, mc, partial, w, h);
    }
}
