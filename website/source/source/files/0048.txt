package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jesus: the game already knows how to stand on a fluid (strider mobs stand on lava). It asks the entity "can you stand on
 * this fluid?" while it works out collisions. Answering yes for your own player makes the water a floor, with the game's
 * own physics, so there is no bobbing or sinking and walking, jumping and sprinting all feel normal.
 */
@Mixin(LivingEntity.class)
public abstract class LivingFluidMixin {
    @Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$stand(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Motion.Jesus.wants((Object) this, fluid)) cir.setReturnValue(true);
    }
}
