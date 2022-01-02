package com.example.cglib;

import com.example.cglib.proxy.HelloWorld;
import com.example.cglib.proxy.impl.HelloWorldImpl;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;

/**
 * @Author youtiaoguagua
 * @create 2022/1/2 1:16
 */
@Slf4j
public class Main {
    public static void main(String[] arg) {
        System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        HelloWorldImpl o = new HelloWorldImpl();
        HelloWorld helloWorld = (HelloWorld) Proxy.newProxyInstance(HelloWorld.class.getClassLoader(), HelloWorldImpl.class.getInterfaces(), (obj, method, args) -> {
            log.info("调用方法开始：{}", method.getName());
            Object result = method.invoke(o, args);
            log.info("调用结果:{}", result);
            log.info("调用方法结束：{}", method.getName());
            return result;
        });
        log.info(helloWorld.sayHello("hello world"));
    }
}
