package dev.kizuna.meridian.piglin.server;

import dev.kizuna.meridian.human.server.HumanBirth;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.*;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

/** 猪灵身体的出生快照和致死裁决。 */
public final class PiglinBody {
    private static final int BODY_SCHEMA_VERSION = 3;
    private static final int MERIDIANS_PER_WANDERING_QI = 8;
    private static final double DEATH_HP_RATIO = 0.2;
    private static final long DEFAULT_RANDOM_SEED = 0L;
    private static final Set<String> VITAL_PARTS = Set.of("head", "neck", "chest");
    private static final QiInventory EMPTY = QiInventory.getDefaultInstance();
    private static final MeridianOpening CLOSED = closedOpening();

    private PiglinBody() {
        // 身体状态由宿主保存，本类只产生候选快照。
    }

    public static BodySnapshot born(BodyTemplate template, String bodyId, TimeCursor now) {
        return born(template, bodyId, now, HumanoidTier.MORTAL, new Random(DEFAULT_RANDOM_SEED));
    }

    public static BodySnapshot born(BodyTemplate template, String bodyId, TimeCursor now,
                                    HumanoidTier tier, RandomGenerator random) {
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
        bodyBuilder.setRealm(realm(tier));
        bodyBuilder.setSettledAt(now);
        bodyBuilder.setVitality(vitalityBuilder.build());
        addHealthyParts(bodyBuilder, structure);
        addMeridians(bodyBuilder, structure, tier);
        bodyBuilder.putAddonProgress(PiglinGold.PROGRESS_KEY, PiglinGold.progress(PiglinGold.initial(tier, random)));
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

    private static void addMeridians(BodySnapshot.Builder body, BodyStructure structure, HumanoidTier tier) {
        for (int index = 0; index < structure.getMeridiansCount(); index++) {
            var meridian = structure.getMeridians(index);
            var stateBuilder = MeridianState.newBuilder();
            stateBuilder.setId(meridian.getId());
            stateBuilder.setLumenQi(EMPTY);
            stateBuilder.setWallQi(EMPTY);
            stateBuilder.setFatigue(0);
            stateBuilder.setCondition(MeridianCondition.INTACT);
            stateBuilder.setOpening(opening(tier, index));
            body.addMeridianStates(stateBuilder.build());
        }
    }

    private static Realm realm(HumanoidTier tier) {
        if (tier == HumanoidTier.TRUE_QI) {
            return Realm.TRUE_QI;
        }
        return Realm.WANDERING_QI;
    }

    private static MeridianOpening opening(HumanoidTier tier, int index) {
        if (tier == HumanoidTier.MORTAL) {
            return CLOSED;
        }
        if (tier == HumanoidTier.WANDERING_QI && index >= MERIDIANS_PER_WANDERING_QI) {
            return CLOSED;
        }
        var builder = MeridianOpening.newBuilder();
        builder.setStage(MeridianOpeningStage.OPENED);
        builder.setProgress(1);
        return builder.build();
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
