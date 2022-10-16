package com.system.rpcdemo.proxy;

import com.system.rpcdemo.rpc.Dispatcher;
import com.system.rpcdemo.rpc.protocol.MyContent;
import com.system.rpcdemo.rpc.transport.ClientFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

/**
 * @description com.system.rpcdemo.proxy
 * @author: chengyu
 * @date: 2022-10-16 10:57
 */
public class MyProxy {

    public static <T> T proxyGet(Class<T> interfaceInfo) {
        //实现各个版本的动态代理。。。
        ClassLoader loader = interfaceInfo.getClassLoader();
        Class<?>[] methodInfo = {interfaceInfo};

        //TODO LOCAL RMEOTE 实现：用到dispatcher 直接给你返回，还是本地调用的时候也代理一下
        Dispatcher dis = Dispatcher.getDis();
        return (T) Proxy.newProxyInstance(loader, methodInfo, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                //TODO 应该在service的方法执行的时候确定是本地的还是远程的，用到dispatcher来区分下
                Object res = null;
                Object o = dis.get(interfaceInfo.getName());
                if(null == o) {
                    //就要走rpc调用

                    //如何设计我们的consumer对于provider的调用过程
                    //1，调用服务，方法，参数 ---> 封装成message [content]
                    String name = interfaceInfo.getName();
                    String methodName = method.getName();
                    Class<?>[] parameterTypes = method.getParameterTypes();

                    //TODO rpc 就像小火车拉货，content是service的具体数据，但是还需要header层完成IO传输的控制
                    MyContent content = new MyContent();
                    content.setArgs(args);
                    content.setMethodName(methodName);
                    content.setName(name);
                    content.setParameterTypes(parameterTypes);

                    //TODO 未来的小火车可能会变
                    /**
                     * 1，缺失了注册发现，zk
                     * 2，第一层负载面向的provider
                     * 3,consumer 线程池 面向service；并发就有木桶，倾斜
                     * serviceA
                     *      ipA:port
                     *          socket1
                     *          socket2
                     *      ipB:port
                     */
                    CompletableFuture resF = ClientFactory.transport(content);
                    res = resF.get();   //阻塞的，一直到被执行为止
                } else {
                    //就是local调用，local也走下代理是因为可以插入一些插件的机会，做一些扩展
                    System.out.println("local FC...");
                    Class<?> clazz = o.getClass();
                    try {
                        Method m = clazz.getMethod(method.getName(), method.getParameterTypes());
                        res = m.invoke(o, args);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

                return res;
            }
        });
    }

}
