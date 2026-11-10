package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Fullbright;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The light value of one block position, as every renderer reads it. Sodium reads light straight from here instead of
 * going through the game's helper, which is why the older hook did nothing under it and ores seen through the stone stayed
 * black (the light inside rock is zero). While Fullbright or X-Ray's "Light everything up" is on, this answers 15 to the
 * game's own thread and to the chunk-building threads. The integrated server's thread is never touched, so mob spawning
 * and crops keep using the real light.
 */
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
