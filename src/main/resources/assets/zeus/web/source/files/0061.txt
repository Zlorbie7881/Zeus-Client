package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.ScreenshotTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every click on a piece of chat text ends up in one of these two methods. The Copy and Delete buttons of Screenshot Tweaks
 * carry a private marker; when one is clicked, Zeus does the work and the game never sees the click.
 */
@Mixin(Screen.class)
public abstract class ShotClickMixin {
    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zeus$shotClickGame(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (ScreenshotTweaks.click(event)) ci.cancel();
    }

    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zeus$shotClickBasic(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (ScreenshotTweaks.click(event)) ci.cancel();
    }
}
