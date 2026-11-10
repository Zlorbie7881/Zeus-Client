package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Friends;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game asks every living thing's renderer whether its name tag is shown. For a friend whose name tag Zeus draws itself
 * (see {@link Friends}) the answer is "no", so there are not two tags. Only the picture on your own screen changes.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FriendTagMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$friendTag(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && Friends.hideNameTag(entity)) cir.setReturnValue(false);
    }
}
