package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium (and Embeddium) build chunk meshes from their own view of the world instead of the game's, so the X-Ray hook on the
 * game's view never runs when one of them is installed. This is the same hook on their view. The class is only touched when
 * the mod is there ({@code @Pseudo}), and every name is looked up by name only, so a different version simply does nothing
 * and the module falls back to Block ESP by itself.
 */
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
