package com.zlorbie788.zeus.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The window (and the task bar tooltip) says "Minecraft 1.21.11 - Zeus Client" everywhere, in menus and in a world. */
@Mixin(Minecraft.class)
public abstract class WindowTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$title(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Minecraft 1.21.11 - Zeus Client");
    }
}
