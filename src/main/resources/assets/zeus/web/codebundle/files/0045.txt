package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.AutomationPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBoat.class)
public abstract class BoatNoclipCollisionMixin {
    @Inject(method = "canBeCollidedWith", at = @At("RETURN"), cancellable = true, require = 1)
    private void zeus$noCollide(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (AutomationPlus.BoatNoClip.getActive() == null) return;
        if (AutomationPlus.BoatNoClip.owns((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
