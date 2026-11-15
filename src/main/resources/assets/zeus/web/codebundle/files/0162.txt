package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Module;

public final class Mining {
    private Mining() {}

    public static final class NoBreakDelay extends Module {
        public NoBreakDelay() {
            super("nobreakdelay", "No Break Delay", "Removes the small wait between finishing one block and starting the next, so mining keeps flowing.", Category.WORLD);
        }

        public static boolean active() {
            NoBreakDelay self = com.zlorbie788.zeus.core.Zeus.get(NoBreakDelay.class);
            return self != null && self.enabled();
        }
    }
}
