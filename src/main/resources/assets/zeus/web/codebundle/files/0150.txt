package com.zlorbie788.zeus.mods;

import com.zlorbie788.zeus.core.Category;
import com.zlorbie788.zeus.core.Hand;
import com.zlorbie788.zeus.core.Module;
import com.zlorbie788.zeus.core.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class HandModel extends Module {
    private final Setting.Num amount = add(new Setting.Num("Shakiness", "How unsteady the aim is. Higher hides more of the machine, and starts to cost accuracy past about 70%.", 35, 0, 100, 5, "%"));
    private final Setting.Info state = add(new Setting.Info("This session", this::lines));

    public HandModel() {
        super("handmodel", "Hand Model", "Puts a human hand in front of automatic aim: a smooth tremor, a slow drift as you play, and a mouse's sensor grain instead of exact angles.", Category.UTILITY);
        anywhere();
        onByDefault();
        alias("hand", "tremor", "humanizer");
    }

    @Override
    protected void onEnable() {
        Hand.set(true, (float) (amount.get() / 100.0));
    }

    @Override
    protected void onDisable() {
        Hand.set(false, 0f);
    }

    @Override
    protected void cleanSlate() {
        Hand.reset();
    }

    @Override
    protected void onTick(Minecraft mc) {
        Hand.set(true, (float) (amount.get() / 100.0));
    }

    private List<String> lines() {
        return List.of(Hand.describe(), "Aim written by a module goes through this before it is sent.");
    }

    @Override
    public String info() {
        return Hand.on() ? Math.round(amount.get()) + "%" : null;
    }
}
