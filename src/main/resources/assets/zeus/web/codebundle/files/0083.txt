package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$xrayBlock(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!XRay.active()) return;
        XRay.saw();
        BlockState swap = XRay.swap(cir.getReturnValue(), pos.getX(), pos.getY(), pos.getZ());
        if (swap != null) cir.setReturnValue(swap);
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true, require = 0)
    private void zeus$xrayFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (!XRay.active()) return;
        FluidState fluid = cir.getReturnValue();
        if (fluid != null && !fluid.isEmpty() && XRay.swap(fluid.createLegacyBlock(), pos.getX(), pos.getY(), pos.getZ()) != null) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }
}
