package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Motion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$edge(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Motion.SafeWalk.wants((Object) this)) cir.setReturnValue(true);
    }
}
