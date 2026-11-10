package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's "loading world" screen: Zeus draws the ring with the real percent (singleplayer) or the menu sky with the
 * logo (a server) in its place. Everything else the screen does (ticking, closing when the world is ready) is left alone.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ZeusLoading.level((Screen) (Object) this, graphics)) ci.cancel();
    }
}
