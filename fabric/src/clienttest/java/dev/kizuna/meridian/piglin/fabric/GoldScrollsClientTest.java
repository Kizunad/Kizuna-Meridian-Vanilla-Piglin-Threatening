package dev.kizuna.meridian.piglin.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueScrollItems;
import dev.kizuna.meridian.humanoid.fabric.HumanoidBirthPlan;
import dev.kizuna.meridian.humanoid.server.HumanoidTier;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;

public final class GoldScrollsClientTest implements FabricClientGameTest {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int SETTLE = 10;
    private static final List<String> SCROLLS = List.of("gold_eating_upper_scroll", "gold_eating_middle_scroll");
    private static final String BOOK = "meridian_piglin:gold_eating";
    private static final double FLOOR_Y = 65;
    private static final double VIEW_DISTANCE = 4;
    private static final float DOWNWARD_THROW = 50;
    private static final float CAMERA_YAW = 180;
    private static final float CAMERA_PITCH = 15;
    private static final int DART_TICKS = 16;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(WIDTH, HEIGHT);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                for (int index = 0; index < SCROLLS.size(); index++) {
                    player.getInventory().setItem(index, new ItemStack(scroll(SCROLLS.get(index))));
                }
                player.getInventory().setSelectedSlot(0);
            });
            context.waitFor(client -> client.player.getAbilities().instabuild
                    && client.player.getMainHandItem().is(scroll(SCROLLS.getFirst())));
            shot(context, "gold-scroll-items");
            context.runOnClient(GoldScrollsClientTest::openCreative);
            context.getInput().setCursorPos(0, 0);
            shot(context, "gold-scroll-creative");
            context.runOnClient(client -> client.gui.setScreen(null));
            String codexCommand = "item replace entity @p weapon.mainhand with meridian_technique:technique_codex";
            world.getServer().runCommand(codexCommand);
            context.waitFor(client -> BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem())
                    .equals(Identifier.parse("meridian_technique:technique_codex")));
            context.runOnClient(client -> client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND));
            context.waitFor(client -> client.gui.screen() != null
                    && client.gui.screen().getClass().getSimpleName().contains("CodexScreen"));
            context.runOnClient(GoldScrollsClientTest::selectBook);
            shot(context, "gold-eating-codex");
            context.runOnClient(client -> client.gui.setScreen(null));
            dartScene(context, world);
        }
    }

    private static void dartScene(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("fill -4 64 -4 4 64 6 minecraft:polished_blackstone_bricks");
        world.getServer().runCommand("time set noon");
        UUID id = world.getServer().computeOnServer(server -> {
            var level = server.overworld();
            var player = server.getPlayerList().getPlayers().getFirst();
            player.teleportTo(0, FLOOR_Y, VIEW_DISTANCE);
            var caster = EntityTypes.PIGLIN.create(level, EntitySpawnReason.COMMAND);
            caster.setPos(0, FLOOR_Y, 0);
            caster.setBaby(false /* baby */);
            caster.setNoAi(true /* noAi */);
            caster.setXRot(DOWNWARD_THROW);
            HumanoidBirthPlan.captureSpawner(caster, level.getCurrentDifficultyAt(caster.blockPosition()),
                    HumanoidTier.TRUE_QI);
            level.addFreshEntity(caster);
            var body = VanillaDamageFabric.inspectBody(caster).orElseThrow();
            var outcome = GoldForms.cast(caster, body, "coin_dart");
            if (outcome.getFailure() != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
                throw new AssertionError("金钱镖未起手 " + outcome.getReason());
            }
            return caster.getUUID();
        });
        context.getInput().lookAt(CAMERA_YAW, CAMERA_PITCH);
        context.waitTicks(DART_TICKS);
        world.getServer().runOnServer(server -> {
            var level = server.overworld();
            var caster = (Piglin) level.getEntity(id);
            var area = caster.getBoundingBox().inflate(GoldAttacks.DART_RANGE);
            var drops = level.getEntitiesOfClass(ItemEntity.class, area,
                    drop -> drop.getOwner() == caster && drop.getItem().is(Items.GOLD_NUGGET));
            if (drops.size() != 1) {
                throw new AssertionError("施放后场内应有一粒金钱镖");
            }
        });
        shot(context, "gold-dart-drop");
        statueScene(context, world, id);
        world.getServer().runCommand("effect give @p meridian_piglin:gilded");
        world.getServer().runCommand("effect give @p meridian_piglin:midased");
        shot(context, "gold-effect-icons");
    }

    private static void statueScene(ClientGameTestContext context, TestSingleplayerContext world, UUID id) {
        int entityId = world.getServer().computeOnServer(server -> {
            var caster = (Piglin) server.overworld().getEntity(id);
            caster.setXRot(0);
            var body = VanillaDamageFabric.inspectBody(caster).orElseThrow();
            var outcome = GoldForms.cast(caster, body, "gold_cast");
            if (outcome.getFailure() != TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED) {
                throw new AssertionError("铸金未起手 " + outcome.getReason());
            }
            return caster.getId();
        });
        context.takeScreenshot(TestScreenshotOptions.of("gold-statue-warmup").disableCounterPrefix());
        context.waitFor(client -> {
            var caster = client.level.getEntity(entityId);
            Integer expected = GoldForms.STATUE_TINT;
            return caster != null && expected.equals(caster.getAttached(GoldForms.SKIN_TINT));
        });
        shot(context, "gold-statue-active");
    }

    private static Item scroll(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("meridian_piglin", path);
        return TechniqueScrollItems.scrolls().get(id);
    }

    private static void openCreative(Minecraft client) {
        var screen = new CreativeModeInventoryScreen(client.player, client.level.enabledFeatures(),
                true /* displayOperatorCreativeTab */);
        client.gui.setScreen(screen);
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(TechniqueScrollItems.GROUP_ID);
        var pages = (FabricCreativeModeInventoryScreen) screen;
        pages.switchToPage(pages.getPage(tab));
        pages.setSelectedTab(tab);
        for (String path : SCROLLS) {
            Item item = scroll(path);
            if (tab.getDisplayItems().stream().noneMatch(stack -> stack.is(item))) {
                throw new AssertionError("功法创造栏缺卷轴 " + path);
            }
        }
    }

    private static void selectBook(Minecraft client) {
        try {
            var screen = client.gui.screen();
            var accessor = screen.getClass().getDeclaredMethod("panel");
            accessor.setAccessible(true /* accessible */);
            Object panel = accessor.invoke(screen);
            var select = panel.getClass().getDeclaredMethod("select", String.class);
            select.setAccessible(true /* accessible */);
            select.invoke(panel, BOOK);
            var selection = panel.getClass().getDeclaredMethod("selectedBook");
            selection.setAccessible(true /* accessible */);
            if (!BOOK.equals(selection.invoke(panel))) {
                throw new AssertionError("功法谱未选中服金诀");
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("无法翻到服金诀书页", failure);
        }
    }

    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> {
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.gui.toastManager().clear();
            client.gui.hud.getChat().clearMessages(false /* clearSentHistory */);
        });
        context.waitTicks(SETTLE);
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    }
}
