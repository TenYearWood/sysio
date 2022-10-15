package com.system.io.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
                        p.addLast(new ServerDecode());
                        p.addLast(new ServerRequestHandler());
                    }
                }).bind(new InetSocketAddress("localhost", 9090));

        try {
            bind.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
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
                Car car = proxyGet(Car.class);  //动态代理实现
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

    public static <T> T proxyGet(Class<T> interfaceInfo) {
        //实现各个版本的动态代理。。。
        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};

        return (T) Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                //如何设计我们的consumer对于provider的调用过程

                //1，调用服务，方法，参数 ---> 封装成message [content]
                String name = interfaceInfo.getName();
                String methodName = method.getName();
                Class<?>[] parameterTypes = method.getParameterTypes();

                MyContent content = new MyContent();
                content.setArgs(args);
                content.setMethodName(methodName);
                content.setName(name);
                content.setParameterTypes(parameterTypes);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(out);
                oout.writeObject(content);
                byte[] msgBody = out.toByteArray();

                //2，requestID + message，本地要缓存
                //协议：【header<>】 【msgBody】
                MyHeader header = createHeader(msgBody);
                out.reset();
                oout = new ObjectOutputStream(out);
                oout.writeObject(header);
                byte[] msgHeader = out.toByteArray();

                //3，连接池，取得连接
                ClientFactory factory = ClientFactory.getFactory();
                NioSocketChannel clientChannel = factory.getClient(new InetSocketAddress("localhost", 9090));

                //4，发送，走IO out ---> 走Netty(event驱动)
                long id = header.getRequestID();
                CompletableFuture<String> res = new CompletableFuture<>();
                ResponseMappingCallback.addCallBack(id, res);
                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(msgHeader.length + msgBody.length);
                byteBuf.writeBytes(msgHeader);
                byteBuf.writeBytes(msgBody);
                ChannelFuture channelFuture = clientChannel.writeAndFlush(byteBuf);
                channelFuture.sync();   //io是双向的，你看似有个sync，她仅代表out

                //5，？ 如果从IO，未来回来了，怎么将代码执行到这里
                //(睡眠/回调，如何让线程停下来？你还能让他继续。。。)

                return res.get();   //阻塞的，一直到被执行为止
            }
        });
    }

    public static MyHeader createHeader(byte[] msgBody) {
        MyHeader header = new MyHeader();
        int size = msgBody.length;
        int f = 0X14141414;
        long requestID = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        //0x14 0001 0100
        header.setFlag(f);
        header.setDataLen(size);
        header.setRequestID(requestID);
        return header;
    }
}

class ServerDecode extends ByteToMessageDecoder {

    /**
     * 父类里一定有channelRead{ 前老的拼buf decode() ; 剩余留存； 对out遍历 } -> bytebuf
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        System.out.println("channel start : " + buf.readableBytes());

        /**
         * buf.readBytes指针是向右移动的
         * getBytes不会移动指针
         */
        while (buf.readableBytes() >= 110) {    //说明报文头是有了
            byte[] bytes = new byte[110];
            buf.getBytes(buf.readerIndex(), bytes); //从哪里读取，读多少，但是readIndex不变。
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            ObjectInputStream oin = new ObjectInputStream(in);
            MyHeader header = (MyHeader) oin.readObject();
            System.out.println("server response @ id: " + header.getRequestID());

            /**
             * decode在2个方向（client和server）都使用
             * 通信的协议
             */
            if (buf.readableBytes() >= header.getDataLen()) {
                buf.readBytes(110);
                byte[] data = new byte[(int) header.getDataLen()];
                buf.readBytes(data);
                ByteArrayInputStream din = new ByteArrayInputStream(data);
                ObjectInputStream odin = new ObjectInputStream(din);

                if (header.flag == 0x14141414) {     //客户端向服务端发送方向
                    MyContent content = (MyContent) odin.readObject();
                    System.out.println(content.getName());  //打印接口名称
                    out.add(new PackMsg(header, content));
                } else if (header.flag == 0x14141424) {  //服务端返回了
                    MyContent content = (MyContent) odin.readObject();
                    out.add(new PackMsg(header, content));
                }
            } else {
                break;      //最后一段报文不完整，直接break跳出循环。留存的buf拼接到下一个报文里
            }
        }
    }
}

class ServerRequestHandler extends ChannelInboundHandlerAdapter {
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
        System.out.println("server handler method: " + requestPkg.content.methodName + " args: " + requestPkg.content.args[0]);

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
            String execThreadName = Thread.currentThread().getName();
            MyContent content = new MyContent();
            String s = "io thread: " + ioThreadName + "exec thread: " + execThreadName + "from args: " + requestPkg.content.args[0];
            System.out.println(s);
            content.setRes(s);
            byte[] contentByte = SerDerUtil.serialize(content);

            MyHeader resHeader = new MyHeader();
            resHeader.setRequestID(requestPkg.header.requestID);
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

class ClientFactory {
    int poolSize = 1;
    NioEventLoopGroup clientWorker;
    Random rand = new Random();
    private static final ClientFactory factory;

    static {
        factory = new ClientFactory();
    }

    private ClientFactory() {
    }

    public static ClientFactory getFactory() {
        return factory;
    }

    //一个consumer可以连接很多的provider，每一个provider都有自己的pool K V
    ConcurrentHashMap<InetSocketAddress, ClientPool> outboxs = new ConcurrentHashMap<>();

    public synchronized NioSocketChannel getClient(InetSocketAddress address) {
        ClientPool clientPool = outboxs.get(address);
        if (null == clientPool) {
            outboxs.putIfAbsent(address, new ClientPool(poolSize));
            clientPool = outboxs.get(address);
        }

        int i = rand.nextInt(poolSize);
        if (clientPool.clients[i] != null && clientPool.clients[i].isActive()) {
            return clientPool.clients[i];
        }

        synchronized (clientPool.lock[i]) {
            return clientPool.clients[i] = create(address);
        }
    }

    private NioSocketChannel create(InetSocketAddress address) {
        //基于netty的客户端创建方式
        clientWorker = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        ChannelFuture connect = bs.group(clientWorker)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ServerDecode());
                        p.addLast(new ClientResponses());
                    }
                }).connect(address);
        try {
            NioSocketChannel client = (NioSocketChannel) connect.sync().channel();
            return client;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}

class ClientPool {
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

class ResponseMappingCallback {
    static ConcurrentHashMap<Long, CompletableFuture> mapping = new ConcurrentHashMap<>();

    public static void addCallBack(long requestID, CompletableFuture cf) {
        mapping.putIfAbsent(requestID, cf);
    }

    public static void runCallBack(PackMsg msg) {
        CompletableFuture cf = mapping.get(msg.header.requestID);
        cf.complete(msg.content.res);
        removeCB(msg.header.requestID);
    }

    private static void removeCB(long requestID) {
        mapping.remove(requestID);
    }
}

class ClientResponses extends ChannelInboundHandlerAdapter {

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

class MyHeader implements Serializable {
    /**
     * 通信上的协议
     * 1.ooxx值
     * 2.UUID:requestID
     * 3.DATA_LEN
     */

    int flag;   //32bit可以设置很多信息
    long requestID;
    long dataLen;

    public int getFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public long getRequestID() {
        return requestID;
    }

    public void setRequestID(long requestID) {
        this.requestID = requestID;
    }

    public long getDataLen() {
        return dataLen;
    }

    public void setDataLen(long dataLen) {
        this.dataLen = dataLen;
    }
}

class MyContent implements Serializable {
    String name;
    String methodName;
    Class<?>[] parameterTypes;
    Object[] args;
    String res;

    public String getRes() {
        return res;
    }

    public void setRes(String res) {
        this.res = res;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}

class PackMsg {
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

interface Car {
    String ooxx(String s);
}

interface Fly {
    void xxoo(String msg);
}




