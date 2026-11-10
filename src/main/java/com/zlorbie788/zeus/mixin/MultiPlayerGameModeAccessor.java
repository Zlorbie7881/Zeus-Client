package com.zlorbie788.zeus.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives Fast Break access to the pause between two broken blocks. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyDelay")
    int zeus$getDestroyDelay();

    @Accessor("destroyDelay")
    void zeus$setDestroyDelay(int ticks);
}
