package com.example.cglib.proxy.impl;

import com.example.cglib.proxy.HelloWorld;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Dispatcher;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.LazyLoader;
import net.sf.cglib.proxy.ProxyRefDispatcher;

/**
 * @author 王祥飞
 * @time 2021/12/30 10:39 AM
 */
@Slf4j
public class HelloWorldImpl implements HelloWorld {

    @Getter
    @Setter
    private String field;


    @Override
    public String sayHello(String str) {
        log.info("内部执行:HelloWorldImpl.sayHello");
        return str;
    }

    @Override
    public String sayGoodBye(String str) {
        log.info("内部执行:HelloWorldImpl.sayGoodBye");
        return str;
    }

    public HelloWorldImpl lazyLoad() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((LazyLoader) () -> {
            log.info("懒加载被调用！");
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            helloWorld.setField("lazyLoad");
            log.info("懒加载调用结束！");
            return helloWorld;
        });
        return (HelloWorldImpl) enhancer.create();
    }


    public HelloWorldImpl dispatcher() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((Dispatcher) () -> {
            log.info("dispatcher被调用！");
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            helloWorld.setField("dispatcher");
            log.info("dispatcher调用结束！");
            return helloWorld;
        });
        return (HelloWorldImpl) enhancer.create();
    }

    public HelloWorldImpl proxyRefDispatcher() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((ProxyRefDispatcher) proxy -> {
            log.info("源代理类：{}", proxy.getClass());
            log.info("proxyRefDispatcher被调用！");
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            helloWorld.setField("proxyRefDispatcher");
            log.info("proxyRefDispatcher调用结束！");
            return helloWorld;
        });
        return (HelloWorldImpl) enhancer.create();
    }
}
