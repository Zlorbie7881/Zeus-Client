package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Fullbright;
import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The light every block face is drawn with. Vanilla's chunk builder and Sodium both ask this one function, so answering
 * "full light" here makes the world fully bright in any renderer, and makes ores seen through the stone bright instead
 * of black (the stone around them is gone, and the light inside rock is zero).
 *
 * <p>The game has several functions of this name, and the build tool can only turn a method name into the game's real
 * (obfuscated) name when it is told exactly which one, so the full signature is written out. A bare name matched nothing
 * once the mod was running outside the development environment, which is why the light never changed before.</p>
 */
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
