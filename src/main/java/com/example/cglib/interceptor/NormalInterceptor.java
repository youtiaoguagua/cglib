package com.example.cglib.interceptor;

import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author 王祥飞
 * @time 2021/12/30 2:09 PM
 */
@Slf4j
public class NormalInterceptor implements MethodInterceptor {
    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        log.info("NormalInterceptor before");
        Object result = methodProxy.invokeSuper(o, objects);
        log.info("NormalInterceptor result:{}", result);
        log.info("NormalInterceptor after");
        return result;
    }
}
