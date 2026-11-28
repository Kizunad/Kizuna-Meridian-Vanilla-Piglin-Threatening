package dev.kizuna.meridian.piglin.fabric;

import static dev.kizuna.meridian.piglin.fabric.PiglinTestBodies.spawn;

import dev.kizuna.meridian.piglin.server.PiglinGold;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class PiglinBehaviorGameTest {
    private static final int INVENTORY_SIZE = 8;
    private static final int STACK_A_COUNT = 5;
    private static final int STACK_B_COUNT = 5;
    private static final int GOLD_NOTICE_TICKS = 60;
    private static final int GOLD_SATED_TICKS = 400;
    private static final int BLOCK_UPDATE_FLAGS = 3;
    private static final String PICK_UP_ITEM = "pickUpItem";
    private static final String STOP_HOLDING_OFF_HAND_ITEM = "stopHoldingOffHandItem";

    @GameTest
    public void inventoryGoldStacksAreConsumedSeparately(GameTestHelper test) {
        announce("piglin_behavior_game_test_inventory_gold_stacks_are_consumed_separately");
        Piglin piglin = piglin(test);
        piglin.getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET, STACK_A_COUNT));
        piglin.getInventory().setItem(1, new ItemStack(Items.GOLD_NUGGET, STACK_B_COUNT));

        test.assertTrue(PiglinBehaviorFabric.swallowGold(piglin), "库存金粒没有进入金囊");
        test.assertTrue(PiglinGoldFabric.gold(piglin) == PiglinGold.MORTAL_CAPACITY,
                "金量没有按容量增加");
        test.assertTrue(piglin.getInventory().getItem(0).isEmpty(), "第一堆金粒未按来源扣减");
        test.assertTrue(piglin.getInventory().getItem(1).getCount() == 1,
                "第二堆金粒扣减数量错误");
        test.succeed();
    }

    @GameTest
    public void vanillaPickupFeedsGoldNuggets(GameTestHelper test) {
        announce("piglin_behavior_game_test_vanilla_pickup_feeds_gold_nuggets");
        Piglin piglin = piglin(test);
        ItemEntity item = new ItemEntity(test.getLevel(), piglin.getX(), piglin.getY(), piglin.getZ(),
                new ItemStack(Items.GOLD_NUGGET, 2));
        test.getLevel().addFreshEntity(item);
        invokePickUpItem(test.getLevel(), piglin, item);

        test.assertTrue(PiglinGoldFabric.gold(piglin) == 2, "原版金粒拾取未进入金囊");
        test.assertTrue(item.isRemoved(), "已服下的金粒掉落物仍存在");
        test.succeed();
    }

    @GameTest
    public void barterKeepsVanillaLootAndAddsGold(GameTestHelper test) {
        announce("piglin_behavior_game_test_barter_keeps_vanilla_loot_and_adds_gold");
        Piglin piglin = piglin(test);
        var inventory = piglin.getInventory();
        test.assertTrue(inventory.getContainerSize() == INVENTORY_SIZE, "原版猪灵应保留八格库存");
        inventory.setItem(INVENTORY_SIZE - 1, new ItemStack(Items.STICK));
        piglin.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
        invokeBarterStop(test.getLevel(), piglin);

        long responseCount = test.getLevel().getEntitiesOfClass(ItemEntity.class,
                        piglin.getBoundingBox().inflate(6))
                .stream()
                .mapToLong(item -> item.getItem().getCount())
                .sum();
        test.assertTrue(PiglinGoldFabric.gold(piglin) == PiglinGold.INGOT_VALUE,
                "易货金锭未进入金囊");
        test.assertTrue(responseCount > 0, "原版易货掉落没有保留");
        test.assertTrue(inventory.getItem(INVENTORY_SIZE - 1).is(Items.STICK), "易货改写了最后一格库存");
        test.succeed();
    }

    @GameTest
    public void soulFireBlocksPiglinSwallowing(GameTestHelper test) {
        announce("piglin_behavior_game_test_soul_fire_blocks_piglin_swallowing");
        Piglin piglin = piglin(test);
        BlockPos firePos = piglin.blockPosition().east();
        test.getLevel().setBlock(firePos.below(), Blocks.SOUL_SOIL.defaultBlockState(), BLOCK_UPDATE_FLAGS);
        test.getLevel().setBlock(firePos, Blocks.SOUL_FIRE.defaultBlockState(), BLOCK_UPDATE_FLAGS);
        piglin.getBrain().setMemory(MemoryModuleType.NEAREST_REPELLENT, firePos);
        piglin.getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET));

        test.assertTrue(!PiglinBehaviorFabric.swallowGold(piglin), "魂火旁错误服金");
        test.assertTrue(PiglinGoldFabric.gold(piglin) == 0, "魂火旁金量错误增加");
        test.succeed();
    }

    @GameTest
    public void foreignGoldInterruptsAndCooldownsSwallowing(GameTestHelper test) {
        announce("piglin_behavior_game_test_foreign_gold_interrupts_and_cooldowns_swallowing");
        Piglin piglin = piglin(test);
        piglin.getInventory().setItem(0, new ItemStack(Items.GOLD_NUGGET));
        ItemEntity foreignGold = new ItemEntity(test.getLevel(), piglin.getX(), piglin.getY(), piglin.getZ(),
                new ItemStack(Items.GOLDEN_SWORD));
        test.getLevel().addFreshEntity(foreignGold);

        PiglinBehaviorFabric.tickForTest(piglin);
        test.assertTrue(PiglinBehaviorFabric.isGoldStruck(piglin), "见金没有中断服金");
        test.assertTrue(PiglinGoldFabric.gold(piglin) == 0, "见金期间错误服金");
        for (int tick = 0; tick < GOLD_NOTICE_TICKS; tick++) {
            PiglinBehaviorFabric.tickForTest(piglin);
        }
        test.assertTrue(PiglinBehaviorFabric.goldStruckTicks(piglin) == 0, "见金时长错误");
        test.assertTrue(PiglinBehaviorFabric.goldSatedTicks(piglin) == GOLD_SATED_TICKS,
                "见金结束后没有进入冷却");
        PiglinBehaviorFabric.tickForTest(piglin);
        test.assertTrue(PiglinGoldFabric.gold(piglin) == 0, "冷却期间错误服金");
        foreignGold.discard();

        Piglin ownDropPiglin = piglin(test);
        ItemEntity ownGold = new ItemEntity(test.getLevel(), ownDropPiglin.getX(), ownDropPiglin.getY(),
                ownDropPiglin.getZ(), new ItemStack(Items.GOLDEN_SWORD));
        ownGold.setThrower(ownDropPiglin);
        test.getLevel().addFreshEntity(ownGold);
        PiglinBehaviorFabric.tickForTest(ownDropPiglin);
        test.assertTrue(!PiglinBehaviorFabric.isGoldStruck(ownDropPiglin), "猪灵自己丢的金触发见金");
        test.succeed();
    }

    @GameTest
    public void safeArmorUsesVanillaPiglinRule(GameTestHelper test) {
        announce("piglin_behavior_game_test_safe_armor_uses_vanilla_piglin_rule");
        Piglin piglin = piglin(test);
        test.assertTrue(!PiglinBehaviorFabric.isWearingSafeArmor(piglin), "未穿金甲错误视为安全");
        piglin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        test.assertTrue(PiglinBehaviorFabric.isWearingSafeArmor(piglin), "金甲未按原版规则识别");
        test.succeed();
    }

    private static Piglin piglin(GameTestHelper test) {
        return (Piglin) spawn(test, EntityTypes.PIGLIN);
    }

    private static void announce(String testName) {
        System.out.println("[PiglinBehaviorGameTest] " + testName);
    }

    private static void invokePickUpItem(ServerLevel level, Piglin piglin, ItemEntity item) {
        try {
            Method method = PiglinAi.class.getDeclaredMethod(PICK_UP_ITEM, ServerLevel.class, Piglin.class,
                    ItemEntity.class);
            method.trySetAccessible();
            method.invoke(null, level, piglin, item);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("无法调用原版猪灵拾取入口", failure);
        }
    }

    private static void invokeBarterStop(ServerLevel level, Piglin piglin) {
        try {
            Method method = PiglinAi.class.getDeclaredMethod(STOP_HOLDING_OFF_HAND_ITEM, ServerLevel.class,
                    Piglin.class, boolean.class);
            method.trySetAccessible();
            method.invoke(null, level, piglin, true /* barteringEnabled */);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("无法调用原版猪灵易货入口", failure);
        }
    }
}
