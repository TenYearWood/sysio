package com.system.rpcdemo.rpc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @description com.system.rpcdemo.rpc
 * @author: chengyu
 * @date: 2022-10-16 11:47
 */
public class Dispatcher {
    private static Dispatcher dis = new Dispatcher();

    public static Dispatcher getDis() {
        return dis;
    }

    private Dispatcher() {}

    public static ConcurrentHashMap<String, Object> invokeMap = new ConcurrentHashMap<>();

    public void register(String k, Object obj) {
        invokeMap.put(k, obj);
    }

    public Object get(String k) {
        return invokeMap.get(k);
    }
}
