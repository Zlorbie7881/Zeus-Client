package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.ui.ZeusSkins;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the start and the end of every screen's drawing, for the Zeus look on the pause menu and the world and server lists. */
@Mixin(Screen.class)
public abstract class ScreenSkinMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void zeus$before(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ZeusSkins.before((Screen) (Object) this);
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void zeus$after(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ZeusSkins.after((Screen) (Object) this, graphics, mouseX, mouseY);
    }
}
