package com.system.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * @description com.system.io
 * @author: chengyu
 * @date: 2022-09-09 10:26
 */
public class SocketNIO {
    public static void main(String[] args) throws IOException, InterruptedException {
        LinkedList<SocketChannel> clients = new LinkedList<>();

        /**
         * 得到一个serverSocketChannel通道，channel:可读可写双向
         * ss.configureBlocking(false)：设置监听不要阻塞,ss.accept() 就不会一直阻塞着
         * ss.configureBlocking设置为true就阻塞，回到了BIO，设置为false，就是NIO
         */
        ServerSocketChannel ss = ServerSocketChannel.open();
        ss.bind(new InetSocketAddress(9090));
        ss.configureBlocking(false);    //重点 OS NONBLOCKING

        ss.setOption(StandardSocketOptions.TCP_NODELAY, false);
//        StandardSocketOptions.TCP_NODELAY;
//        StandardSocketOptions.SO_KEEPALIVE;
//        StandardSocketOptions.SO_LINGER;
//        StandardSocketOptions.SO_RCVBUF;
//        StandardSocketOptions.SO_SNDBUF;
//        StandardSocketOptions.SO_REUSEADDR;

        while (true) {
            //1.接受客户端的连接
            Thread.sleep(1000);
            SocketChannel client = ss.accept(); //不会阻塞？ -1 NULL
            //accept 调用内核了：1.没有客户端连接进来，返回值? 在BIO的时候一直卡着，但是在NIO，不卡着，返回-1，NULL
            //如果来客户端的连接，accept返回的是这个客户端的fd 5，client object
            //NONBLOCKING 就是代码能往下走了，只不过有不同的情况。

            if (client == null) {
                System.out.println("null...");
            } else {
                /**
                 * 也可以给连接的客户端设置blocking为false
                 * socket (服务端的listen socket<连接请求三次握手后，往我这里扔，我去通过accept得到连接的socket>，连接socket<连接后的数据读写使用的>)
                 */
                client.configureBlocking(false);
                int port = client.socket().getPort();
                System.out.println("client...port: " + port);
                clients.add(client);
            }

            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);    //可以在堆里 堆外

            //2.遍历已经连接进来的客户端能不能读写数据
            for (SocketChannel c : clients) {    //串行化 多线程
                int num = c.read(buffer);       //>0 -1 0 //不会阻塞
                if (num > 0) {
                    buffer.flip();
                    byte[] aaa = new byte[buffer.limit()];
                    buffer.get(aaa);

                    String b = new String(aaa);
                    System.out.println(c.socket().getPort() + " : " + b);
                    buffer.clear();
                }
            }
        }
    }
}
