package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemBlockRenderTypes.class)
public abstract class XRayLayerMixin {
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zeus$layer(BlockState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (!XRay.transparent() || !XRay.onMeshThread() || !XRay.hide(state)) return;
        XRay.saw(XRay.HOOK_LAYER);
        cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
    }
}
