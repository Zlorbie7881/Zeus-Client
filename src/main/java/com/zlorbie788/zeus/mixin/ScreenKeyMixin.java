package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.core.Zeus;
import com.zlorbie788.zeus.ui.PaletteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenKeyMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$openPalette(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() != GLFW.GLFW_KEY_K) return;
        if ((event.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return;
        if (((Object) this) instanceof PaletteScreen) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || Zeus.destroyed()) return;
        mc.setScreen(new PaletteScreen((Screen) (Object) this));
        cir.setReturnValue(true);
    }
}
