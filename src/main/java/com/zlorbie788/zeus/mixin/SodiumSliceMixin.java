package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
    "net.caffeinemc.mods.sodium.client.world.LevelSlice",
    "me.jellysquid.mods.sodium.client.world.WorldSlice",
    "org.embeddedt.embeddium.impl.world.WorldSlice"
}, remap = false)
public abstract class SodiumSliceMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void zeus$xrayBlockXyz(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (!XRay.active()) return;
        XRay.saw(XRay.HOOK_SODIUM);
        BlockState swap = XRay.swap(cir.getReturnValue(), x, y, z);
        if (swap != null) cir.setReturnValue(swap);
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void zeus$xrayBlockPos(net.minecraft.core.BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!XRay.active()) return;
        XRay.saw(XRay.HOOK_SODIUM);
        BlockState swap = XRay.swap(cir.getReturnValue(), pos.getX(), pos.getY(), pos.getZ());
        if (swap != null) cir.setReturnValue(swap);
    }
}
