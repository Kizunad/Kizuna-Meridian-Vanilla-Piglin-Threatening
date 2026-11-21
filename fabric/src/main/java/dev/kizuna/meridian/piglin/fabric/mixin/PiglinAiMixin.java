package dev.kizuna.meridian.piglin.fabric.mixin;

import dev.kizuna.meridian.piglin.fabric.PiglinBehaviorFabric;
import dev.kizuna.meridian.piglin.fabric.PiglinGoldFabric;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.world.entity.monster.piglin.PiglinAi.class)
abstract class PiglinAiMixin {
    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private static void meridian$beforePickup(ServerLevel level, Piglin piglin, ItemEntity item,
                                              CallbackInfo callback) {
        if (!PiglinBehaviorFabric.swallowPickedUpGold(piglin, item.getItem())) {
            return;
        }
        if (item.getItem().isEmpty()) {
            item.discard();
            callback.cancel();
        }
    }

    @Inject(method = "pickUpItem", at = @At("TAIL"))
    private static void meridian$afterPickup(ServerLevel level, Piglin piglin, ItemEntity item,
                                              CallbackInfo callback) {
        PiglinBehaviorFabric.swallowGold(piglin);
        PiglinBehaviorFabric.tickForTest(piglin);
    }

    @Inject(method = "stopHoldingOffHandItem", at = @At("HEAD"))
    private static void meridian$beforeBarter(ServerLevel level, Piglin piglin, boolean barteringEnabled,
                                               CallbackInfo callback) {
        if (!barteringEnabled || !piglin.isAdult()
                || !piglin.getItemInHand(InteractionHand.OFF_HAND).is(Items.GOLD_INGOT)) {
            return;
        }
        PiglinGoldFabric.addGold(piglin, PiglinGoldFabric.gold(piglin));
    }
}
