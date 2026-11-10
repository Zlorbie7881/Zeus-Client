package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The third way X-Ray reaches the screen, and the one that does not care which chunk renderer is installed. While it is on,
 * the threads that build chunk meshes see the blocks you did not pick as invisible and see-through (they draw nothing and do
 * not hide the faces of the blocks next to them). Only those helper threads are affected: the game's own thread and the
 * world's thread never see a change, so walking, swimming and collisions stay normal.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$shape(CallbackInfoReturnable<RenderShape> cir) {
        if (!XRay.active() || !XRay.onMeshThread()) return;
        BlockState self = (BlockState) (Object) this;
        // the mesh builders ask for the shape right before they write the block: remember how see-through it is to be
        XRay.aboutToDraw(self);
        if (XRay.stateHooks() && XRay.hide(self)) {
            XRay.saw(XRay.HOOK_STATE);
            if (!XRay.invisibleShape()) return;
            if (XRay.scatterByState() && XRay.keepScattered()) return;
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }

    @Inject(method = "canOcclude", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$occlude(CallbackInfoReturnable<Boolean> cir) {
        if (XRay.active() && XRay.holeHooks() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(false);
    }

    /**
     * Sodium and the game skip the light of a block that is "solid" (it is never seen from outside), so the stone around an
     * ore reads as dark. While X-Ray hides it, it is also not solid for the mesh builders, which makes them ask for its
     * light (answered as full light) and show the ore's faces next to it.
     */
    @Inject(method = "isSolidRender()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$solid(CallbackInfoReturnable<Boolean> cir) {
        if (XRay.active() && XRay.holeHooks() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(false);
    }

    /** The darkening that comes from neighbouring blocks (ambient occlusion): hidden stone must not darken the ore beside it. */
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$shade(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XRay.active() && XRay.stateHooks() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(1.0f);
    }
}
