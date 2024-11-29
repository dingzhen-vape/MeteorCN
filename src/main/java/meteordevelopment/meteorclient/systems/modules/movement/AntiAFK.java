/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;

import java.util.List;
import java.util.Random;

public class AntiAFK extends Module {
    private final SettingGroup sgActions = settings.createGroup("动作");
    private final SettingGroup sgMessages = settings.createGroup("消息");

    // Actions

    private final Setting<Boolean> jump = sgActions.add(new BoolSetting.Builder()
        .name("跳跃")
        .description("随机跳跃。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgActions.add(new BoolSetting.Builder()
        .name("挥动")
        .description("挥动你的手。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> sneak = sgActions.add(new BoolSetting.Builder()
        .name("潜行")
        .description("快速地潜行和取消潜行。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> sneakTime = sgActions.add(new IntSetting.Builder()
        .name("潜行时间")
        .description("保持潜行的刻数。")
        .defaultValue(5)
        .min(1)
        .sliderMin(1)
        .visible(sneak::get)
        .build()
    );

    private final Setting<Boolean> strafe = sgActions.add(new BoolSetting.Builder()
        .name("扫射")
        .description("左右扫射。")
        .defaultValue(false)
        .onChanged(aBoolean -> {
            strafeTimer = 0;
            direction = false;

            if (isActive()) {
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
            }
        })
        .build()
    );

    private final Setting<Boolean> spin = sgActions.add(new BoolSetting.Builder()
        .name("旋转")
        .description("在原地旋转玩家。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SpinMode> spinMode = sgActions.add(new EnumSetting.Builder<SpinMode>()
        .name("旋转模式")
        .description("旋转的方法。")
        .defaultValue(SpinMode.Server)
        .visible(spin::get)
        .build()
    );

    private final Setting<Integer> spinSpeed = sgActions.add(new IntSetting.Builder()
        .name("速度")
        .description("旋转你的速度。")
        .defaultValue(7)
        .visible(spin::get)
        .build()
    );

    private final Setting<Integer> pitch = sgActions.add(new IntSetting.Builder()
        .name("俯仰角")
        .description("发送到服务器的俯仰角。")
        .defaultValue(0)
        .range(-90, 90)
        .sliderRange(-90, 90)
        .visible(() -> spin.get() && spinMode.get() == SpinMode.Server)
        .build()
    );


    // Messages

    private final Setting<Boolean> sendMessages = sgMessages.add(new BoolSetting.Builder()
        .name("发送消息")
        .description("发送消息以防止因为AFK而被踢出。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> randomMessage = sgMessages.add(new BoolSetting.Builder()
        .name("随机")
        .description("从你的消息列表中选择一个随机消息。")
        .defaultValue(false)
        .visible(sendMessages::get)
        .build()
    );

    private final Setting<Integer> delay = sgMessages.add(new IntSetting.Builder()
        .name("延迟")
        .description("以秒为单位指定消息之间的延迟。")
        .defaultValue(15)
        .min(0)
        .sliderMax(30)
        .visible(sendMessages::get)
        .build()
    );

    private final Setting<List<String>> messages = sgMessages.add(new StringListSetting.Builder()
        .name("消息")
        .description("可供选择的消息。")
        .defaultValue(
            "Meteor on top!",
            "Meteor on crack!"
        )
        .visible(sendMessages::get)
        .build()
    );

    public AntiAFK() {
        super(Categories.Player, "防AFK", "执行不同的动作以防止在AFK时被踢出。");
    }

    private final Random random = new Random();
    private int messageTimer = 0;
    private int messageI = 0;
    private int sneakTimer = 0;
    private int strafeTimer = 0;
    private boolean direction = false;
    private float prevYaw;

    @Override
    public void onActivate() {
        if (sendMessages.get() && messages.get().isEmpty()) {
            warning("消息列表为空，关闭消息...");
            sendMessages.set(false);
        }

        prevYaw = mc.player.getYaw();
        messageTimer = delay.get() * 20;
    }

    @Override
    public void onDeactivate() {
        if (strafe.get()) {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        // Jump
        if (jump.get()) {
            if (mc.options.jumpKey.isPressed()) mc.options.jumpKey.setPressed(false);
            else if (random.nextInt(99) == 0) mc.options.jumpKey.setPressed(true);
        }

        // Swing
        if (swing.get() && random.nextInt(99) == 0) {
            mc.player.swingHand(mc.player.getActiveHand());
        }

        // Sneak
        if (sneak.get()) {
            if (sneakTimer++ >= sneakTime.get()) {
                mc.options.sneakKey.setPressed(false);
                if (random.nextInt(99) == 0) sneakTimer = 0; // Sneak after ~5 seconds
            } else mc.options.sneakKey.setPressed(true);
        }

        // Strafe
        if (strafe.get() && strafeTimer-- <= 0) {
            mc.options.leftKey.setPressed(!direction);
            mc.options.rightKey.setPressed(direction);
            direction = !direction;
            strafeTimer = 20;
        }

        // Spin
        if (spin.get()) {
            prevYaw += spinSpeed.get();
            switch (spinMode.get()) {
                case Client -> mc.player.setYaw(prevYaw);
                case Server -> Rotations.rotate(prevYaw, pitch.get(), -15);
            }
        }

        // Messages
        if (sendMessages.get() && !messages.get().isEmpty() && messageTimer-- <= 0) {
            if (randomMessage.get()) messageI = random.nextInt(messages.get().size());
            else if (++messageI >= messages.get().size()) messageI = 0;

            ChatUtils.sendPlayerMsg(messages.get().get(messageI));
            messageTimer = delay.get() * 20;
        }
    }

    public enum SpinMode {
        Server,
        Client
    }
}
