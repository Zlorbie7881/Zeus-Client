package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.Morph;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @ModifyVariable(method = "extractEntity", at = @At("HEAD"), argsOnly = true, require = 0)
    private Entity zeus$morph(Entity entity) {
        return Morph.swap(entity);
    }
}
