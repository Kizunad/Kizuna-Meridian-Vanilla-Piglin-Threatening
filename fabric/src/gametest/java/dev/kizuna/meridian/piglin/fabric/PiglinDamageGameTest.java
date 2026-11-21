package dev.kizuna.meridian.piglin.fabric;

import static dev.kizuna.meridian.piglin.fabric.PiglinTestBodies.*;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;

public final class PiglinDamageGameTest {
    private static final double DESTROY = 100;
    private static final List<String> LIMBS = List.of("left_hand", "right_hand", "left_foot", "right_foot");

    @GameTest
    public void trunkAndLimbsReceiveTheirOwnDamage(GameTestHelper test) {
        for (String partId : List.of("abdomen", "left_hand", "right_foot")) {
            var piglin = spawn(test, EntityTypes.PIGLIN);
            var before = body(piglin);
            var after = damage(piglin, partId, 3);
            double beforeHp = before.getPartStatesList().stream()
                    .filter(state -> state.getId().equals(partId)).findFirst().orElseThrow().getHp();
            double afterHp = after.getPartStatesList().stream()
                    .filter(state -> state.getId().equals(partId)).findFirst().orElseThrow().getHp();
            test.assertTrue(afterHp < beforeHp, "目标部位没有受伤 " + partId);
            test.assertTrue(after.getPartStatesList().stream()
                    .filter(state -> !state.getId().equals(partId))
                    .allMatch(state -> before.getPartStatesList().stream()
                            .filter(previous -> previous.getId().equals(state.getId()))
                            .findFirst().orElseThrow().getHp() == state.getHp()),
                    "伤害扩散到了非目标部位 " + partId);
        }
        test.succeed();
    }

    @GameTest
    public void vitalPartsKillAndRemoveEntity(GameTestHelper test) {
        for (String partId : List.of("head", "neck", "chest")) {
            var piglin = spawn(test, EntityTypes.PIGLIN);
            var result = vanillaDamage(piglin, partId, DESTROY);
            test.assertTrue(result.getVitality().getDead(), partId + " 归零未致死");
            test.assertTrue(!piglin.isAlive(), partId + " 致死后实体未移除");
        }
        test.succeed();
    }

    @GameTest
    public void lowerPartsRemainAliveWhenNotBelowThreshold(GameTestHelper test) {
        for (String partId : LIMBS) {
            var piglin = spawn(test, EntityTypes.PIGLIN_BRUTE);
            var result = vanillaDamage(piglin, partId, DESTROY);
            test.assertTrue(!result.getVitality().getDead(), partId + " 单独损坏错误致死");
            test.assertTrue(piglin.isAlive(), partId + " 单独损坏后实体已移除");
        }
        test.succeed();
    }
}
