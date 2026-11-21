package dev.kizuna.meridian.piglin.fabric;

import static dev.kizuna.meridian.piglin.fabric.PiglinTestBodies.*;

import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.humanoid.server.SpeciesBodyRules.Spawn;
import java.util.Optional;
import java.util.random.RandomGenerator;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class PiglinGoldGameTest {
    private static final long GOLD_BALANCE = 9;
    private static final double DESTROY = 100;
    private static final double ITEM_SEARCH_RADIUS = 6;
    private static final double TRUE_QI_ROLL = 0.05;
    private static final double WANDERING_QI_ROLL = 0.10;
    private static final double MORTAL_ROLL = 0.40;
    private static final String ABDOMEN = "abdomen";
    private static final String HEAD = "head";

    @GameTest
    public void commandSpawnStartsWhiteAndCannotSpendGold(GameTestHelper test) {
        LivingEntity piglin = spawn(test, EntityTypes.PIGLIN);
        test.assertTrue(PiglinGoldFabric.gold(piglin) == 0, "命令生成猪灵初始金量不为零");
        test.assertTrue(PiglinGoldFabric.spend(piglin, 1).isEmpty(), "无金量时消耗请求成功");
        test.succeed();
    }

    @GameTest
    public void goldPersistsAcrossEntityReload(GameTestHelper test) {
        LivingEntity piglin = spawn(test, EntityTypes.PIGLIN);
        PiglinGoldFabric.setGold(piglin, GOLD_BALANCE).orElseThrow();
        var saved = save(piglin);
        piglin.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        LivingEntity restored = EntityTypes.PIGLIN.create(test.getLevel(), EntitySpawnReason.LOAD);
        load(restored, saved);
        add(test, restored);
        test.assertTrue(PiglinGoldFabric.gold(restored) == GOLD_BALANCE, "读档后金量丢失");
        test.succeed();
    }

    @GameTest
    public void brokenPouchDropsGoldAndClearsBalance(GameTestHelper test) {
        LivingEntity piglin = spawn(test, EntityTypes.PIGLIN);
        PiglinGoldFabric.setGold(piglin, GOLD_BALANCE).orElseThrow();
        vanillaDamage(piglin, ABDOMEN, DESTROY);
        test.assertTrue(PiglinGoldFabric.gold(piglin) == 0, "腹部损坏后金囊未清空");
        test.assertTrue(droppedGold(test, piglin) == GOLD_BALANCE, "腹部损坏掉落金量不守恒");
        test.succeed();
    }

    @GameTest
    public void deathDropsGoldOnce(GameTestHelper test) {
        LivingEntity piglin = spawn(test, EntityTypes.PIGLIN);
        PiglinGoldFabric.setGold(piglin, GOLD_BALANCE).orElseThrow();
        var death = vanillaDamage(piglin, HEAD, DESTROY);
        test.assertTrue(death.getVitality().getDead(), "致死伤害未标记身体死亡");
        test.assertTrue(droppedGold(test, piglin) == GOLD_BALANCE, "死亡掉落金量不守恒");
        ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(piglin, piglin.damageSources().generic());
        test.assertTrue(droppedGold(test, piglin) == GOLD_BALANCE, "重复死亡事件重复掉金");
        test.succeed();
    }

    @GameTest
    public void spawnSourceSelectsEligibleTiers(GameTestHelper test) {
        Spawn naturalPiglin = new Spawn("minecraft:piglin", EntitySpawnReason.NATURAL.name(), 1,
                Optional.empty());
        Spawn fortressBrute = new Spawn("minecraft:piglin_brute", EntitySpawnReason.STRUCTURE.name(), 1,
                Optional.empty());
        Spawn commandPiglin = new Spawn("minecraft:piglin", EntitySpawnReason.COMMAND.name(), 1,
                Optional.empty());
        Spawn naturalBrute = new Spawn("minecraft:piglin_brute", EntitySpawnReason.NATURAL.name(), 1,
                Optional.empty());
        test.assertTrue(PiglinFabric.spawnTier(naturalPiglin, fixedRoll(TRUE_QI_ROLL)) == HumanoidTier.TRUE_QI,
                "自然猪灵未抽中金骨档");
        test.assertTrue(PiglinFabric.spawnTier(fortressBrute, fixedRoll(TRUE_QI_ROLL)) == HumanoidTier.TRUE_QI,
                "堡垒蛮兵未抽中金骨档");
        test.assertTrue(PiglinFabric.spawnTier(naturalPiglin, fixedRoll(WANDERING_QI_ROLL))
                == HumanoidTier.WANDERING_QI, "修士概率边界不符");
        test.assertTrue(PiglinFabric.spawnTier(naturalPiglin, fixedRoll(MORTAL_ROLL)) == HumanoidTier.MORTAL,
                "白身概率边界不符");
        test.assertTrue(PiglinFabric.spawnTier(commandPiglin, fixedRoll(TRUE_QI_ROLL)) == HumanoidTier.MORTAL,
                "命令生成猪灵错误掷道行");
        test.assertTrue(PiglinFabric.spawnTier(naturalBrute, fixedRoll(TRUE_QI_ROLL)) == HumanoidTier.MORTAL,
                "自然生成蛮兵错误掷道行");
        test.succeed();
    }

    private static RandomGenerator fixedRoll(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    private static long droppedGold(GameTestHelper test, LivingEntity entity) {
        AABB searchArea = entity.getBoundingBox().inflate(ITEM_SEARCH_RADIUS);
        return test.getLevel().getEntitiesOfClass(ItemEntity.class, searchArea,
                        item -> item.getItem().is(Items.GOLD_NUGGET))
                .stream()
                .mapToLong(item -> item.getItem().getCount())
                .sum();
    }
}
