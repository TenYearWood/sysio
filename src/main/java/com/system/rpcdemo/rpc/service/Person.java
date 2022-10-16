package com.system.rpcdemo.rpc.service;

import java.io.Serializable;

/**
 * @description com.system.rpcdemo.rpc.service
 * @author: chengyu
 * @date: 2022-10-16 19:12
 */
public class Person implements Serializable {
    String name;
    Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "name='" + name + ", age=" + age;
    }
}
