package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Env;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;

/** Player modules. */
public final class PlayerMods {
    private PlayerMods() {}

    /** Makes you giant or tiny. */
    public static final class Resize extends Module {
        private final Setting.Num size = add(new Setting.Num("Size", "1x is normal. Below 1 you look tiny, above 1 you look like a giant. Tight gaps can still push you back.", 0.5, 0.1, 12.0, 0.1, "x"));

        public Resize() {
            super("resize", "Resize", "Become tiny or giant on your screen. The server still treats you as normal size.", Category.PLAYER);
            size.onChange(this::apply);
        }

        private void apply() {
            if (enabled()) Env.attribute("scale", "size", size.get() - 1.0, "add_multiplied_total");
        }

        @Override
        protected void onEnable() {
            apply();
        }

        @Override
        protected void onDisable() {
            Env.attributeOff("scale", "size");
        }

        @Override
        protected void cleanSlate() {
            onDisable();
        }

        @Override
        public String info() {
            return size.text();
        }
    }
}
