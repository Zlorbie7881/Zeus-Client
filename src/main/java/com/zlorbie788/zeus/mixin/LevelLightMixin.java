package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Fullbright;
import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelLightMixin {
    @Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void zeus$light(CallbackInfoReturnable<Integer> cir) {
        if (Fullbright.blockLight() || XRay.fullbright()) {
            Fullbright.sawLight();
            cir.setReturnValue(0xF000F0);
        }
    }
}
