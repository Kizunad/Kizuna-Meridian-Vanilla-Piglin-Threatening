package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.humanoid.fabric.HumanoidHealthHost;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageBody;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageKind;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.RequestEnvelope;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

final class PiglinTestBodies {
    static final int SPAWN = 2;
    static final String SAVE_KEY = "meridian_humanoid";

    private PiglinTestBodies() {
    }

    static LivingEntity spawn(GameTestHelper test, EntityType<? extends LivingEntity> type) {
        var entity = type.create(test.getLevel(), EntitySpawnReason.COMMAND);
        return add(test, entity);
    }

    static LivingEntity add(GameTestHelper test, LivingEntity entity) {
        entity.setPos(Vec3.atCenterOf(test.absolutePos(new BlockPos(SPAWN, SPAWN, SPAWN))));
        test.getLevel().addFreshEntity(entity);
        return entity;
    }

    static BodySnapshot body(LivingEntity entity) {
        return VanillaDamageFabric.inspectBody(entity).orElseThrow();
    }

    static CompoundTag save(LivingEntity entity) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    static void load(LivingEntity entity, CompoundTag saved) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), saved));
    }

    static BodySnapshot damage(LivingEntity entity, String partId, double amount) {
        return damage(entity, HealthTarget.newBuilder().setPartId(partId).build(), amount);
    }

    static BodySnapshot damage(LivingEntity entity, HealthTarget target, double amount) {
        var prior = body(entity);
        var damage = DamageBody.newBuilder()
                .setSourceId("meridian_piglin:test")
                .setDamageType("meridian_piglin:test")
                .setKind(DamageKind.CUSTOM)
                .setHit(target)
                .setDamageHp(amount)
                .build();
        var command = BodyCommand.newBuilder()
                .setBodyId(prior.getId())
                .setExpectedRevision(prior.getRevision())
                .setDamageBody(damage)
                .build();
        String requestId = UUID.randomUUID().toString();
        var envelope = RequestEnvelope.newBuilder()
                .setProtocolVersion(1)
                .setRequestId(requestId)
                .setBodyCommand(command)
                .build();
        var request = HealthHostRequest.newBuilder()
                .setProtocolVersion(1)
                .setRequestId(requestId)
                .setSubjectId(entity.getStringUUID())
                .setEffect(envelope)
                .build();
        var response = HealthHostProtobuf.decodeResponse(new HumanoidHealthHost().exchange(
                HealthHostProtobuf.encodeRequest(request)));
        if (!response.hasBody()) {
            throw new AssertionError("宿主拒绝伤量 " + target + response.getFailure());
        }
        return response.getBody();
    }

    static BodySnapshot vanillaDamage(LivingEntity entity, String partId, double amount) {
        var source = entity.damageSources().generic();
        var damage = new VanillaDamageFabric.DirectDamage(entity, source, partId, amount);
        return VanillaDamageFabric.damageDirect(damage).orElseThrow(
                () -> new AssertionError("原版直接伤害未命中 " + partId)).body();
    }
}
