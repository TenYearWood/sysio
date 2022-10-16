package com.system.rpcdemo;

import com.system.io.netty.SerDerUtil;
import com.system.rpcdemo.proxy.MyProxy;
import com.system.rpcdemo.rpc.Dispatcher;
import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.protocol.MyHeader;
import com.system.rpcdemo.rpc.service.Car;
import com.system.rpcdemo.rpc.service.Fly;
import com.system.rpcdemo.rpc.service.MyCar;
import com.system.rpcdemo.rpc.service.MyFly;
import com.system.rpcdemo.rpc.service.Person;
import com.system.rpcdemo.rpc.transport.ServerDecode;
import com.system.rpcdemo.rpc.transport.ServerRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1，先假设一个需求，写一个RPC
 * 2，来回通信，连接数量，拆包？
 * 3，动态代理呀，序列化，协议封装
 * 4，连接池
 * 5，就像调用本地方法一样去调用远程的方法，面向java中就是所谓的面向interface开发。
 */
public class MyRPCTest {

    @Test
    public void startServer() {
        MyCar car = new MyCar();
        MyFly fly = new MyFly();
        Dispatcher dis = Dispatcher.getDis();
        dis.register(Car.class.getName(), car);
        dis.register(Fly.class.getName(), fly);

        NioEventLoopGroup boss = new NioEventLoopGroup(20);
        NioEventLoopGroup worker = boss;

        ServerBootstrap sbs = new ServerBootstrap();
        ChannelFuture bind = sbs.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        System.out.println("server accept client port: " + ch.remoteAddress().getPort());
                        ChannelPipeline p = ch.pipeline();

                        //1.自定义的rpc
                        //p.addLast(new ServerDecode());
                        //p.addLast(new ServerRequestHandler(dis));
                        //在自己定义协议的时候关注过哪些问题：粘包拆包，header+body，

                        //2.小火车，传输协议用的就是http了， <- 你可以自己学，自己解码byte[]
                        //其实，netty提供了一套编解码
                        p.addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1024 * 512))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        //http协议，这个msg是啥：完成的http-request
                                        FullHttpRequest request = (FullHttpRequest) msg;
                                        System.out.println(request.toString()); //因为现在consumer使用的是一个现成的URL

                                        //这个就是consumer 序列化的MyContent
                                        ByteBuf content = request.content();
                                        byte[] data = new byte[content.readableBytes()];
                                        content.readBytes(data);
                                        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(data));
                                        MyContent myContent = (MyContent) oin.readObject();

                                        String serviceName = myContent.getName();
                                        String method = myContent.getMethodName();
                                        Object c = dis.get(serviceName);
                                        Class<?> clazz = c.getClass();
                                        Object res = null;
                                        try {
                                            Method m = clazz.getMethod(method, myContent.getParameterTypes());
                                            res = m.invoke(c, myContent.getArgs());
                                        } catch (NoSuchMethodException e) {
                                            e.printStackTrace();
                                        } catch (IllegalAccessException e) {
                                            e.printStackTrace();
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        }

                                        MyContent resContent = new MyContent();
                                        resContent.setRes(res);
                                        byte[] contentByte = SerDerUtil.serialize(resContent);

                                        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0,
                                                HttpResponseStatus.OK, Unpooled.copiedBuffer(contentByte));

                                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentByte.length);

                                        //http协议 header+body
                                        ctx.writeAndFlush(response);
                                    }
                                });

                    }
                }).bind(new InetSocketAddress("localhost", 9090));

        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void startHttpServer() {
        MyCar car = new MyCar();
        MyFly fly = new MyFly();
        Dispatcher dis = Dispatcher.getDis();
        dis.register(Car.class.getName(), car);
        dis.register(Fly.class.getName(), fly);


        //tomcat jetty servlet
        Server server = new Server(new InetSocketAddress("localhost", 9090));
        ServletContextHandler handler = new ServletContextHandler(server, "/");
        server.setHandler(handler);
        handler.addServlet(MyHttpRpcHandler.class, "/*");   //类似web.xml的配置

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟consumer端
     */
    @Test
    public void get() {
        new Thread(() -> startServer()).start();
        System.out.println("server started...");

        AtomicInteger num = new AtomicInteger(0);
        int size = 20;
        Thread[] threads = new Thread[size];
        for (int i = 0; i < size; i++) {
            threads[i] = new Thread(() -> {
                Car car = MyProxy.proxyGet(Car.class);  //动态代理实现
                String arg = "hello" + num.incrementAndGet();
                String res = car.ooxx(arg);
                System.out.println("client over msg: " + res + " src arg: " + arg);
            });
        }
        for (Thread thread : threads) {
            thread.start();
        }

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRPC() {
        Car car = MyProxy.proxyGet(Car.class);
        Person zhangsan = car.oxox("zhangsan", 16);
        System.out.println(zhangsan);
    }

    @Test
    public void testRpcLocal() {
        new Thread(() -> startServer()).start();
        System.out.println("server started...");

        Car car = MyProxy.proxyGet(Car.class);
        Person zhangsan = car.oxox("zhangsan", 16);
        System.out.println(zhangsan);
    }

    private class MyHttpRpcHandler extends HttpServlet {

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            ServletInputStream in = req.getInputStream();
            ObjectInputStream oin = new ObjectInputStream(in);
            try {
                MyContent myContent = (MyContent) oin.readObject();
                String serviceName = myContent.getName();
                String method = myContent.getMethodName();
                Object c = Dispatcher.getDis().get(serviceName);
                Class<?> clazz = c.getClass();
                Object res = null;
                try {
                    Method m = clazz.getMethod(method, myContent.getParameterTypes());
                    res = m.invoke(c, myContent.getArgs());
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }

                MyContent resContent = new MyContent();
                resContent.setRes(res);

                ServletOutputStream out = resp.getOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(resContent);

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}




