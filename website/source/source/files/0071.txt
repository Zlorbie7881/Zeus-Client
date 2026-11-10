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

/**
 * While the ordinary blocks are drawn faintly they must not hide the faces of the ores they touch: an ore inside the rock has
 * stone on every side, and the game would not draw any of its faces. For the chunk builders the faint blocks have no face that
 * blocks the view, so the ore's faces are drawn. Kept apart from the other hooks so that if this method looks different in
 * some version, only this one is lost.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XRayOcclusionMixin {
    @Inject(method = "getFaceOcclusionShape", at = @At("HEAD"), cancellable = true, require = 0)
    private void zeus$face(Direction dir, CallbackInfoReturnable<VoxelShape> cir) {
        if (XRay.transparent() && XRay.onMeshThread() && XRay.hide((BlockState) (Object) this)) cir.setReturnValue(Shapes.empty());
    }
}
