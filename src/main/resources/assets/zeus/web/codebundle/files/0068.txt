package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Fullbright;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    @Inject(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$light(CallbackInfoReturnable<Integer> cir) {
        if (Fullbright.engineLight()) {
            Fullbright.sawEngine();
            cir.setReturnValue(15);
        }
    }
}
