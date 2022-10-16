package com.system.rpcdemo.rpc.transport;

import com.system.rpcdemo.rpc.util.PackMsg;
import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.protocol.MyHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * @description com.system.rpcdemo.rpc.transport
 * @author: chengyu
 * @date: 2022-10-16 14:20
 */
public class ServerDecode extends ByteToMessageDecoder {

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
            if (buf.readableBytes() >= 110 + header.getDataLen()) {
                buf.readBytes(110);
                byte[] data = new byte[(int) header.getDataLen()];
                buf.readBytes(data);
                ByteArrayInputStream din = new ByteArrayInputStream(data);
                ObjectInputStream odin = new ObjectInputStream(din);

                if (header.getFlag() == 0x14141414) {     //客户端向服务端发送方向
                    MyContent content = (MyContent) odin.readObject();
                    System.out.println(content.getName());  //打印接口名称
                    out.add(new PackMsg(header, content));
                } else if (header.getFlag() == 0x14141424) {  //服务端返回了
                    MyContent content = (MyContent) odin.readObject();
                    out.add(new PackMsg(header, content));
                }
            } else {
                break;      //最后一段报文不完整，直接break跳出循环。留存的buf拼接到下一个报文里
            }
        }
    }
}

