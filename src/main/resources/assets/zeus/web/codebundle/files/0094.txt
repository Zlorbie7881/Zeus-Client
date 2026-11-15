package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Motion;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SnapTapMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void zeus$snapTap(CallbackInfo ci) {
        try {
            this.keyPresses = Motion.SnapTap.filter(this.keyPresses);
        } catch (RuntimeException ignored) {
        }
    }
}
