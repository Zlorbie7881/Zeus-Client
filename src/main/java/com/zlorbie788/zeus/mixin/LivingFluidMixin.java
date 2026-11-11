package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Motion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingFluidMixin {
    @Inject(method = "canStandOnFluid", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$stand(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Motion.Jesus.wants((Object) this, fluid)) cir.setReturnValue(true);
    }
}
