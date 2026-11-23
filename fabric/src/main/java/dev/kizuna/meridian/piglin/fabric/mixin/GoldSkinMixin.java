package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.GoldForms;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 观察者使用服务端同步的金皮颜色，金像凝住原版模型的步伐和耳摆。 */
@Mixin(LivingEntityRenderer.class)
abstract class GoldSkinMixin {
    @Unique private static final RenderStateDataKey<Integer> MERIDIAN_GOLD = RenderStateDataKey.create();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"), require = 1)
    private void meridianPiglin$skin(LivingEntity entity, LivingEntityRenderState state, float partialTick,
                                     CallbackInfo ci) {
        if (state instanceof PiglinRenderState) {
            Integer tint = entity.getAttached(GoldForms.SKIN_TINT);
            state.setData(MERIDIAN_GOLD, tint);
            if (tint != null && tint == GoldForms.STATUE_TINT) {
                state.walkAnimationSpeed = 0;
                state.ageInTicks = 0;
            }
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true, require = 1)
    private void meridianPiglin$tint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> result) {
        Integer tint = state.getData(MERIDIAN_GOLD);
        if (tint != null) {
            result.setReturnValue(ARGB.multiply(result.getReturnValue(), tint));
        }
    }
}
