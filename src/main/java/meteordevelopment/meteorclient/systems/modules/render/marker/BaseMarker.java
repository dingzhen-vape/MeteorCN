/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.marker;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.screens.MarkerScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.NbtCompound;

public abstract class BaseMarker implements ISerializable<BaseMarker> {
    public final Settings settings = new Settings();

    protected final SettingGroup sgBase = settings.createGroup("基础");

    public final Setting<String> name = sgBase.add(new StringSetting.Builder()
        .name("name")
        .description("这个标记的自定义名称。")
        .defaultValue("")
        .build()
    );

    protected final Setting<String> description = sgBase.add(new StringSetting.Builder()
        .name("description")
        .description("这个标记的自定义描述。")
        .defaultValue("")
        .build()
    );

    private final Setting<Dimension> dimension = sgBase.add(new EnumSetting.Builder<Dimension>()
        .name("dimension")
        .description("这个标记应该在哪个维度可见。")
        .defaultValue(Dimension.Overworld)
        .build()
    );

    private final Setting<Boolean> active = sgBase.add(new BoolSetting.Builder()
        .name("active")
        .description("这个标记是否可见。")
        .defaultValue(false)
        .build()
    );

    public BaseMarker(String name) {
        this.name.set(name);

        dimension.set(PlayerUtils.getDimension());
    }

    protected void render(Render3DEvent event) {}

    protected void tick() {}

    public Screen getScreen(GuiTheme theme) {
        return new MarkerScreen(theme, this);
    }

    public WWidget getWidget(GuiTheme theme) {
        return null;
    }

    public String getName() {
        return name.get();
    }

    public String getTypeName() {
        return null;
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean isVisible() {
        return isActive() && PlayerUtils.getDimension() == dimension.get();
    }

    public Dimension getDimension() {
        return dimension.get();
    }

    public void toggle() {
        active.set(!active.get());
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.put("settings", settings.toTag());
        return tag;
    }

    @Override
    public BaseMarker fromTag(NbtCompound tag) {
        NbtCompound settingsTag = (NbtCompound) tag.get("settings");
        if (settingsTag != null) settings.fromTag(settingsTag);

        return this;
    }
}
