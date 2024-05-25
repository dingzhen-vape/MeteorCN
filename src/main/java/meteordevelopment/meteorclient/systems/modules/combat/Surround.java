/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.*;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.CardinalDirection;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Surround extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgToggles = settings.createGroup("切换");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // General

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("方块")
        .description("用于包围的方块。")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK)
        .filter(this::blockFilter)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("方块放置之间的延迟，以刻为单位。")
        .min(0)
        .defaultValue(0)
        .build()
    );

    private final Setting<Center> center = sgGeneral.add(new EnumSetting.Builder<Center>()
        .name("居中")
        .description("将你传送到方块的中心。")
        .defaultValue(Center.Incomplete)
        .build()
    );

    private final Setting<Boolean> doubleHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("双重高度")
        .description("在原始包围方块的上方放置黑曜石，以防止人们对着你放置。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("仅在地面")
        .description("只有当你站在方块上时才起作用。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleModules = sgGeneral.add(new BoolSetting.Builder()
        .name("切换模块")
        .description("当包围被激活时关闭其他模块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleBack = sgGeneral.add(new BoolSetting.Builder()
        .name("切换回开")
        .description("当包围被停用时重新开启其他模块。")
        .defaultValue(false)
        .visible(toggleModules::get)
        .build()
    );

    private final Setting<List<Module>> modules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("模块")
        .description("激活时要禁用的模块。")
        .visible(toggleModules::get)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("自动朝向被放置的黑曜石。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> protect = sgGeneral.add(new BoolSetting.Builder()
        .name("保护")
        .description("尝试破坏包围位置周围的水晶，以防止包围被破坏。")
        .defaultValue(true)
        .build()
    );

    // Toggles

    private final Setting<Boolean> toggleOnYChange = sgToggles.add(new BoolSetting.Builder()
        .name("切换Y变化")
        .description("当你的Y级别改变时（步行，跳跃等）自动停用。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toggleOnComplete = sgToggles.add(new BoolSetting.Builder()
        .name("切换完成")
        .description("当所有方块都放置好时切换关闭。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleOnDeath = sgToggles.add(new BoolSetting.Builder()
        .name("切换死亡")
        .description("当你死亡时切换关闭。")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("摇摆")
        .description("当放置包围方块时渲染你的手摇摆。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("在黑曜石将被放置的地方渲染一个方块覆盖。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderBelow = sgRender.add(new BoolSetting.Builder()
        .name("下方")
        .description("渲染你下方的方块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .description("形状的渲染方式。")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> safeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("安全侧颜色")
        .description("安全方块的侧面颜色。")
        .defaultValue(new SettingColor(13, 255, 0, 0))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> safeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("安全线颜色")
        .description("安全方块的线条颜色。")
        .defaultValue(new SettingColor(13, 255, 0, 0))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    private final Setting<SettingColor> normalSideColor = sgRender.add(new ColorSetting.Builder()
        .name("正常侧颜色")
        .description("正常方块的侧面颜色。")
        .defaultValue(new SettingColor(0, 255, 238, 12))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> normalLineColor = sgRender.add(new ColorSetting.Builder()
        .name("正常线颜色")
        .description("正常方块的线条颜色。")
        .defaultValue(new SettingColor(0, 255, 238, 100))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    private final Setting<SettingColor> unsafeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("不安全侧颜色")
        .description("不安全方块的侧面颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 12))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> unsafeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("不安全线颜色")
        .description("不安全方块的线条颜色。")
        .defaultValue(new SettingColor(204, 0, 0, 100))
        .visible(() -> render.get() && shapeMode.get() != ShapeMode.Sides)
        .build()
    );

    private final BlockPos.Mutable placePos = new BlockPos.Mutable();
    private final BlockPos.Mutable renderPos = new BlockPos.Mutable();
    private final BlockPos.Mutable testPos = new BlockPos.Mutable();
    public ArrayList<Module> toActivate = new ArrayList<>();
    private int ticks;

    public Surround() {
        super(Categories.Combat, "包围", "用方块包围你，以防止水晶造成巨大的伤害。");
    }

    // Render

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get()) return;

        // Below
        if (renderBelow.get()) draw(event, null, -1, 0);

        // Regular surround positions
        for (CardinalDirection direction : CardinalDirection.values()) {
            draw(event, direction, 0, doubleHeight.get() ? Dir.UP : 0);
        }

        // Double height
        if (doubleHeight.get()) {
            for (CardinalDirection direction : CardinalDirection.values()) {
                draw(event, direction, 1, Dir.DOWN);
            }
        }
    }

    private void draw(Render3DEvent event, CardinalDirection direction, int y, int exclude) {
        renderPos.set(offsetPosFromPlayer(direction, y));
        Color sideColor = getSideColor(renderPos);
        Color lineColor = getLineColor(renderPos);
        event.renderer.box(renderPos, sideColor, lineColor, shapeMode.get(), exclude);
    }

    // Function

    @Override
    public void onActivate() {
        // Center on activate
        if (center.get() == Center.OnActivate) PlayerUtils.centerPlayer();

        // Reset delay
        ticks = 0;

        if (toggleModules.get() && !modules.get().isEmpty() && mc.world != null && mc.player != null) {
            for (Module module : modules.get()) {
                if (module.isActive()) {
                    module.toggle();
                    toActivate.add(module);
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (toggleBack.get() && !toActivate.isEmpty() && mc.world != null && mc.player != null) {
            for (Module module : toActivate) {
                if (!module.isActive()) {
                    module.toggle();
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Tick the placement timer, should always happen
        if (ticks > 0) {
            ticks--;
            return;
        }
        else {
            ticks = delay.get();
        }

        // Toggle if Y level changed
        if (toggleOnYChange.get() && mc.player.prevY != mc.player.getY()) {
            toggle();
            return;
        }

        // Wait till player is on ground
        if (onlyOnGround.get() && !mc.player.isOnGround()) return;

        // Wait until the player has a block available to place
        if (!getInvBlock().found()) return;

        // Centering player
        if (center.get() == Center.Always) PlayerUtils.centerPlayer();

        // Check surround blocks in order and place the first missing one if present
        int safe = 0;

        // Looping through feet blocks
        for (CardinalDirection direction : CardinalDirection.values()) {
            if (place(direction, 0)) break;
            safe++;
        }

        // Looping through head blocks
        if (doubleHeight.get() && safe == 4) {
            for (CardinalDirection direction : CardinalDirection.values()) {
                if (place(direction, 1)) break;
                safe++;
            }
        }

        boolean complete = safe == (doubleHeight.get() ? 8 : 4);

        // Disable if all the surround blocks are placed
        if (complete && toggleOnComplete.get()) {
            toggle();
            return;
        }

        // Keep the player centered until all the blocks are placed to avoid collision
        if (!complete && center.get() == Center.Incomplete) PlayerUtils.centerPlayer();
    }

    private boolean place(CardinalDirection direction, int y) {
        placePos.set(offsetPosFromPlayer(direction, y));

        // Attempt to place
        boolean placed = BlockUtils.place(
            placePos,
            getInvBlock(),
            rotate.get(),
            100,
            swing.get(),
            true
        );

        // Check if the block is being mined
        boolean beingMined = false;
        for (BlockBreakingInfo value : ((WorldRendererAccessor) mc.worldRenderer).getBlockBreakingInfos().values()) {
            if (value.getPos().equals(placePos)) {
                beingMined = true;
                break;
            }
        }

        boolean isThreat = mc.world.getBlockState(placePos).isReplaceable() || beingMined;

        // If the block is air or is being mined, destroy nearby crystals to be safe
        if (protect.get() && !placed && isThreat) {
            Box box = new Box(
                placePos.getX() - 1, placePos.getY() - 1, placePos.getZ() - 1,
                placePos.getX() + 1, placePos.getY() + 1, placePos.getZ() + 1
            );

            Predicate<Entity> entityPredicate = entity -> entity instanceof EndCrystalEntity && DamageUtils.crystalDamage(mc.player, entity.getPos()) < PlayerUtils.getTotalHealth();

            for (Entity crystal : mc.world.getOtherEntities(null, box, entityPredicate)) {
                if (rotate.get()) {
                    Rotations.rotate(Rotations.getPitch(crystal), Rotations.getYaw(crystal), () -> {
                        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                    });
                }
                else {
                    mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                }

                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
        }

        return placed;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event)  {
        if (event.packet instanceof DeathMessageS2CPacket packet) {
            Entity entity = mc.world.getEntityById(packet.playerId());
            if (entity == mc.player && toggleOnDeath.get()) {
                toggle();
                info("因为你死了而切换关闭。");
            }
        }
    }

    private BlockPos.Mutable offsetPosFromPlayer(CardinalDirection direction, int y) {
        return offsetPos(mc.player.getBlockPos(), direction, y);
    }

    private BlockPos.Mutable offsetPos(BlockPos origin, CardinalDirection direction, int y) {
        if (direction == null) {
            return testPos.set(
                origin.getX(),
                origin.getY() + y,
                origin.getZ()
            );
        }

        return testPos.set(
            origin.getX() + direction.toDirection().getOffsetX(),
            origin.getY() + y,
            origin.getZ() + direction.toDirection().getOffsetZ()
        );
    }

    private BlockType getBlockType(BlockPos pos) {
        BlockState blockState = mc.world.getBlockState(pos);

        // Unbreakable eg. bedrock
        if (blockState.getBlock().getHardness() < 0) return BlockType.Safe;
        // Blast resistant eg. obsidian
        else if (blockState.getBlock().getBlastResistance() >= 600) return BlockType.Normal;
        // Anything else
        else return BlockType.Unsafe;
    }

    private Color getSideColor(BlockPos pos) {
        return switch (getBlockType(pos)) {
            case Safe -> safeSideColor.get();
            case Normal -> normalSideColor.get();
            case Unsafe -> unsafeSideColor.get();
        };
    }

    private Color getLineColor(BlockPos pos) {
        return switch (getBlockType(pos)) {
            case Safe -> safeLineColor.get();
            case Normal -> normalLineColor.get();
            case Unsafe -> unsafeLineColor.get();
        };
    }

    private FindItemResult getInvBlock() {
        return InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
    }

    private boolean blockFilter(Block block) {
        return block == Blocks.OBSIDIAN ||
            block == Blocks.CRYING_OBSIDIAN ||
            block == Blocks.NETHERITE_BLOCK ||
            block == Blocks.ENDER_CHEST ||
            block == Blocks.RESPAWN_ANCHOR;
    }

    public enum Center {
        Never,
        OnActivate,
        Incomplete,
        Always
    }

    public enum BlockType {
        Safe,
        Normal,
        Unsafe
    }
}
