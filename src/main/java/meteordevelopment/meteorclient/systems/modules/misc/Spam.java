/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

public class Spam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> messages = sgGeneral.add(new StringListSetting.Builder()
        .name("消息")
        .description("用于垃圾邮件的消息。")
        .defaultValue(List.of("Meteor on Crack!"))
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("延迟")
        .description("指定消息之间的延迟，单位为tick。")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> disableOnLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("离开时禁用")
        .description("离开服务器时禁用垃圾邮件。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("断开时禁用")
        .description("从服务器断开时禁用垃圾邮件。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> random = sgGeneral.add(new BoolSetting.Builder()
        .name("随机")
        .description("从垃圾邮件消息列表中选择一条随机消息。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSplitMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("自动拆分消息")
        .description("在一定长度后自动拆分大消息")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> splitLength = sgGeneral.add(new IntSetting.Builder()
        .name("拆分长度")
        .description("拆分聊天消息的长度")
        .visible(autoSplitMessages::get)
        .defaultValue(256)
        .min(1)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> autoSplitDelay = sgGeneral.add(new IntSetting.Builder()
        .name("拆分延迟")
        .description("拆分消息之间的延迟，单位为tick。")
        .visible(autoSplitMessages::get)
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> bypass = sgGeneral.add(new BoolSetting.Builder()
        .name("绕过")
        .description("在消息末尾添加随机文本，以尝试绕过反垃圾邮件。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> uppercase = sgGeneral.add(new BoolSetting.Builder()
        .name("包含大写字符")
        .description("绕过文本是否应包含大写字符。")
        .visible(bypass::get)
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> length = sgGeneral.add(new IntSetting.Builder()
        .name("长度")
        .description("用于绕过反垃圾邮件的字符数。")
        .visible(bypass::get)
        .defaultValue(16)
        .sliderRange(1, 256)
        .build()
    );

    private int messageI, timer, splitNum;
    private String text;

    public Spam() {
        super(Categories.Misc, "垃圾邮件", "在聊天中发送指定的垃圾邮件消息。");
    }

    @Override
    public void onActivate() {
        timer = delay.get();
        messageI = 0;
        splitNum = 0;
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (disableOnDisconnect.get() && event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (disableOnLeave.get()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (messages.get().isEmpty()) return;

        if (timer <= 0) {
            if (text == null) {
                int i;
                if (random.get()) {
                    i = Utils.random(0, messages.get().size());
                } else {
                    if (messageI >= messages.get().size()) messageI = 0;
                    i = messageI++;
                }

                text = messages.get().get(i);
                if (bypass.get()) {
                    String bypass = RandomStringUtils.randomAlphabetic(length.get());
                    if (!uppercase.get()) bypass = bypass.toLowerCase();

                    text += " " + bypass;
                }
            }

            if (autoSplitMessages.get() && text.length() > splitLength.get()) {
                // the number of individual messages the whole text needs to be broken into
                double length = text.length();
                int splits = (int) Math.ceil(length / splitLength.get());

                // determine which chunk we need to send
                int start = splitNum * splitLength.get();
                int end = Math.min(start + splitLength.get(), text.length());
                ChatUtils.sendPlayerMsg(text.substring(start, end));

                splitNum = ++splitNum % splits;
                timer = autoSplitDelay.get();
                if (splitNum == 0) { // equals zero when all chunks are sent
                    timer = delay.get();
                    text = null;
                }
            } else {
                if (text.length() > 256) text = text.substring(0, 256); // prevent kick
                ChatUtils.sendPlayerMsg(text);
                timer = delay.get();
                text = null;
            }
        } else {
            timer--;
        }
    }
}
