package com.zlorbie788.zeus.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Weather Changer set the rain and thunder strength that your own screen draws. */
@Mixin(Level.class)
public interface LevelWeatherAccessor {
    @Accessor("rainLevel")
    void zeus$setRain(float v);

    @Accessor("thunderLevel")
    void zeus$setThunder(float v);
}
