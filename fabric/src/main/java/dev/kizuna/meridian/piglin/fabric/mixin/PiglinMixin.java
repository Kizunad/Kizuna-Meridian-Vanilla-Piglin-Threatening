package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.PiglinBehaviorFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Piglin.class)
abstract class PiglinMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void meridian$tick(ServerLevel level, CallbackInfo callback) {
        PiglinBehaviorFabric.tick((Piglin) (Object) this);
    }
}
