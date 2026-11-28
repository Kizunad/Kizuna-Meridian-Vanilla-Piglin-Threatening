package dev.kizuna.meridian.piglin.fabric;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 性能基准，不是行为回归用例：量 30 只猪灵的服务端 tick 墙钟耗时。 */
public final class PiglinCrowdPerfGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("meridian_piglin_perf");
    private static final String PERF_MODE = "perf";
    private static final String OUTPUT = "perf-piglin.txt";
    private static final int PIGLINS = 30;
    private static final int COLUMNS = 6;
    private static final int START_X = 3;
    private static final int START_Z = 3;
    private static final int COLUMN_SPACING = 2;
    private static final int ROW_SPACING = 2;
    private static final int IDLE_TICKS = 40;
    private static final int WARMUP_TICKS = 200;
    private static final int WINDOW_TICKS = 600;
    private static final int TIMEOUT = IDLE_TICKS + WARMUP_TICKS + WINDOW_TICKS + 40;
    private static final double MAX_WINDOW_MEAN_MILLIS = 12.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static boolean listening;
    private static long tickStartedNanos;
    private static long lastTickNanos;

    @GameTest(maxTicks = TIMEOUT)
    public void thirtyPiglinsMeasureTickCost(GameTestHelper context) {
        if (!PERF_MODE.equals(System.getProperty("meridian.repro.mode"))) {
            context.succeed();
            return;
        }
        listen();
        new Run(context).tick();
    }

    private static void listen() {
        if (listening) {
            return;
        }
        listening = true;
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStartedNanos = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> lastTickNanos = System.nanoTime() - tickStartedNanos);
    }

    private static List<Piglin> spawnAll(GameTestHelper context) {
        var piglins = new ArrayList<Piglin>(PIGLINS);
        for (int index = 0; index < PIGLINS; index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            var entity = EntityTypes.PIGLIN.create(context.getLevel(), EntitySpawnReason.COMMAND);
            if (entity == null) {
                throw new AssertionError("性能基准猪灵生成失败 " + index);
            }
            var relative = new BlockPos(START_X + column * COLUMN_SPACING, 1,
                    START_Z + row * ROW_SPACING);
            entity.setPos(Vec3.atBottomCenterOf(context.absolutePos(relative)));
            entity.setPersistenceRequired();
            context.assertTrue(context.getLevel().addFreshEntity(entity), "性能基准猪灵入世失败 " + index);
            piglins.add(entity);
        }
        return piglins;
    }

    private static final class Bucket {
        private final String label;
        private final long[] nanos;
        private int count;

        private Bucket(String label, int capacity) {
            this.label = label;
            nanos = new long[capacity];
        }

        private void add(long tickNanos) {
            nanos[count++] = tickNanos;
        }

        private String describe() {
            long[] sorted = Arrays.copyOf(nanos, count);
            Arrays.sort(sorted);
            double mean = Arrays.stream(sorted).average().orElse(0) / NANOS_PER_MILLI;
            double median = sorted[count / 2] / NANOS_PER_MILLI;
            double p95 = sorted[Math.min(count - 1, count * 95 / 100)] / NANOS_PER_MILLI;
            double maximum = sorted[count - 1] / NANOS_PER_MILLI;
            return String.format(Locale.ROOT,
                    "%s ticks=%d mean_ms=%.2f median_ms=%.2f p95_ms=%.2f max_ms=%.2f",
                    label, count, mean, median, p95, maximum);
        }

        private double meanMillis() {
            return Arrays.stream(nanos, 0, count).average().orElse(0) / NANOS_PER_MILLI;
        }
    }

    private static final class Run {
        private final GameTestHelper context;
        private final Bucket idle = new Bucket("idle", IDLE_TICKS);
        private final Bucket warmup = new Bucket("warmup", WARMUP_TICKS);
        private final Bucket window = new Bucket("window", WINDOW_TICKS);
        private List<Piglin> piglins = List.of();
        private int ticks;

        private Run(GameTestHelper context) {
            this.context = context;
        }

        private void tick() {
            if (ticks > 1 && ticks <= IDLE_TICKS) {
                idle.add(lastTickNanos);
            } else if (ticks > IDLE_TICKS + 1 && ticks <= IDLE_TICKS + 1 + WARMUP_TICKS) {
                warmup.add(lastTickNanos);
            } else if (ticks > IDLE_TICKS + 1 + WARMUP_TICKS) {
                window.add(lastTickNanos);
            }
            if (ticks == IDLE_TICKS) {
                piglins = spawnAll(context);
            }
            if (ticks == IDLE_TICKS + 1 + WARMUP_TICKS + WINDOW_TICKS) {
                finish();
                return;
            }
            ticks++;
            context.runAfterDelay(1, this::tick);
        }

        private void finish() {
            var lines = List.of(idle.describe(), warmup.describe(), window.describe());
            lines.forEach(line -> LOGGER.info("piglin-perf {}", line));
            try {
                Files.write(Path.of(OUTPUT), lines, StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            context.assertTrue(window.meanMillis() <= MAX_WINDOW_MEAN_MILLIS,
                    "猪灵窗口均值超出性能上限 " + window.meanMillis() + " ms");
            piglins.forEach(Piglin::discard);
            context.succeed();
        }
    }
}
