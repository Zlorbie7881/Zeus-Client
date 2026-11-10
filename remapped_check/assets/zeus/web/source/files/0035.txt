package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Esp;
import com.zlorbie788.zeus.mods.Friends;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ESP outlines: the game's own glow outline is shown through walls, so entities are marked with it. */
@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$glow(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && (Friends.outlines((Entity) (Object) this) || Esp.outlines((Entity) (Object) this))) cir.setReturnValue(true);
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$color(CallbackInfoReturnable<Integer> cir) {
        int c = Friends.outlineColor((Entity) (Object) this);
        if (c == 0) c = Esp.outlineColor((Entity) (Object) this);
        if (c != 0) cir.setReturnValue(c & 0xFFFFFF);
    }
}
