package com.system.rpcdemo.rpc;

import com.system.rpcdemo.rpc.util.PackMsg;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description com.system.rpcdemo.rpc
 * @author: chengyu
 * @date: 2022-10-16 13:46
 */
public class ResponseMappingCallback {
    static ConcurrentHashMap<Long, CompletableFuture> mapping = new ConcurrentHashMap<>();

    public static void addCallBack(long requestID, CompletableFuture cf) {
        mapping.putIfAbsent(requestID, cf);
    }

    public static void runCallBack(PackMsg msg) {
        CompletableFuture cf = mapping.get(msg.getHeader().getRequestID());
        cf.complete(msg.getContent().getRes());
        removeCB(msg.getHeader().getRequestID());
    }

    private static void removeCB(long requestID) {
        mapping.remove(requestID);
    }
}
