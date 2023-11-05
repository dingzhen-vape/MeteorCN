/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("选定的块。")
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("如何使用块列表设置")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Boolean> fastTower = sgGeneral.add(new BoolSetting.Builder()
        .name("fast-tower")
        .description("是否更快地向上搭建脚手架。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> cancelVelocity = sgGeneral.add(new BoolSetting.Builder()
        .name("取消-velocity")
        .description("高耸时是否取消速度。")
        .defaultValue(false)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("仅在按住右键时放置方块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("渲染客户端摆动。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("自动切换")
        .description("放置前自动交换到一个块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("旋转")
        .description("朝着正在放置的块旋转。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("空中放置")
        .description("允许空中放置。这还允许您修改支架半径。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("closest-block-range")
        .description("当你在空中时支架可以放置方块多远。")
        .defaultValue(4)
        .min(0)
        .sliderMax(8)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("支架半径。")
        .defaultValue(0)
        .min(0)
        .max(6)
        .visible(() -> airPlace.get())
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick ")
        .description("一次要放置多少块。")
        .defaultValue(3)
        .min(1)
        .visible(() -> airPlace.get())
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染")
        .description("是否渲染已放置的块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("如何渲染形状。")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("目标块渲染的侧面颜色。")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("目标块渲染的线条颜色。")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(render::get)
        .build()
    );

    private final BlockPos.Mutable bp = new BlockPos.Mutable();
    private final BlockPos.Mutable prevBp = new BlockPos.Mutable();

    private boolean lastWasSneaking;
    private double lastSneakingY;

    public Scaffold() {
        super(Categories.Movement, "scaffold", "自动将块放置在你的下方。");
    }

    @Override
    public void onActivate() {
        lastWasSneaking = mc.options.sneakKey.isPressed();
        if (lastWasSneaking) lastSneakingY = mc.player.getY();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (onlyOnClick.get() && !mc.options.useKey.isPressed()) return;

        if (airPlace.get()) {
            Vec3d vec = mc.player.getPos().add(mc.player.getVelocity()).add(0, -0.5f, 0);
            bp.set(vec.getX(), vec.getY(), vec.getZ());
        } else if (BlockUtils.getPlaceSide(mc.player.getBlockPos().down()) != null) {
            bp.set(mc.player.getBlockPos().down());
        } else {
            Vec3d pos = mc.player.getPos();
            pos = pos.add(0, -0.98f, 0);
            pos.add(mc.player.getVelocity());

            if (!PlayerUtils.isWithin(prevBp, placeRange.get())) {
                List<BlockPos> blockPosArray = new ArrayList<>();

                for (int x = (int) (mc.player.getX() - placeRange.get()); x < mc.player.getX() + placeRange.get(); x++) {
                    for (int z = (int) (mc.player.getZ() - placeRange.get()); z < mc.player.getZ() + placeRange.get(); z++) {
                        for (int y = (int) Math.max(mc.world.getBottomY(), mc.player.getY() - placeRange.get()); y < Math.min(mc.world.getTopY(), mc.player.getY() + placeRange.get()); y++) {
                            bp.set(x, y, z);
                            if (!mc.world.getBlockState(bp).isAir()) blockPosArray.add(new BlockPos(bp));
                        }
                    }
                }
                if (blockPosArray.size() == 0) {
                    return;
                }

                blockPosArray.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));

                prevBp.set(blockPosArray.get(0));
            }

            Vec3d vecPrevBP = new Vec3d((double) prevBp.getX() + 0.5f,
                (double) prevBp.getY() + 0.5f,
                (double) prevBp.getZ() + 0.5f);

            Vec3d sub = pos.subtract(vecPrevBP);
            Direction facing;
            if (sub.getY() < -0.5f) {
                facing = Direction.DOWN;
            } else if (sub.getY() > 0.5f) {
                facing = Direction.UP;
            } else facing = Direction.getFacing(sub.getX(), 0, sub.getZ());

            bp.set(prevBp.offset(facing));
        }

        // Move down if shifting
        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            if (lastSneakingY - mc.player.getY() < 0.1) {
                lastWasSneaking = false;
                return;
            }
        } else {
            lastWasSneaking = false;
        }
        if (!lastWasSneaking) lastSneakingY = mc.player.getY();

        fastTower(false, null);

        if (airPlace.get()) {
            List<BlockPos> blocks = new ArrayList<>();
            for (int x = (int) (mc.player.getX() - radius.get()); x < mc.player.getX() + radius.get(); x++) {
                for (int z = (int) (mc.player.getZ() - radius.get()); z < mc.player.getZ() + radius.get(); z++) {
                    blocks.add(BlockPos.ofFloored(x, mc.player.getY() - 0.5, z));
                }
            }

            if (!blocks.isEmpty()) {
                blocks.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
                int counter = 0;
                for (BlockPos block : blocks) {
                    if (place(block)) {
                        fastTower(true, block);
                        counter++;
                    }

                    if (counter >= blocksPerTick.get()) {
                        break;
                    }
                }
            }
        } else {
            if (place(bp)) fastTower(true, bp);
            if (!mc.world.getBlockState(bp).isAir()) {
                prevBp.set(bp);
            }
        }
    }

    public boolean scaffolding() {
        return isActive() && (!onlyOnClick.get() || (onlyOnClick.get() && mc.options.useKey.isPressed()));
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem)) return false;

        Block block = ((BlockItem) itemStack.getItem()).getBlock();

        if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) return false;
        else if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) return false;

        if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(mc.world.getBlockState(pos));
    }

    private boolean place(BlockPos bp) {
        FindItemResult item = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        if (!item.found()) return false;

        if (item.getHand() == null && !autoSwitch.get()) return false;

        if (BlockUtils.place(bp, item, rotate.get(), 50, renderSwing.get(), true)) {
            // Render block if was placed
            if (render.get())
                RenderUtils.renderTickingBlock(bp.toImmutable(), sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
            return true;
        }
        return false;
    }

    private void fastTower(boolean down, BlockPos checkBlock) {
        if (down) {
            // Move player down so they are on top of the placed block ready to jump again
            if (fastTower.get() && mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() && !mc.player.isOnGround()) {
                // The chunk hasn't updated yet so we check the block we were standing on
                if (!mc.world.getBlockState(checkBlock.down()).isReplaceable())
                    mc.player.setVelocity(0, -0.28f, 0);
            }
        } else {
            if (mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed() && fastTower.get() && InvUtils.testInHotbar(stack -> validItem(stack, mc.player.getBlockPos()))) {
                Vec3d vel = mc.player.getVelocity();
                mc.player.setVelocity(cancelVelocity.get() ? 0 : vel.x, 0.42, cancelVelocity.get() ? 0 : vel.z);
            }
        }
    }

    public enum ListMode {
        Whitelist,
        Blacklist
    }
}
