package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.UUID;

public final class PiglinBodyClientTest implements FabricClientGameTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final double DAMAGE = 2;
    private static final String DAMAGED_PART_ID = "chest";
    private static final double EXPECTED_DAMAGED_PART_HP = 38;
    private static final int FLOOR_Y = 64;
    private static final int STAND_Y = FLOOR_Y + 1;
    private static final int PLAYER_Z = 4;
    private static final int ENTITY_Z = 0;
    private static final int CLEAR_RADIUS = 16;
    private static final int CLEAR_TOP = 90;
    private static final double PIGLIN_X = -1;
    private static final double BRUTE_X = 1;
    private static final float CAMERA_YAW = 180;
    private static final float ENTITY_FACING_PLAYER_YAW = 0;
    private static final float BODY_PITCH = 7;
    private static final float PIGLIN_YAW = (float) Math.toDegrees(
            Math.atan2(-PIGLIN_X, ENTITY_Z - PLAYER_Z));
    private static final int LENS_REFRESH_TICKS = 20;
    private static final Identifier LENS_ID = Identifier.fromNamespaceAndPath("meridian_debug", "limb_lens");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(WIDTH, HEIGHT);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            prepareWorld(context, world);
            UUID piglinUuid = world.getServer().computeOnServer(PiglinBodyClientTest::spawnLineup);
            context.getInput().lookAt(CAMERA_YAW, BODY_PITCH);
            clearClientPopups(context);
            hideHud(context);
            context.waitTicks(LENS_REFRESH_TICKS);
            takeScreenshot(context, "piglin-brute-lineup");

            world.getServer().runOnServer(server -> {
                woundPiglin(server, piglinUuid);
                giveLimbLens(server);
            });
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                if (client.gui.hud.isHidden()) {
                    client.gui.hud.toggle();
                }
            });
            context.getInput().lookAt(PIGLIN_YAW, BODY_PITCH);
            context.waitFor(client -> client.player.getMainHandItem().is(
                    BuiltInRegistries.ITEM.getValue(LENS_ID)));
            context.waitTicks(LENS_REFRESH_TICKS);
            clearClientPopups(context);
            takeScreenshot(context, "piglin-wounded-body");
        }
    }

    private static void prepareWorld(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("fill -" + CLEAR_RADIUS + " " + FLOOR_Y + " -" + CLEAR_RADIUS + " "
                + CLEAR_RADIUS + " " + CLEAR_TOP + " " + CLEAR_RADIUS + " air");
        world.getServer().runCommand("fill -" + CLEAR_RADIUS + " " + FLOOR_Y + " -" + CLEAR_RADIUS + " "
                + CLEAR_RADIUS + " " + FLOOR_Y + " " + CLEAR_RADIUS + " stone");
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("weather clear");
        world.getServer().runOnServer(host -> {
            var player = host.getPlayerList().getPlayers().getFirst();
            host.getPlayerList().op(player.nameAndId());
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(0, STAND_Y, PLAYER_Z);
        });
        clearClientPopups(context);
        hideHud(context);
    }

    private static UUID spawnLineup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var piglin = spawn(level, EntityTypes.PIGLIN, PIGLIN_X, Items.CROSSBOW);
        spawn(level, EntityTypes.PIGLIN_BRUTE, BRUTE_X, Items.GOLDEN_AXE);
        return piglin.getUUID();
    }

    private static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, double x, Item heldItem) {
        var entity = type.create(level, EntitySpawnReason.NATURAL);
        entity.setPos(x, STAND_Y, ENTITY_Z);
        entity.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                EntitySpawnReason.NATURAL, null);
        if (entity instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
            piglin.setBaby(false /* adult screenshot fixture */);
        }
        entity.setNoAi(true /* disableMobAi */);
        entity.setYRot(ENTITY_FACING_PLAYER_YAW);
        entity.setYHeadRot(ENTITY_FACING_PLAYER_YAW);
        entity.setYBodyRot(ENTITY_FACING_PLAYER_YAW);
        entity.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(heldItem));
        entity.setPersistenceRequired();
        if (!level.addFreshEntity(entity)) {
            throw new AssertionError("猪灵客户端测试实体未入场");
        }
        return entity;
    }

    private static void woundPiglin(MinecraftServer server, UUID piglinUuid) {
        var target = server.overworld().getEntity(piglinUuid);
        if (!(target instanceof Mob piglin)) {
            throw new AssertionError("猪灵客户端测试目标已丢失");
        }
        var before = VanillaDamageFabric.inspectBody(piglin).orElseThrow();
        if (VanillaDamageFabric.damageDirect(new VanillaDamageFabric.DirectDamage(
                piglin, piglin.damageSources().generic(), DAMAGED_PART_ID, DAMAGE)).isEmpty()) {
            throw new AssertionError("猪灵定点伤害未提交");
        }
        var after = VanillaDamageFabric.inspectBody(piglin).orElseThrow();
        double damagedPartHp = partHp(after, DAMAGED_PART_ID);
        if (!hasWound(before, after) || partHp(before, DAMAGED_PART_ID) <= damagedPartHp
                || damagedPartHp != EXPECTED_DAMAGED_PART_HP) {
            throw new AssertionError("猪灵伤害未产生身体伤势");
        }
    }

    private static void giveLimbLens(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var lens = BuiltInRegistries.ITEM.getValue(LENS_ID);
        if (lens == Items.AIR) {
            throw new AssertionError("客户端测试缺少肢体透视镜");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(lens));
    }

    private static void hideHud(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            if (!client.gui.hud.isHidden()) {
                client.gui.hud.toggle();
            }
        });
    }

    private static void clearClientPopups(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.getTutorial().setStep(TutorialSteps.NONE);
            client.gui.hud.getChat().clearMessages(false /* clearSentHistory */);
            client.gui.toastManager().clear();
        });
    }

    private static void takeScreenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    }

    private static boolean hasWound(dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot before,
                                    dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot after) {
        return after.getPartStatesList().stream().anyMatch(state -> before.getPartStatesList().stream()
                .filter(previous -> previous.getId().equals(state.getId()))
                .findFirst().orElseThrow().getHp() > state.getHp());
    }

    private static double partHp(dev.kizuna.meridian.protocol.pb.BodyMessages.BodySnapshot body, String partId) {
        return body.getPartStatesList().stream()
                .filter(state -> state.getId().equals(partId))
                .findFirst().orElseThrow().getHp();
    }
}
