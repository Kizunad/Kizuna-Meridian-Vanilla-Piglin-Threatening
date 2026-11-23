package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.GoldForms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractPiglin.class)
abstract class GoldFormsMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"), require = 1)
    private void meridianPiglin$useForm(ServerLevel level, CallbackInfo ci) {
        GoldForms.tick((AbstractPiglin) (Object) this, level);
    }
}
