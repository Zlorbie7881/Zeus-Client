package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Motion;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Safe Walk uses the game's own edge rule, the one sneaking uses: a player who "stays on the ground surface" cannot be
 * moved off the edge of a block. Pretending to sneak for that one question (not for the pose, not for the speed) is all it
 * takes, and it is the same movement the game would compute if you were holding sneak.
 */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$edge(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && Motion.SafeWalk.wants((Object) this)) cir.setReturnValue(true);
    }
}
