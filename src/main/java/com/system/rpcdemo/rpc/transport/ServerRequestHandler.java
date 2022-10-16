package com.system.rpcdemo.rpc.transport;

import com.system.io.netty.SerDerUtil;
import com.system.rpcdemo.rpc.Dispatcher;
import com.system.rpcdemo.rpc.util.PackMsg;
import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.protocol.MyHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @description com.system.rpcdemo.rpc.transport
 * @author: chengyu
 * @date: 2022-10-16 14:24
 */
public class ServerRequestHandler extends ChannelInboundHandlerAdapter {
    Dispatcher dis;

    public ServerRequestHandler(Dispatcher dis) {
        this.dis = dis;
    }

    //provider

    /**
     * channelRead读报文，可能不是一条，可能是多条混在一起
     * <p>
     * 通过netty channel read的时候不能保证数据的完整性。
     * 而且，不是一次read处理一个message，可能处理多条message。
     * 但是两次read之间一定会保证数据的完整性。
     * <p>
     * 那么怎么解决呢？
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        PackMsg requestPkg = (PackMsg) msg;
        System.out.println("server handler method: " + requestPkg.getContent().getMethodName() + " args: " + requestPkg.getContent().getArgs()[0]);

        /**
         * 假设处理完了，要给客户端返回了。需要注意那些环节？
         * 1.因为是rpc嘛，你得有requestID
         * 2，在client那一侧也要解决解码问题
         * 3.关注rpc通信协议，来的时候flag 0x14141414
         * 4.有新的header + content
         * 5.通信有没有来回错位？
         */
        String ioThreadName = Thread.currentThread().getName();
        //1.直接在当前方法 处理IO和业务和返回
        //2.使用netty自己的eventloop来处理业务及返回
        //3.自己创建线程池
        //ctx.executor().execute(() -> {       //拿自己的线程池去执行
        ctx.executor().parent().next().execute(() -> {   //从线程池的父亲eventLoopGroup，next
            String serviceName = requestPkg.getContent().getName();
            String method = requestPkg.getContent().getMethodName();
            Object c = dis.get(serviceName);
            Class<?> clazz = c.getClass();
            Object res = null;
            try {
                Method m = clazz.getMethod(method, requestPkg.getContent().getParameterTypes());
                res = m.invoke(c, requestPkg.getContent().getArgs());
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }


            //String execThreadName = Thread.currentThread().getName();
            MyContent content = new MyContent();
            //String s = "io thread: " + ioThreadName + "exec thread: " + execThreadName + "from args: " + requestPkg.content.args[0];
            content.setRes((String) res);
            byte[] contentByte = SerDerUtil.serialize(content);

            MyHeader resHeader = new MyHeader();
            resHeader.setRequestID(requestPkg.getHeader().getRequestID());
            resHeader.setFlag(0x14141424);
            resHeader.setDataLen(contentByte.length);
            byte[] headerByte = SerDerUtil.serialize(resHeader);

            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(headerByte.length + contentByte.length);
            byteBuf.writeBytes(headerByte);
            byteBuf.writeBytes(contentByte);
            ctx.writeAndFlush(byteBuf);
        });
    }
}

