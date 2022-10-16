package com.system.rpcdemo.rpc.transport;

import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * @description com.system.rpcdemo.rpc.transport
 * @author: chengyu
 * @date: 2022-10-16 13:09
 */
public class ClientPool {
    NioSocketChannel[] clients;
    Object[] lock;

    ClientPool(int size) {
        clients = new NioSocketChannel[size];   //init 连接都是空的
        lock = new Object[size];    //锁是可以初始化的
        for (int i = 0; i < size; i++) {
            lock[i] = new Object();
        }
    }
}
