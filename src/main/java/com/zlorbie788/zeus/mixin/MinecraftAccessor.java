package com.zlorbie788.zeus.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("rightClickDelay")
    int zeus$getRightClickDelay();

    @Accessor("rightClickDelay")
    void zeus$setRightClickDelay(int ticks);
}
