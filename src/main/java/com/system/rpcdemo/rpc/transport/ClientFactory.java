package com.system.rpcdemo.rpc.transport;

import com.system.rpcdemo.rpc.util.SerDerUtil;
import com.system.rpcdemo.rpc.ResponseMappingCallback;
import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.protocol.MyHeader;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description com.system.rpcdemo.rpc.transport
 * @author: chengyu
 * @date: 2022-10-16 13:08
 */
public class ClientFactory {
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

    public static CompletableFuture<Object> transport(MyContent content) {
        //content 就是货物，现在可以用自定义的rpc传输协议（有状态），也可以用http协议作为载体传输
        //我们先手工用了http协议作为载体，那这样是不是代表我们未来可以让provider是一个tomcat jetty 基于http协议的一个容器
        //有无状态来自于你使用的什么协议，那么http协议肯定是无状态，每请求对应一个连接
        //dubbo是一个rpc框架 netty是一个io框架
        //dubbo中传输协议上，可以是自定义的rpc传输协议，也可以是http协议

        String type = "rpc";
        CompletableFuture<Object> res = new CompletableFuture<>();
        if (type.equals("rpc")) {
            byte[] msgBody = SerDerUtil.serialize(content);
            MyHeader header = MyHeader.createHeader(msgBody);
            byte[] msgHeader = SerDerUtil.serialize(header);
            System.out.println("main:::" + msgHeader.length);

            NioSocketChannel clientChannel = factory.getClient(new InetSocketAddress("localhost", 9090));
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(msgHeader.length + msgBody.length);
            long id = header.getRequestID();
            ResponseMappingCallback.addCallBack(id, res);
            byteBuf.writeBytes(msgHeader);
            byteBuf.writeBytes(msgBody);
            ChannelFuture channelFuture = clientChannel.writeAndFlush(byteBuf);
        } else {
            //使用http协议为载体
            //1,用URL现成的工具（包含了http的编解码，发送，socket，连接）
            //urlTS(content, res);

            //2,自己操心: on netty (IO框架) + 已经提供的http相关的编解码
            nettyTS(content, res);
        }
        return res;
    }

    private static void nettyTS(MyContent content, CompletableFuture<Object> res) {
        //在这个执行之前，我们的server端 provider端已经开发完了，已经是on netty的http server了
        //现在做的是consumer端的代码修改，改成on netty的http client
        //刚才一切都顺利，关注未来的问题...

        //每个请求对应一个连接
        //1.通过netty建立io 建立连接
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Bootstrap bs = new Bootstrap();
        Bootstrap client = bs.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(1024 * 512))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        //3.接收  预埋的回调，根据netty对socket io事件的响应
                                        //客户端的msg是啥：完整的http-response
                                        FullHttpResponse response = (FullHttpResponse) msg;
                                        System.out.println(response.toString());

                                        ByteBuf resContent = response.content();
                                        byte[] data = new byte[resContent.readableBytes()];
                                        resContent.readBytes(data);

                                        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(data));
                                        MyContent o = (MyContent) oin.readObject();

                                        res.complete(o.getRes());
                                    }
                                });
                    }
                });//未来连接后，收到数据的处理handler
        try {
            ChannelFuture syncFuture = client.connect("localhost", 9090).sync();
            //2.发送
            Channel clientChannel = syncFuture.channel();
            byte[] data = SerDerUtil.serialize(content);
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0,
                    HttpMethod.POST, "/", Unpooled.copiedBuffer(data));

            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, data.length);

            clientChannel.writeAndFlush(request).sync();  //作为client 向server端发送：http request
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这种方式是每请求占用一个连接的方式，因为使用的是http协议
     */
    private static void urlTS(MyContent content, CompletableFuture<Object> res) {

        Object obj = null;
        try {
            URL url = new URL("http://localhost:9090/");
            HttpURLConnection hc = (HttpURLConnection) url.openConnection();
            //post
            hc.setRequestMethod("POST");
            hc.setDoOutput(true);
            hc.setDoInput(true);

            OutputStream out = hc.getOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeObject(content);  //这里真的发送了吗？

            if (hc.getResponseCode() == 200) {
                InputStream in = hc.getInputStream();
                ObjectInputStream oin = new ObjectInputStream(in);
                MyContent myContent = (MyContent) oin.readObject();
                obj = myContent;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        res.complete(obj);
    }

    //一个consumer可以连接很多的provider，每一个provider都有自己的pool K V
    ConcurrentHashMap<InetSocketAddress, ClientPool> outboxs = new ConcurrentHashMap<>();

    public NioSocketChannel getClient(InetSocketAddress address) {
        //TODO 在并发情况下一定要谨慎
        ClientPool clientPool = outboxs.get(address);
        if (null == clientPool) {
            synchronized (outboxs) {
                if (null == clientPool) {
                    outboxs.putIfAbsent(address, new ClientPool(poolSize));
                    clientPool = outboxs.get(address);
                }
            }
        }

        int i = rand.nextInt(poolSize);
        if (clientPool.clients[i] != null && clientPool.clients[i].isActive()) {
            return clientPool.clients[i];
        } else {
            synchronized (clientPool.lock[i]) {
                if (null == clientPool.clients[i] || !clientPool.clients[i].isActive()) {
                    clientPool.clients[i] = create(address);
                }
            }
        }
        return clientPool.clients[i];
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

