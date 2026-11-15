package com.zlorbie788.zeus.mixin;

import com.zlorbie788.zeus.mods.AutomationPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityMoveMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, require = 1)
    private Vec3 zeus$boatNoclipDrive(Vec3 movement, MoverType type) {
        AutomationPlus.BoatNoClip noclip = AutomationPlus.BoatNoClip.getActive();
        if (noclip == null) return movement;
        if (type != MoverType.SELF) return movement;
        return noclip.drive((Entity) (Object) this, movement);
    }
}
