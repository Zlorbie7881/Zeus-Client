package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XRayOcclusionMixin {
    @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$face(Direction dir, CallbackInfoReturnable<VoxelShape> cir) {
        if (XRay.transparent() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(Shapes.empty());
    }
}
