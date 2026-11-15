package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Mining;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class NoBreakDelayMixin {
    @Shadow
    private int destroyDelay;

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void zeus$noBreakDelay(CallbackInfo ci) {
        try {
            if (destroyDelay > 0 && Mining.NoBreakDelay.active()) destroyDelay = 0;
        } catch (RuntimeException ignored) {
        }
    }
}
