package dev.kizuna.meridian.piglin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.StringValue;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.TimeCursor;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.AddonCost;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Unit;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostPayer;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostPayers;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import org.junit.Test;

import java.util.Random;

public final class PiglinTechniqueCostTest {
    private static final long INITIAL_GOLD_SEED = 3;
    private static final long INITIAL_GOLD = 7;
    private static final double PAYMENT_AMOUNT = 3;
    private static final double INSUFFICIENT_AMOUNT = 8;

    @Test
    public void registeredGoldPayerReturnsSpentPouchProgress() throws Exception {
        TechniqueAddonCostPayer payer = payer();
        BodySnapshot body = coreBody(PiglinGold.withGold(protocolBody(), INITIAL_GOLD));
        AddonCost cost = cost(PAYMENT_AMOUNT);

        TechniqueAddonCostPayer.Payment payment = payer.pay(body, cost);

        assertTrue(payment.accepted());
        assertEquals(Long.toString(INITIAL_GOLD - (long) PAYMENT_AMOUNT),
                payment.progress().unpack(StringValue.class).getValue());
        assertEquals(INITIAL_GOLD, PiglinGold.read(BodyProtobuf.toMessage(body)));
    }

    @Test
    public void insufficientGoldReturnsTranslationAndLeavesBodyUnchanged() {
        TechniqueAddonCostPayer payer = payer();
        BodySnapshot body = coreBody(PiglinGold.withGold(protocolBody(), INITIAL_GOLD));
        AddonCost cost = cost(INSUFFICIENT_AMOUNT);

        TechniqueAddonCostPayer.Payment payment = payer.pay(body, cost);

        assertFalse(payment.accepted());
        assertEquals("technique.meridian_piglin.gold.insufficient", payment.failureTranslationKey());
        assertEquals("金量不足", payment.reason());
        assertEquals(INITIAL_GOLD, PiglinGold.read(BodyProtobuf.toMessage(body)));
    }

    private static TechniqueAddonCostPayer payer() {
        TechniqueAddonCostPayers payers = new TechniqueAddonCostPayers();
        new PiglinTechniqueCosts().registerPayers(payers);
        return payers.find(PiglinTechniqueCosts.PAYER_KEY).orElseThrow();
    }

    private static AddonCost cost(double amount) {
        var quantity = dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Quantity.newBuilder()
                .setValue(amount)
                .setUnit(Unit.GOLD)
                .build();
        return AddonCost.newBuilder()
                .setKey(PiglinTechniqueCosts.PAYER_KEY)
                .setAmount(quantity)
                .build();
    }

    private static BodySnapshot coreBody(dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot body) {
        return BodyProtobuf.fromMessage(body);
    }

    private static dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot protocolBody() {
        return PiglinBody.born(PiglinContent.bundle().getTemplates(0), "technique-cost-test", time(),
                HumanoidTier.MORTAL, new Random(INITIAL_GOLD_SEED));
    }

    private static TimeCursor time() {
        return TimeCursor.newBuilder()
                .setDomain("meridian_piglin:test")
                .setEpoch("technique-cost")
                .setElapsedMillis(1)
                .build();
    }
}
