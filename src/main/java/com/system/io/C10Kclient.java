package com.system.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * @description com.system.io
 * @author: chengyu
 * @date: 2022-09-09 14:20
 */
public class C10Kclient {
    public static void main(String[] args) {
        LinkedList<SocketChannel> clients = new LinkedList<>();
        InetSocketAddress serverAddr = new InetSocketAddress("192.168.150.11", 9090);

        for (int i = 10000; i < 65000; i++) {
            try {
                SocketChannel client1 = SocketChannel.open();
                SocketChannel client2 = SocketChannel.open();

                client1.bind(new InetSocketAddress("192.168.150.1", i));
                client1.connect(serverAddr);
                boolean c1 = client1.isOpen();
                clients.add(client1);

                client2.bind(new InetSocketAddress("192.168.110.100", i));
                client2.connect(serverAddr);
                boolean c2 = client2.isOpen();
                clients.add(client2);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("clients size :" + clients.size());
    }
}
