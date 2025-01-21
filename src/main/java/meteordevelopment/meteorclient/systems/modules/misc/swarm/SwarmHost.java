/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SwarmHost extends Thread {
    private ServerSocket socket;
    private final SwarmConnection[] clientConnections = new SwarmConnection[50];

    public SwarmHost(int port) {
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            socket = null;
            ChatUtils.errorPrefix("Swarm", "无法在端口%s上启动服务器。", port);
            e.printStackTrace();
        }

        if (socket != null) start();
    }

    @Override
    public void run() {
        ChatUtils.infoPrefix("Swarm", "在端口%s上监听传入的连接。", socket.getLocalPort());

        while (!isInterrupted()) {
            try {
                Socket connection = socket.accept();
                assignConnectionToSubServer(connection);
            } catch (IOException e) {
                ChatUtils.errorPrefix("Swarm", "与工作节点建立连接时出错。");
                e.printStackTrace();
            }
        }
    }

    public void assignConnectionToSubServer(Socket connection) {
        for (int i = 0; i < clientConnections.length; i++) {
            if (this.clientConnections[i] == null) {
                this.clientConnections[i] = new SwarmConnection(connection);
                break;
            }
        }
    }

    public void disconnect() {
        for (SwarmConnection connection : clientConnections) {
            if (connection != null) connection.disconnect();
        }

        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ChatUtils.infoPrefix("Swarm", "在端口%s上的服务器关闭。", socket.getLocalPort());

        interrupt();
    }

    public void sendMessage(String s) {
        MeteorExecutor.execute(() -> {
            for (SwarmConnection connection : clientConnections) {
                if (connection != null) {
                    connection.messageToSend = s;
                }
            }
        });
    }

    public SwarmConnection[] getConnections() {
        return clientConnections;
    }

    public int getConnectionCount() {
        int count = 0;

        for (SwarmConnection clientConnection : clientConnections) {
            if (clientConnection != null) count++;
        }

        return count;
    }
}
