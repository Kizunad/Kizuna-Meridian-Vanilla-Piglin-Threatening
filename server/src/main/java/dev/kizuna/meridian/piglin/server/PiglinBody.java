package dev.kizuna.meridian.piglin.server;

import dev.kizuna.meridian.human.server.HumanBirth;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.QiDensity;
import dev.kizuna.meridian.protocol.pb.BodyMessages.*;
import java.util.Set;

/** 猪灵身体的出生快照和致死裁决。 */
public final class PiglinBody {
    private static final int BODY_SCHEMA_VERSION = 3;
    private static final double DEATH_HP_RATIO = 0.2;
    private static final Set<String> VITAL_PARTS = Set.of("head", "neck", "chest");
    private static final QiInventory EMPTY = QiInventory.getDefaultInstance();
    private static final MeridianOpening CLOSED = closedOpening();

    private PiglinBody() {
        // 身体状态由宿主保存，本类只产生候选快照。
    }

    public static BodySnapshot born(BodyTemplate template, String bodyId, TimeCursor now) {
        var vitalityBuilder = VitalityState.newBuilder();
        vitalityBuilder.setAgeSeconds(HumanBirth.INITIAL_AGE_YEARS * HumanBirth.SECONDS_PER_YEAR);
        vitalityBuilder.setLifespanLimitSeconds(HumanBirth.LIFESPAN_YEARS * HumanBirth.SECONDS_PER_YEAR);
        vitalityBuilder.setSpentLifespanSeconds(0);
        vitalityBuilder.setDead(false /* dead */);
        var structure = template.getStructure();
        var bodyBuilder = BodySnapshot.newBuilder();
        bodyBuilder.setSchemaVersion(BODY_SCHEMA_VERSION);
        bodyBuilder.setRevision(0);
        bodyBuilder.setId(bodyId);
        bodyBuilder.setTemplate(template.getReference());
        bodyBuilder.setStructure(structure);
        bodyBuilder.setRealm(Realm.WANDERING_QI);
        bodyBuilder.setSettledAt(now);
        bodyBuilder.setVitality(vitalityBuilder.build());
        addHealthyParts(bodyBuilder, structure);
        addEmptyMeridians(bodyBuilder, structure);
        return validated(bodyBuilder.build());
    }

    public static boolean lethal(BodySnapshot wounded) {
        boolean vitalPartDestroyed = wounded.getPartStatesList().stream()
                .anyMatch(part -> part.getHp() <= 0 && VITAL_PARTS.contains(part.getId()));
        if (vitalPartDestroyed) {
            return true;
        }
        double currentHp = wounded.getPartStatesList().stream()
                .mapToDouble(PartState::getHp)
                .sum();
        double maximumHp = wounded.getStructure().getPartsList().stream()
                .mapToDouble(PartDefinition::getMaxHp)
                .sum();
        return currentHp < maximumHp * DEATH_HP_RATIO;
    }

    private static void addHealthyParts(BodySnapshot.Builder body, BodyStructure structure) {
        for (var part : structure.getPartsList()) {
            var stateBuilder = PartState.newBuilder();
            stateBuilder.setId(part.getId());
            stateBuilder.setHp(part.getMaxHp());
            body.addPartStates(stateBuilder.build());
        }
        for (var tissue : structure.getTissuesList()) {
            var stateBuilder = TissueState.newBuilder();
            stateBuilder.setId(tissue.getId());
            stateBuilder.setHp(tissue.getMaxHp());
            stateBuilder.setFatigue(0);
            body.addTissueStates(stateBuilder.build());
        }
        for (var organ : structure.getOrgansList()) {
            var stateBuilder = OrganState.newBuilder();
            stateBuilder.setId(organ.getId());
            stateBuilder.setHp(organ.getMaxHp());
            stateBuilder.setBuffer(EMPTY);
            body.addOrganStates(stateBuilder.build());
        }
    }

    private static void addEmptyMeridians(BodySnapshot.Builder body, BodyStructure structure) {
        for (var meridian : structure.getMeridiansList()) {
            var stateBuilder = MeridianState.newBuilder();
            stateBuilder.setId(meridian.getId());
            stateBuilder.setLumenQi(EMPTY);
            stateBuilder.setWallQi(EMPTY);
            stateBuilder.setFatigue(0);
            stateBuilder.setCondition(MeridianCondition.INTACT);
            stateBuilder.setOpening(CLOSED);
            body.addMeridianStates(stateBuilder.build());
        }
    }

    private static MeridianOpening closedOpening() {
        var builder = MeridianOpening.newBuilder();
        builder.setStage(MeridianOpeningStage.UNOPENED);
        builder.setProgress(0);
        return builder.build();
    }

    private static BodySnapshot validated(BodySnapshot candidate) {
        var validated = BodyProtobuf.fromMessage(candidate);
        return BodyProtobuf.toMessage(validated);
    }
}
