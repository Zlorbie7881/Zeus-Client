package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;

/**
 * Do two things at once: eat and attack, hold a shield and attack, draw a bow and hit, mine while you eat, use an item while
 * you mine. The work is done by {@code MultiUseMixin}, which asks this module whether each "one thing at a time" rule of the
 * game still applies.
 */
public final class MultiUse extends Module {
    private static volatile MultiUse self;

    private final Setting.Bool attack = add(new Setting.Bool("Attack while using an item", "Hit mobs and players while you eat, draw a bow or hold a shield. The item keeps being used.", true));
    private final Setting.Bool mine = add(new Setting.Bool("Mine while using an item", "Hold the attack button to break blocks while you use an item.", true));
    private final Setting.Bool use = add(new Setting.Bool("Use an item while mining", "Right click (eat, block, place) while you are breaking a block.", true));

    public MultiUse() {
        super("multiuse", "Multi Use", "Do two things at once: eat and attack, shield and attack, mine while you eat.", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        self = this;
    }

    @Override
    protected void onDisable() {
        if (self == this) self = null;
    }

    @Override
    protected void cleanSlate() {
        // nothing is kept between worlds
    }

    private static MultiUse on() {
        MultiUse m = self;
        return m != null && m.enabled() ? m : null;
    }

    /**
     * For the key handling. While you use an item and still hold the use button, the answer is "no", so the attack clicks
     * are handled. When the use button is let go the real answer is given, so the game stops using the item as usual.
     */
    public static boolean keysUsing(boolean real, boolean useHeld) {
        MultiUse m = on();
        if (m == null || !real || !m.attack.on()) return real;
        return !useHeld && real;
    }

    public static boolean mineUsing(boolean real) {
        MultiUse m = on();
        return m != null && m.mine.on() ? false : real;
    }

    public static boolean useMining(boolean real) {
        MultiUse m = on();
        return m != null && m.use.on() ? false : real;
    }
}
