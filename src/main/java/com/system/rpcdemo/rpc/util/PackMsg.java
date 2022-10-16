package com.system.rpcdemo.rpc.util;

import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.protocol.MyHeader;

/**
 * @description com.system.rpcdemo.rpc
 * @author: chengyu
 * @date: 2022-10-16 13:50
 */
public class PackMsg {
    MyHeader header;
    MyContent content;

    public PackMsg(MyHeader header, MyContent content) {
        this.header = header;
        this.content = content;
    }

    public MyHeader getHeader() {
        return header;
    }

    public void setHeader(MyHeader header) {
        this.header = header;
    }

    public MyContent getContent() {
        return content;
    }

    public void setContent(MyContent content) {
        this.content = content;
    }
}
