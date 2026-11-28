package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.humanoid.fabric.HumanoidBrain;
import dev.kizuna.meridian.piglin.fabric.PiglinBehaviorFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Piglin.class)
abstract class PiglinMixin {
    @Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true, require = 1)
    private void meridian$checkCrossbowShot(LivingEntity target, float power, CallbackInfo callback) {
        // 已上弦的原版弩可以跳过拉弦阶段，发射时仍须遵守当前施放的限制。
        if (HumanoidBrain.blocksRanged((Piglin) (Object) this)) {
            callback.cancel();
        }
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void meridian$tick(ServerLevel level, CallbackInfo callback) {
        PiglinBehaviorFabric.tickScheduled((Piglin) (Object) this);
    }
}
