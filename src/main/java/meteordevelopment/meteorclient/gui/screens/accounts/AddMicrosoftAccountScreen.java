/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.screens.accounts;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.accounts.MicrosoftLogin;
import meteordevelopment.meteorclient.systems.accounts.types.MicrosoftAccount;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AddMicrosoftAccountScreen extends AddAccountScreen {
    public AddMicrosoftAccountScreen(GuiTheme theme, AccountsScreen parent) {
        super(theme, "添加微软账户", parent);
    }

    @Override
    public void initWidgets() {
        String url = MicrosoftLogin.getRefreshToken(refreshToken -> {

            if (refreshToken != null) {
                MicrosoftAccount account = new MicrosoftAccount(refreshToken);
                AccountsScreen.addAccount(null, parent, account);
            }

            close();
        });

        add(theme.label("请在浏览器中选择要登录的账户。"));
        add(theme.label("If the link does not automatically open in a few seconds, copy it into your browser."));

        WHorizontalList l = add(theme.horizontalList()).expandX().widget();

        WButton copy = l.add(theme.button("Copy link")).expandX().widget();
        copy.action = () -> mc.keyboard.setClipboard(url);

        WButton cancel = l.add(theme.button("取消")).expandX().widget();
        cancel.action = () -> {
            MicrosoftLogin.stopServer();
            close();
        };
    }

    @Override
    public void tick() {}

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
