package com.system.rpcdemo.rpc.transport;

import com.system.rpcdemo.rpc.util.PackMsg;
import com.system.rpcdemo.rpc.ResponseMappingCallback;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * @description com.system.rpcdemo.rpc.transport
 * @author: chengyu
 * @date: 2022-10-16 14:28
 */
public class ClientResponses extends ChannelInboundHandlerAdapter {

    //consumser
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        PackMsg responePkg = (PackMsg) msg;

        /**
         * 曾经我们没有考虑返回的事，
         */
        ResponseMappingCallback.runCallBack(responePkg);
    }
}
