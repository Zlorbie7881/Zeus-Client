package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.XRay;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * X-Ray opacity, step one: while the other blocks are drawn see-through, the chunk builders put them in the game's
 * translucent layer (the one glass and water use), which is the only layer that blends with what is behind it.
 * Both the game's own block renderer and Fabric's default one (Indigo) ask this method for the layer of every block.
 *
 * <p>The layer is named directly here. An earlier version looked it up by its development name at run time, which finds
 * nothing in a real game (the names are different there), so the hook never did anything.</p>
 */
@Mixin(ItemBlockRenderTypes.class)
public abstract class XRayLayerMixin {
    @Inject(method = "getChunkRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private static void zeus$layer(BlockState state, CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (!XRay.transparent() || !XRay.onMeshThread() || !XRay.hide(state)) return;
        XRay.saw(XRay.HOOK_LAYER);
        cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
    }
}
