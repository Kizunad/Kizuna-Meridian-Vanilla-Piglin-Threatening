package dev.kizuna.meridian.piglin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import dev.kizuna.meridian.protocol.pb.BodyMessages.PartState;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TimeCursor;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;

public final class PiglinBodyTest {
    private static final double DESTROY = 100;

    @Test
    public void birthIncludesFullHealthAndEmptyGoldPouch() {
        var template = PiglinContent.bundle().getTemplates(0);
        var now = TimeCursor.newBuilder().setDomain("meridian_piglin:test").setEpoch("birth")
                .setElapsedMillis(1).build();
        var born = PiglinBody.born(template, "test_piglin", now);
        var parts = born.getPartStatesList().stream().collect(Collectors.toMap(PartState::getId, Function.identity()));
        for (var definition : template.getStructure().getPartsList()) {
            assertEquals(definition.getMaxHp(), parts.get(definition.getId()).getHp(), 0);
        }
        var pouch = born.getOrganStatesList().stream().filter(state -> state.getId().equals(PiglinContent.GOLD_POUCH))
                .findFirst().orElseThrow();
        assertTrue(pouch.getBuffer().getEntriesList().isEmpty());
        assertEquals(now, born.getSettledAt());
    }

    @Test
    public void vitalPartsAreLethalAndLowerPartsAreNot() {
        var born = birth();
        for (String id : new String[] {"head", "neck", "chest"}) {
            assertTrue(PiglinBody.lethal(wound(born, id, DESTROY)));
        }
        assertFalse(PiglinBody.lethal(wound(born, "abdomen", DESTROY)));
    }

    private static BodySnapshot birth() {
        return PiglinBody.born(PiglinContent.bundle().getTemplates(0), "test_piglin",
                TimeCursor.newBuilder().setDomain("meridian_piglin:test").setEpoch("birth")
                        .setElapsedMillis(1).build());
    }

    private static BodySnapshot wound(BodySnapshot original, String partId, double amount) {
        var builder = original.toBuilder();
        for (int index = 0; index < builder.getPartStatesCount(); index++) {
            var state = builder.getPartStates(index);
            if (state.getId().equals(partId)) {
                builder.setPartStates(index, state.toBuilder().setHp(Math.max(0, state.getHp() - amount)).build());
            }
        }
        return builder.build();
    }
}
