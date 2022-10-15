package com.system.io.netty;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * @description com.system.io.netty
 * @author: chengyu
 * @date: 2022-10-14 23:00
 */
public class SerDerUtil {

    static ByteArrayOutputStream out = new ByteArrayOutputStream();

    /**
     * java序列化为什么header长度不一样？
     * 1.包的全路径名称不一样
     * 2.
     * @param msg
     * @return
     */
    public synchronized static byte[] serialize(Object msg) {
        out.reset();
        ObjectOutputStream oout;
        byte[] msgBody = null;
        try {
            oout = new ObjectOutputStream(out);
            oout.writeObject(msg);
            msgBody = out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return msgBody;
    }
}
