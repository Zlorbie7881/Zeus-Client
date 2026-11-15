package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class XRayFaceMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zeus$face(BlockState state, BlockState neighbour, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (!XRay.transparent() || !XRay.onMeshThread()) return;
        boolean me = XRay.hide(state);
        boolean other = XRay.hide(neighbour);
        if (me) {
            XRay.saw(XRay.HOOK_FACE);
            if (other && !XRay.innerFaces()) cir.setReturnValue(false);
            else if (!other) cir.setReturnValue(true);
        } else if (other) {
            cir.setReturnValue(true);
        }
    }
}
