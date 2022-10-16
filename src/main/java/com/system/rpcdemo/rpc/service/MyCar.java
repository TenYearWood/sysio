package com.system.rpcdemo.rpc.service;

/**
 * @description com.system.rpcdemo.rpc.service
 * @author: chengyu
 * @date: 2022-10-16 14:33
 */
public class MyCar implements Car {

    @Override
    public String ooxx(String msg) {
        System.out.println("server, get client arg :" + msg);
        return "server res " + msg;
    }

    @Override
    public Person oxox(String name, Integer age) {
        Person p = new Person();
        p.setName(name);
        p.setAge(age);
        return p;
    }
}
