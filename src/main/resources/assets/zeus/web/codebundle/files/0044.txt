package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$shape(CallbackInfoReturnable<RenderShape> cir) {
        if (!XRay.active() || !XRay.onMeshThread()) return;
        BlockState self = (BlockState) (Object) this;
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

    @Inject(method = "isSolidRender()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$solid(CallbackInfoReturnable<Boolean> cir) {
        if (XRay.active() && XRay.holeHooks() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$shade(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XRay.active() && XRay.stateHooks() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(1.0f);
    }
}
