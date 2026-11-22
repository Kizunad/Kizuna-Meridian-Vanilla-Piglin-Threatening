package dev.kizuna.meridian.piglin.server;

import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.AddonCost;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Quantity;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Unit;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostEntrypoint;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostPayer;
import dev.kizuna.meridian.technique.server.TechniqueAddonCostPayers;

/** 猪灵金囊为功法库提供附属费用支付方。 */
public final class PiglinTechniqueCosts implements TechniqueAddonCostEntrypoint {
    public static final String PAYER_KEY = PiglinGold.PROGRESS_KEY;
    private static final String INSUFFICIENT_TRANSLATION_KEY =
            "technique.meridian_piglin.gold.insufficient";
    private static final String INSUFFICIENT_REASON = "金量不足";

    @Override
    public void registerPayers(TechniqueAddonCostPayers payers) {
        payers.register(new GoldPayer());
    }

    private static final class GoldPayer implements TechniqueAddonCostPayer {
        @Override
        public String key() {
            return PAYER_KEY;
        }

        @Override
        public Payment pay(BodySnapshot body, AddonCost cost) {
            validateKey(cost);
            long requested = requestedGold(cost);
            long available = PiglinGold.read(BodyProtobuf.toMessage(body));
            if (available < requested) {
                return Payment.rejected(INSUFFICIENT_TRANSLATION_KEY, INSUFFICIENT_REASON);
            }
            long remaining = available - requested;
            return Payment.accepted(PiglinGold.progress(remaining));
        }

        private static void validateKey(AddonCost cost) {
            if (!PAYER_KEY.equals(cost.getKey())) {
                throw new IllegalArgumentException("附属费用键不属于猪灵金囊支付方");
            }
        }

        private static long requestedGold(AddonCost cost) {
            Quantity quantity = cost.getAmount();
            if (!cost.hasAmount() || quantity.getUnit() != Unit.GOLD) {
                throw new IllegalArgumentException("猪灵金囊费用必须使用 GOLD 单位");
            }
            double amount = quantity.getValue();
            if (!Double.isFinite(amount) || amount <= 0 || amount != Math.rint(amount)
                    || amount > Long.MAX_VALUE) {
                throw new IllegalArgumentException("猪灵金囊费用必须是正整数");
            }
            return (long) amount;
        }
    }
}
