package com.system.io.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MyNetty {

    public static void print(ByteBuf buf) {
        System.out.println(buf.isReadable());
        System.out.println(buf.readerIndex());
        System.out.println(buf.readableBytes());
        System.out.println(buf.isWritable());
        System.out.println(buf.writerIndex());
        System.out.println(buf.writableBytes());
        System.out.println(buf.capacity());
        System.out.println(buf.maxCapacity());
        System.out.println(buf.isDirect());
    }

    @Test
    public void loopExecutor() throws IOException {
        //group 线程池
        NioEventLoopGroup selector = new NioEventLoopGroup(2);
        selector.execute(() -> {
            for(;;) {
                System.out.println("hello world001");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        selector.execute(() -> {
            for(;;){
                System.out.println("hello world002");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        System.in.read();
    }

    @Test
    public void clientMode() throws InterruptedException {
        NioEventLoopGroup thread = new NioEventLoopGroup(1);

        //客户端模式：
        NioSocketChannel client = new NioSocketChannel();
        thread.register(client);

        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());

        //reactor 异步的特征
        ChannelFuture connect = client.connect(new InetSocketAddress("192.168.31.69", 9090));
        ChannelFuture sync = connect.sync();
        ByteBuf buf = Unpooled.copiedBuffer("hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        sync.channel().closeFuture().sync();
        System.out.println("client over...");
    }

    @Test
    public void serverMode() throws InterruptedException {
        NioEventLoopGroup  thread = new NioEventLoopGroup(1);
        NioServerSocketChannel server = new NioServerSocketChannel();

        thread.register(server);
        ChannelPipeline p = server.pipeline();
        p.addLast(new MyAcceptHandler(thread, new ChannelInit()));   //accept接受客户端，并且注册到selector
        ChannelFuture bind = server.bind(new InetSocketAddress("192.168.31.69", 9090));

        bind.sync().channel().closeFuture().sync();
        System.out.println("server close...");
    }

    @Test
    public void nettyClient() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(group)
                .channel(NioSocketChannel.class)
//                .handler(new ChannelInit())
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })
                .connect(new InetSocketAddress("192.168.31.69", 9090));

        Channel client = connect.sync().channel();
        ByteBuf buf = Unpooled.copiedBuffer("hello server".getBytes());
        ChannelFuture send = client.writeAndFlush(buf);
        send.sync();

        client.closeFuture().sync();
        System.out.println("client over...");
    }

    @Test
    public void nettyServer() throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ServerBootstrap bs = new ServerBootstrap();
        ChannelFuture bind = bs.group(group, group)
                .channel(NioServerSocketChannel.class)
//                .childHandler(new ChannelInit())
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new MyInHandler());
                    }
                })
                .bind(new InetSocketAddress("192.168.31.69", 9090));

        bind.sync().channel().closeFuture().sync();
    }

}

@ChannelHandler.Sharable
class ChannelInit extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel client = ctx.channel();
        ChannelPipeline p = client.pipeline();
        p.addLast(new MyInHandler());
        ctx.pipeline().remove(this);
    }
}

class MyAcceptHandler extends ChannelInboundHandlerAdapter {
    private final EventLoopGroup selector;
    private final ChannelHandler handler;

    public MyAcceptHandler(EventLoopGroup thread, ChannelHandler myInHandler) {
        this.selector = thread;
        this.handler = myInHandler;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("server registered...");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SocketChannel client = (SocketChannel) msg;
        //1,注册
        selector.register(client);
        //2,响应式的 handler
        ChannelPipeline p = client.pipeline();
        p.addLast(handler);
    }
}

class MyInHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client registered...");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client active...");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
//        CharSequence str = buf.readCharSequence(buf.readableBytes(), CharsetUtil.UTF_8);
        CharSequence str2 = buf.getCharSequence(0, buf.readableBytes(), CharsetUtil.UTF_8);
        System.out.println(str2);
        ctx.writeAndFlush(buf);
    }
}
