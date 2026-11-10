package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusBackdrop;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The background every screen of the game draws behind itself. Outside a world (server list, world list, options, the
 * disconnect and error screens) Zeus draws the sky of its menu there instead of the game's panorama (see {@link ZeusBackdrop}).
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$background(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ZeusBackdrop.draw((Screen) (Object) this, graphics)) ci.cancel();
    }
}
