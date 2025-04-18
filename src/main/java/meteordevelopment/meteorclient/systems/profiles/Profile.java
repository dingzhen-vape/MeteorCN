/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.profiles;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.macros.Macros;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class Profile implements ISerializable<Profile> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSave = settings.createGroup("保存");

    public Setting<String> name = sgGeneral.add(new StringSetting.Builder()
        .name("name")
        .description("配置文件的名称。")
        .filter(Utils::nameFilter)
        .build()
    );

    public Setting<List<String>> loadOnJoin = sgGeneral.add(new StringListSetting.Builder()
        .name("加入时加载")
        .description("加入时在哪些服务器上设置此配置文件为激活状态。")
        .filter(Utils::ipFilter)
        .build()
    );

    public Setting<Boolean> hud = sgSave.add(new BoolSetting.Builder()
        .name("界面")
        .description("配置文件是否应保存界面。")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> macros = sgSave.add(new BoolSetting.Builder()
        .name("宏")
        .description("配置文件是否应保存宏命令。")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> modules = sgSave.add(new BoolSetting.Builder()
        .name("modules")
        .description("配置文件是否应保存模块。")
        .defaultValue(false)
        .build()
    );

    public Setting<Boolean> waypoints = sgSave.add(new BoolSetting.Builder()
        .name("创建路标")
        .description("配置文件是否应保存导航点。")
        .defaultValue(false)
        .build()
    );

    public Profile() {}
    public Profile(NbtElement tag) {
        fromTag((NbtCompound) tag);
    }

    public void load() {
        File folder = getFile();

        if (hud.get()) Hud.get().load(folder);
        if (macros.get()) Macros.get().load(folder);
        if (modules.get()) Modules.get().load(folder);
        if (waypoints.get()) Waypoints.get().load(folder);
    }

    public void save() {
        File folder = getFile();

        if (hud.get()) Hud.get().save(folder);
        if (macros.get()) Macros.get().save(folder);
        if (modules.get()) Modules.get().save(folder);
        if (waypoints.get()) Waypoints.get().save(folder);
    }

    public void delete() {
        try {
            FileUtils.deleteDirectory(getFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getFile() {
        return new File(Profiles.FOLDER, name.get());
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public Profile fromTag(NbtCompound tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompoundOrEmpty("settings"));
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Profile profile = (Profile) o;
        return Objects.equals(profile.name.get(), this.name.get());
    }
}
