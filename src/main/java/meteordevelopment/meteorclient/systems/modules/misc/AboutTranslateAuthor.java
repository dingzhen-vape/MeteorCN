/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;


import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;

import meteordevelopment.meteorclient.utils.misc.text.RunnableClickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.awt.desktop.OpenURIEvent;
import java.net.URI;

public class AboutTranslateAuthor extends Module {
    public AboutTranslateAuthor() {
        super(Categories.Misc, "食我压路汉化", "耶。");
    }

    @Override
    public void onActivate() {
        URI uri = URI.create("https://space.bilibili.com/14191622");
        Text text = Text.of(uri.toString());
        ChatUtils.sendMsg(text);
    }
}
