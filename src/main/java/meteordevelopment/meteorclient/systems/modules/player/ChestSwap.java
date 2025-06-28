/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

public class ChestSwap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Chestplate> chestplate = sgGeneral.add(new EnumSetting.Builder<Chestplate>()
        .name("胸甲")
        .description("要切换到哪种类型的胸甲。")
        .defaultValue(Chestplate.PreferNetherite)
        .build()
    );

    private final Setting<Boolean> stayOn = sgGeneral.add(new BoolSetting.Builder()
        .name("保持开启")
        .description("保持开启并在你关闭时激活。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> closeInventory = sgGeneral.add(new BoolSetting.Builder()
        .name("关闭库存")
        .description("在切换后发送库存关闭。")
        .defaultValue(true)
        .build()
    );

    public ChestSwap() {
        super(Categories.Player, "胸甲切换", "自动在胸甲和鞘翅之间切换。");
    }

    @Override
    public void onActivate() {
        swap();
        if (!stayOn.get()) toggle();
    }

    @Override
    public void onDeactivate() {
        if (stayOn.get()) swap();
    }

    public void swap() {
        ItemStack currentItem = mc.player.getEquippedStack(EquipmentSlot.CHEST);

        if (currentItem.contains(DataComponentTypes.GLIDER)) {
            equipChestplate();
        } else if (currentItem.contains(DataComponentTypes.EQUIPPABLE) && currentItem.get(DataComponentTypes.EQUIPPABLE).slot().getEntitySlotId() == EquipmentSlot.CHEST.getEntitySlotId()) {
            equipElytra();
        } else {
            if (!equipChestplate()) equipElytra();
        }
    }

    private boolean equipChestplate() {
        int bestSlot = -1;
        boolean breakLoop = false;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            Item item = mc.player.getInventory().getMainStacks().get(i).getItem();

            switch (chestplate.get()) {
                case Diamond:
                    if (item == Items.DIAMOND_CHESTPLATE) {
                        bestSlot = i;
                        breakLoop = true;
                    }
                    break;
                case Netherite:
                    if (item == Items.NETHERITE_CHESTPLATE) {
                        bestSlot = i;
                        breakLoop = true;
                    }
                    break;
                case PreferDiamond:
                    if (item == Items.DIAMOND_CHESTPLATE) {
                        bestSlot = i;
                        breakLoop = true;
                    } else if (item == Items.NETHERITE_CHESTPLATE) {
                        bestSlot = i;
                    }
                    break;
                case PreferNetherite:
                    if (item == Items.DIAMOND_CHESTPLATE) {
                        bestSlot = i;
                    } else if (item == Items.NETHERITE_CHESTPLATE) {
                        bestSlot = i;
                        breakLoop = true;
                    }
                    break;
            }

            if (breakLoop) break;
        }

        if (bestSlot != -1) equip(bestSlot);
        return bestSlot != -1;
    }

    private void equipElytra() {
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack item = mc.player.getInventory().getMainStacks().get(i);

            if (item.contains(DataComponentTypes.GLIDER)) {
                equip(i);
                break;
            }
        }
    }

    private void equip(int slot) {
        InvUtils.move().from(slot).toArmor(2);
        if (closeInventory.get()) {
            // Notchian clients send a Close Window packet with Window ID 0 to close their inventory even though there is never an Open Screen packet for the inventory.
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
        }
    }

    @Override
    public void sendToggledMsg() {
        if (stayOn.get()) super.sendToggledMsg();
        else if (Config.get().chatFeedback.get() && chatFeedback) info("触发 (高亮)%s(默认)。", title);
    }

    public enum Chestplate {
        Diamond,
        Netherite,
        PreferDiamond,
        PreferNetherite
    }
}
