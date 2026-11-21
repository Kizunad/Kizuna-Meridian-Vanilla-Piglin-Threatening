package dev.kizuna.meridian.piglin.server;

import com.google.protobuf.Any;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** 猪灵金囊的容量、出生金量和物品换算规则。 */
public final class PiglinGold {
    public static final String PROGRESS_KEY = "meridian_piglin:gold";
    public static final long NUGGET_VALUE = 1;
    public static final long INGOT_VALUE = 9;
    public static final long BLOCK_VALUE = 81;
    public static final long MORTAL_CAPACITY = 9;
    public static final long WANDERING_QI_CAPACITY = 27;
    public static final long TRUE_QI_CAPACITY = 54;
    public static final long WANDERING_QI_INITIAL_MIN = 9;
    public static final long WANDERING_QI_INITIAL_MAX = 18;
    public static final long TRUE_QI_INITIAL_MIN = 27;
    public static final long TRUE_QI_INITIAL_MAX = 54;

    private PiglinGold() {
    }

    public static long capacity(HumanoidTier tier) {
        return switch (tier) {
            case MORTAL -> MORTAL_CAPACITY;
            case WANDERING_QI -> WANDERING_QI_CAPACITY;
            case TRUE_QI -> TRUE_QI_CAPACITY;
        };
    }

    public static long initial(HumanoidTier tier, RandomGenerator random) {
        if (tier == HumanoidTier.MORTAL) {
            return 0;
        }
        long minimum = tier == HumanoidTier.TRUE_QI ? TRUE_QI_INITIAL_MIN : WANDERING_QI_INITIAL_MIN;
        long maximum = tier == HumanoidTier.TRUE_QI ? TRUE_QI_INITIAL_MAX : WANDERING_QI_INITIAL_MAX;
        return random.nextLong(minimum, maximum + 1);
    }

    public static long read(BodySnapshot body) {
        if (!body.containsAddonProgress(PROGRESS_KEY)) {
            return 0;
        }
        Any encoded = body.getAddonProgressOrDefault(PROGRESS_KEY, Any.getDefaultInstance());
        long amount;
        if (encoded.is(StringValue.class)) {
            amount = readString(encoded);
        } else if (encoded.is(Int64Value.class)) {
            amount = readLegacyInt64(encoded);
        } else {
            throw new IllegalArgumentException("金囊金量载荷类型无效");
        }
        if (amount < 0 || amount > capacity(HumanoidTier.of(body))) {
            throw new IllegalArgumentException("金囊金量超出容量");
        }
        return amount;
    }

    private static long readString(Any encoded) {
        String value;
        try {
            value = encoded.unpack(StringValue.class).getValue();
        } catch (InvalidProtocolBufferException invalid) {
            throw new IllegalArgumentException("金囊金量编码无效", invalid);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("金囊金量编码无效", invalid);
        }
    }

    private static long readLegacyInt64(Any encoded) {
        try {
            return encoded.unpack(Int64Value.class).getValue();
        } catch (InvalidProtocolBufferException invalid) {
            throw new IllegalArgumentException("金囊金量编码无效", invalid);
        }
    }

    public static Optional<Long> readOptional(BodySnapshot body) {
        if (!body.containsAddonProgress(PROGRESS_KEY)) {
            return Optional.empty();
        }
        return Optional.of(read(body));
    }

    public static Any progress(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("金囊金量不能为负数");
        }
        return Any.pack(StringValue.of(Long.toString(amount)));
    }

    public static BodySnapshot withGold(BodySnapshot body, long amount) {
        long limit = capacity(HumanoidTier.of(body));
        if (amount < 0 || amount > limit) {
            throw new IllegalArgumentException("金囊金量超出容量");
        }
        return body.toBuilder().putAddonProgress(PROGRESS_KEY, progress(amount)).build();
    }

    public static long value(String itemId) {
        return switch (itemId) {
            case "minecraft:gold_nugget" -> NUGGET_VALUE;
            case "minecraft:gold_ingot" -> INGOT_VALUE;
            case "minecraft:gold_block" -> BLOCK_VALUE;
            default -> 0;
        };
    }
}
