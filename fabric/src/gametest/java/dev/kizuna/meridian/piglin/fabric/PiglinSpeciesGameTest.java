package dev.kizuna.meridian.piglin.fabric;

import static dev.kizuna.meridian.piglin.fabric.PiglinTestBodies.*;

import dev.kizuna.meridian.piglin.server.PiglinContent;
import dev.kizuna.meridian.protocol.pb.BodyMessages.MeridianOpeningStage;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;

public final class PiglinSpeciesGameTest {
    private static final List<EntityType<? extends LivingEntity>> ADULT_TYPES = List.of(
            EntityTypes.PIGLIN,
            EntityTypes.PIGLIN_BRUTE);
    private static final double WOUND = 5;

    @GameTest
    public void adultsHavePiglinBodies(GameTestHelper test) {
        for (var type : ADULT_TYPES) {
            var entity = spawn(test, type);
            var body = body(entity);
            test.assertTrue(body.getTemplate().getId().equals(PiglinContent.TEMPLATE_ID),
                    "实体未使用猪灵模板 " + type);
            test.assertTrue(body.getId().endsWith(entity.getStringUUID()), "身体身份未绑定实体");
            test.assertTrue(body.getOrganStatesList().stream().anyMatch(state -> state.getId().equals(
                    PiglinContent.GOLD_POUCH)), "出生快照缺少金囊");
            var pouch = body.getStructure().getOrgansList().stream()
                    .filter(organ -> organ.getId().equals(PiglinContent.GOLD_POUCH))
                    .findFirst().orElseThrow();
            test.assertTrue(pouch.getDisplayName().equals("金囊（暂存）"), "金囊缺少中文名称");
            test.assertTrue(pouch.getPartId().equals("abdomen"), "金囊没有挂在腹部");
            test.assertTrue(body.getMeridianStatesList().stream().allMatch(state ->
                    state.getOpening().getStage() == MeridianOpeningStage.UNOPENED), "出生错误开脉");
        }
        test.succeed();
    }

    @GameTest
    public void babiesStayVanilla(GameTestHelper test) {
        var baby = EntityTypes.PIGLIN.create(test.getLevel(), EntitySpawnReason.COMMAND);
        baby.setBaby(true /* baby */);
        add(test, baby);
        test.assertTrue(VanillaDamageFabric.inspectBody(baby).isEmpty(), "幼年猪灵被认领");
        test.assertTrue(!save(baby).contains(SAVE_KEY), "幼年猪灵写入身体存档");
        baby.setHealth(1);
        test.assertTrue(baby.getHealth() == 1, "幼年猪灵原版血量被覆盖");
        test.succeed();
    }

    @GameTest
    public void woundedBodySurvivesEntityReload(GameTestHelper test) {
        var piglin = spawn(test, EntityTypes.PIGLIN);
        var wounded = damage(piglin, "left_hand", WOUND);
        var saved = save(piglin);
        piglin.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored = EntityTypes.PIGLIN.create(test.getLevel(), EntitySpawnReason.LOAD);
        load(restored, saved);
        add(test, restored);
        test.assertTrue(body(restored).equals(wounded), "带伤读档后身体快照不符");
        test.succeed();
    }

    @GameTest
    public void bodyStatusCommandResolvesBothSpecies(GameTestHelper test) {
        var source = test.getLevel().getServer().createCommandSourceStack().withLevel(test.getLevel());
        for (var type : ADULT_TYPES) {
            var entity = spawn(test, type);
            body(entity);
            int[] result = {0};
            test.getLevel().getServer().getCommands().performPrefixedCommand(
                    source.withCallback((success, value) -> result[0] = value),
                    "meridian body status " + entity.getStringUUID());
            test.assertTrue(result[0] == 1, "身体状态命令未解析 " + type);
        }
        test.succeed();
    }

    @GameTest
    public void vanillaDamageLeavesBodyWound(GameTestHelper test) {
        var piglin = spawn(test, EntityTypes.PIGLIN);
        var before = body(piglin);
        piglin.hurtServer(test.getLevel(), piglin.damageSources().generic(), 2);
        var after = body(piglin);
        test.assertTrue(after.getRevision() > before.getRevision(), "原版伤害没有提交身体事务");
        test.assertTrue(after.getPartStatesList().stream().anyMatch(state -> state.getHp() < before.getPartStatesList()
                .stream().filter(previous -> previous.getId().equals(state.getId())).findFirst().orElseThrow().getHp()),
                "原版伤害没有留下部位伤势");
        test.succeed();
    }
}
