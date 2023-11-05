/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MixinPlugin;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.block.Block;

import java.util.List;

public class WallHack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("渲染块的不透明度。")
        .defaultValue(0)
        .range(0, 255)
        .sliderMax(255)
        .onChanged(onChanged -> {
            if (this.isActive()) {
                mc.worldRenderer.reload();
            }
        })
        .build()
    );

    public final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("哪些块应该成为 Wall Hack 的目标。")
        .defaultValue()
        .onChanged(onChanged -> {
            if (isActive()) mc.worldRenderer.reload();
        })
        .build()
    );

    public final Setting<Boolean> occludeChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("occlusion-chunks")
        .description("洞穴是否应该遮挡地下(打开时可能看起来很奇怪)。 ")
        .defaultValue(false)
        .build()
    );

    public WallHack() {
        super(Categories.Render, "wall-hack", "使块半透明。");
    }

    @Override
    public void onActivate() {
        mc.worldRenderer.reload();
    }

    @Override
    public void onDeactivate() {
        mc.worldRenderer.reload();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        if (MixinPlugin.isSodiumPresent) return theme.label("警告：由于使用了钠,不透明度被覆盖为 0。");
        if (MixinPlugin.isIrisPresent && IrisApi.getInstance().isShaderPackInUse()) return theme.label("警告：由于使用了着色器,不透明度被覆盖为 0。");

        return null;
    }

    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        if (!occludeChunks.get()) event.cancel();
    }
}
