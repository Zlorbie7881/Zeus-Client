package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Gear;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitboxes: how far outside its body an entity can still be aimed at. */
@Mixin(Entity.class)
public abstract class EntityPickMixin {
    @Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$pick(CallbackInfoReturnable<Float> cir) {
        float extra = Gear.Hitboxes.extra((Entity) (Object) this);
        if (extra > 0f) cir.setReturnValue(cir.getReturnValueF() + extra);
    }
}
