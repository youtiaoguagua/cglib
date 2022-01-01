package com.example.cglib.interceptor;

import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @author 王祥飞
 * @time 2021/12/30 1:52 PM
 */
@Slf4j
public class AuthInterceptor implements MethodInterceptor {

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        log.info("认证开始.....");
        log.info("认证通过.....");
        log.info("开始执行方法：{}", method.getName());
        Object result = proxy.invokeSuper(obj, args);
        log.info("方法执行结果：{}", result);
        log.info("开始执行方法：{}", method.getName());
        return result;
    }
}
