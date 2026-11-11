package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XRayShapeMixin {
    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$skip(BlockState other, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (!XRay.transparent() || XRay.innerFaces() || !XRay.onMeshThread()) return;
        if (XRay.hide((BlockState) (Object) this) && XRay.hide(other)) {
            XRay.saw(XRay.HOOK_FACE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), require = 0)
    private void zeus$fluid(CallbackInfoReturnable<FluidState> cir) {
        if (XRay.transparent() && XRay.onMeshThread()) XRay.aboutToDraw((BlockState) (Object) this);
    }
}
