package dev.kizuna.meridian.piglin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.Any;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TimeCursor;
import java.util.Random;
import org.junit.Test;

public final class PiglinGoldTest {
    private static final long INITIAL_SAMPLES = 128;
    private static final long INITIAL_GOLD_SAMPLE_SEED = 17;
    private static final long TIER_BIRTH_SEED = 31;
    private static final long BODY_BIRTH_SEED = 3;
    private static final long MISSING_GOLD = 0;
    private static final long LEGACY_GOLD_AMOUNT = 7;

    @Test
    public void capacitiesMatchThreeTiers() {
        assertEquals(PiglinGold.MORTAL_CAPACITY, PiglinGold.capacity(HumanoidTier.MORTAL));
        assertEquals(PiglinGold.WANDERING_QI_CAPACITY, PiglinGold.capacity(HumanoidTier.WANDERING_QI));
        assertEquals(PiglinGold.TRUE_QI_CAPACITY, PiglinGold.capacity(HumanoidTier.TRUE_QI));
    }

    @Test
    public void initialGoldStaysInsideTierRange() {
        Random random = new Random(INITIAL_GOLD_SAMPLE_SEED);
        for (int index = 0; index < INITIAL_SAMPLES; index++) {
            long wandering = PiglinGold.initial(HumanoidTier.WANDERING_QI, random);
            long trueQi = PiglinGold.initial(HumanoidTier.TRUE_QI, random);
            assertTrue(wandering >= PiglinGold.WANDERING_QI_INITIAL_MIN);
            assertTrue(wandering <= PiglinGold.WANDERING_QI_INITIAL_MAX);
            assertTrue(trueQi >= PiglinGold.TRUE_QI_INITIAL_MIN);
            assertTrue(trueQi <= PiglinGold.TRUE_QI_INITIAL_MAX);
        }
        assertEquals(MISSING_GOLD, PiglinGold.initial(HumanoidTier.MORTAL, random));
    }

    @Test
    public void itemValuesUseExplicitGoldMapping() {
        assertEquals(PiglinGold.NUGGET_VALUE, PiglinGold.value("minecraft:gold_nugget"));
        assertEquals(PiglinGold.INGOT_VALUE, PiglinGold.value("minecraft:gold_ingot"));
        assertEquals(PiglinGold.BLOCK_VALUE, PiglinGold.value("minecraft:gold_block"));
        assertEquals(0, PiglinGold.value("minecraft:golden_sword"));
        assertEquals(0, PiglinGold.value("minecraft:stone"));
    }

    @Test
    public void progressUsesNonEmptyPayloadAndReadsLegacyInt64() {
        assertTrue(PiglinGold.progress(0).getValue().size() > 0);
        BodySnapshot body = birth(HumanoidTier.MORTAL);
        BodySnapshot legacy = body.toBuilder()
                .putAddonProgress(PiglinGold.PROGRESS_KEY, Any.pack(Int64Value.of(LEGACY_GOLD_AMOUNT)))
                .build();
        assertEquals(LEGACY_GOLD_AMOUNT, PiglinGold.read(legacy));
        assertEquals(0, PiglinGold.read(body.toBuilder().clearAddonProgress().build()));
    }

    @Test
    public void malformedAndOutOfRangeGoldIsRejected() {
        BodySnapshot body = birth(HumanoidTier.MORTAL);
        BodySnapshot malformed = body.toBuilder()
                .putAddonProgress(PiglinGold.PROGRESS_KEY, Any.pack(StringValue.of("not-a-number")))
                .build();
        BodySnapshot negative = body.toBuilder()
                .putAddonProgress(PiglinGold.PROGRESS_KEY, Any.pack(StringValue.of("-1")))
                .build();
        BodySnapshot overCapacity = body.toBuilder()
                .putAddonProgress(PiglinGold.PROGRESS_KEY, PiglinGold.progress(PiglinGold.MORTAL_CAPACITY + 1))
                .build();
        assertThrows(IllegalArgumentException.class, () -> PiglinGold.read(malformed));
        assertThrows(IllegalArgumentException.class, () -> PiglinGold.read(negative));
        assertThrows(IllegalArgumentException.class, () -> PiglinGold.read(overCapacity));
        assertThrows(IllegalArgumentException.class, () -> PiglinGold.withGold(body, -1));
        assertThrows(IllegalArgumentException.class,
                () -> PiglinGold.withGold(body, PiglinGold.MORTAL_CAPACITY + 1));
    }

    @Test
    public void withGoldHonorsEachCapacityBoundary() {
        for (HumanoidTier tier : HumanoidTier.values()) {
            BodySnapshot body = birth(tier);
            long capacity = PiglinGold.capacity(tier);
            assertEquals(capacity, PiglinGold.read(PiglinGold.withGold(body, capacity)));
        }
    }

    @Test
    public void bornBodyUsesTierCapacityAndInitialGold() {
        Random random = new Random(TIER_BIRTH_SEED);
        for (HumanoidTier tier : HumanoidTier.values()) {
            BodySnapshot body = PiglinBody.born(PiglinContent.bundle().getTemplates(0),
                    "gold-tier-" + tier.name(), time(), tier, random);
            long amount = PiglinGold.read(body);
            assertTrue(amount <= PiglinGold.capacity(tier));
            if (tier == HumanoidTier.MORTAL) {
                assertEquals(0, amount);
            } else {
                assertTrue(amount >= PiglinGold.WANDERING_QI_INITIAL_MIN);
            }
        }
    }

    private static BodySnapshot birth(HumanoidTier tier) {
        return PiglinBody.born(PiglinContent.bundle().getTemplates(0), "gold-test", time(), tier,
                new Random(BODY_BIRTH_SEED));
    }

    private static TimeCursor time() {
        TimeCursor now = TimeCursor.newBuilder()
                .setDomain("meridian_piglin:test")
                .setEpoch("gold")
                .setElapsedMillis(1)
                .build();
        return now;
    }
}
