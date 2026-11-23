package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.server.TechniqueService.StaggerKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class GoldWarmupGameTest {
    private static final int NOTICE_RANGE = 9;
    private static final int STRUCK_TICKS = 60;
    private static final int AFTER_WARMUP = 18;
    private static final float SWING = 15;

    @GameTest
    public void gildingSurvivesOrdinaryHit(GameTestHelper test) {
        PiglinTestBodies.announce("gold_warmup_game_test_gilding_survives_ordinary_hit");
        checkHit(test, "gilding", "gilded");
    }

    @GameTest
    public void statueSurvivesOrdinaryHit(GameTestHelper test) {
        PiglinTestBodies.announce("gold_warmup_game_test_statue_survives_ordinary_hit");
        checkHit(test, "gold_cast", "gold_cast");
    }

    private static void checkHit(GameTestHelper test, String form, String status) {
        Piglin caster = GoldFormsGameTest.practitioner(test);
        String castId = start(test, caster, form);
        var attacker = PiglinTestBodies.spawn(test, EntityTypes.COW);
        var source = attacker.damageSources().mobAttack(attacker);
        long seed = MidasGameTest.seedFor(caster, source, "pelvis");
        caster.getRandom().setSeed(seed);
        caster.hurtServer(test.getLevel(), source, SWING);
        var service = TechniqueFabric.service();
        test.assertTrue(service.staggerRemainingMillis(caster.getUUID(), StaggerKind.HIT) > 0, "普通受击应产生僵直");
        assertWarmup(test, caster, castId);
        test.runAfterDelay(AFTER_WARMUP, () -> {
            test.assertTrue(GoldForms.has(caster, status), form + " 受击后应如期生效");
            test.succeed();
        });
    }

    @GameTest
    public void foreignIngotInterruptsGilding(GameTestHelper test) {
        PiglinTestBodies.announce("gold_warmup_game_test_foreign_ingot_interrupts_gilding");
        checkGold(test, "gilding", "gilded");
    }

    @GameTest
    public void foreignIngotInterruptsStatue(GameTestHelper test) {
        PiglinTestBodies.announce("gold_warmup_game_test_foreign_ingot_interrupts_statue");
        checkGold(test, "gold_cast", "gold_cast");
    }

    private static void checkGold(GameTestHelper test, String form, String status) {
        Piglin caster = GoldFormsGameTest.practitioner(test);
        String castId = start(test, caster, form);
        var level = test.getLevel();
        var origin = caster.position();
        var ingot = new ItemStack(Items.GOLD_INGOT);
        var bait = new ItemEntity(level, origin.x + NOTICE_RANGE + 1, origin.y, origin.z, ingot);
        level.addFreshEntity(bait);
        PiglinBehaviorFabric.tick(caster);
        assertWarmup(test, caster, castId);
        bait.setPos(origin.add(NOTICE_RANGE, 0, 0));
        PiglinBehaviorFabric.tick(caster);
        bait.discard();
        test.assertTrue(PiglinBehaviorFabric.goldStruckTicks(caster) == STRUCK_TICKS, "九格处的外来金锭应触发三秒见金");
        var service = TechniqueFabric.service();
        var uuid = caster.getUUID();
        test.assertTrue(service.observe(uuid).orElseThrow().cast().isEmpty(), form + " 起手应被外来金锭中断");
        test.assertTrue(service.staggerRemainingMillis(uuid, StaggerKind.HIT) == 0, "见金不应造成受击僵直");
        test.runAfterDelay(AFTER_WARMUP, () -> {
            test.assertTrue(!GoldForms.has(caster, status), "被中断的起手不应产生效果");
            test.succeed();
        });
    }

    @GameTest
    public void staleCastIdLeavesNextWarmupRunning(GameTestHelper test) {
        PiglinTestBodies.announce("gold_warmup_game_test_stale_cast_id_leaves_next_warmup_running");
        Piglin caster = GoldFormsGameTest.practitioner(test);
        var service = TechniqueFabric.service();
        var uuid = caster.getUUID();
        String first = start(test, caster, "gilding");
        test.assertTrue(service.interruptWarmup(uuid, first), "当前起手应能凭施放身份中断");
        String next = start(test, caster, "gold_cast");
        test.assertTrue(!service.interruptWarmup(uuid, first), "过期施放身份应被拒绝");
        assertWarmup(test, caster, next);
        test.runAfterDelay(AFTER_WARMUP, () -> {
            test.assertTrue(!service.interruptWarmup(uuid, next), "成像后即使身份仍匹配也已过起手窗口");
            test.assertTrue(GoldForms.isStatue(caster), "后一次铸金应完成");
            test.succeed();
        });
    }

    private static String start(GameTestHelper test, Piglin caster, String form) {
        var outcome = GoldForms.cast(caster, PiglinTestBodies.body(caster), form);
        test.assertTrue(outcome.getPhase() == CastPhase.WARMUP, form + " 起手失败 " + outcome.getReason());
        return outcome.getCastId();
    }

    private static void assertWarmup(GameTestHelper test, Piglin caster, String castId) {
        var cast = TechniqueFabric.service().observe(caster.getUUID()).orElseThrow().cast();
        test.assertTrue(cast.filter(current -> current.phase() == CastPhase.WARMUP
                && castId.equals(current.active().getRequest().getCastId())).isPresent(), "原施放应仍在起手");
    }
}
