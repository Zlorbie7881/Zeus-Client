package com.zlorbie788.zeus.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Gives No Jump Delay access to the pause the game keeps between two jumps. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("noJumpDelay")
    int zeus$getNoJumpDelay();

    @Accessor("noJumpDelay")
    void zeus$setNoJumpDelay(int ticks);
}
