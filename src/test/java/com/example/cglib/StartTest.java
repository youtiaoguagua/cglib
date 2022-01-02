package com.example.cglib;

import com.example.cglib.entity.User;
import com.example.cglib.entity.UserDto;
import com.example.cglib.filter.CglibFilter;
import com.example.cglib.interceptor.AuthInterceptor;
import com.example.cglib.interceptor.NormalInterceptor;
import com.example.cglib.proxy.HelloWorld;
import com.example.cglib.proxy.impl.HelloWorldImpl;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.beans.BeanCopier;
import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.FixedValue;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.NoOp;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;

/**
 * @author 王祥飞
 * @time 2021/12/30 1:14 PM
 */
@Slf4j
public class StartTest {

    @BeforeEach
    public void before() {
        //  设置java动态代理调试模式
        System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");

        //  设置Cglib动态代理调试模式
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target");

    }

    /**
     * 测试java动态代理
     */
    @Test
    @DisplayName("测试java动态代理")
    public void testJavaProxy() {
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

    @Test
    @DisplayName("Cglib动态代理使用invokeSuper")
    @Tag("cglib")
    public void testCglib() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
            log.info("调用方法开始：{}", method.getName());
            Object result = methodProxy.invokeSuper(obj, args);
            log.info("调用结果:{}", result);
            log.info("调用方法结束：{}", method.getName());
            return result;
        });
        HelloWorld helloWorld = (HelloWorld) enhancer.create();
        log.info(helloWorld.sayHello("cglib"));
    }

    @Test
    @DisplayName("Cglib动态代理使用invoke(StackOverFlow)")
    @Tag("cglib")
    public void testCglibWithInvoke() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
            log.info("调用方法开始：{}", method.getName());
            Object result = methodProxy.invoke(obj, args);
            log.info("调用结果:{}", result);
            log.info("调用方法结束：{}", method.getName());
            return result;
        });
        HelloWorld helloWorld = (HelloWorld) enhancer.create();
        log.info(helloWorld.sayHello("cglib"));
        //会报StackOverFlow异常
    }

    @Test
    @DisplayName("Cglib动态代理使用invoke(解决StackOverFlow)")
    @Tag("cglib")
    public void testCglibWithInvokeSolve() {
        HelloWorldImpl helloWorldOrigin = new HelloWorldImpl();
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
            log.info("调用方法开始：{}", method.getName());
            Object result = methodProxy.invoke(helloWorldOrigin, args);
            log.info("调用结果:{}", result);
            log.info("调用方法结束：{}", method.getName());
            return result;
        });
        HelloWorld helloWorld = (HelloWorld) enhancer.create();
        log.info(helloWorld.sayHello("cglib"));
        //会报StackOverFlow异常
    }

    @Test
    @DisplayName("Cglib动态代理使用method.invoke")
    @Tag("cglib")
    public void testCglibWithMethodInvoke() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(HelloWorldImpl.class);
        enhancer.setCallback((MethodInterceptor) (obj, method, args, methodProxy) -> {
            log.info("调用方法开始：{}", method.getName());
            Object result = method.invoke(obj, args);
            log.info("调用结果:{}", result);
            log.info("调用方法结束：{}", method.getName());
            return result;
        });
        HelloWorld helloWorld = (HelloWorld) enhancer.create();
        log.info(helloWorld.sayHello("cglib"));
        //会报StackOverFlow异常
    }





    @DisplayName("不同的回调实现")
    @Nested
    @Tag("cglib")
    class DifferentCallback {

        @Test
        @DisplayName("为每个方法设置回调")
        public void testMultiCallBack() {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(HelloWorldImpl.class);
            enhancer.setCallbackFilter(new CglibFilter());
            enhancer.setCallbacks(new MethodInterceptor[]{new AuthInterceptor(), new NormalInterceptor()});
            HelloWorld helloWorld = (HelloWorld) enhancer.create();
            log.info("最终返回结果：{}", helloWorld.sayHello("cglib"));
            log.info("最终返回结果：{}", helloWorld.sayGoodBye("cglib"));
        }

        @Test
        @DisplayName("FixedValue")
        public void testFixedValue() {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(HelloWorldImpl.class);
            enhancer.setCallback((FixedValue) () -> "fixedValue");
            HelloWorld helloWorld = (HelloWorld) enhancer.create();
            log.info("最终返回结果：{}", helloWorld.sayHello("cglib"));
        }

        @Test
        @DisplayName("NoOp")
        public void testNoOp() {
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(HelloWorldImpl.class);
            enhancer.setCallback(NoOp.INSTANCE);
            HelloWorld helloWorld = (HelloWorld) enhancer.create();
            log.info("最终返回结果：{}", helloWorld.sayHello("cglib"));
        }

        @Test
        @DisplayName("LazyLoader")
        public void testLazyLoader() {
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            HelloWorldImpl helloWorldLazy = helloWorld.lazyLoad();
            log.info("开始调用getField方法....");
            log.info("lazyLoad:{}", helloWorldLazy.getField());
            log.info("lazyLoad:{}", helloWorldLazy.getField());
        }


        @Test
        @DisplayName("Dispatcher")
        public void testDispatcher() {
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            HelloWorldImpl helloWorldDispatcher = helloWorld.dispatcher();
            log.info("开始调用getField方法....");
            log.info("dispatcher:{}", helloWorldDispatcher.getField());
            log.info("dispatcher:{}", helloWorldDispatcher.getField());
        }

        @Test
        @DisplayName("ProxyRefDispatcher")
        public void testLazyLoader2() {
            HelloWorldImpl helloWorld = new HelloWorldImpl();
            HelloWorldImpl helloWorldProxyRefDispatcher = helloWorld.proxyRefDispatcher();
            log.info("开始调用getField方法....");
            log.info("lazyLoad:{}", helloWorldProxyRefDispatcher.getField());
            log.info("lazyLoad:{}", helloWorldProxyRefDispatcher.getField());
        }
    }

    @Test
    @Tag("copy")
    public void testCopy() {
        User user = new User("cglib", 1);
        UserDto userDto = new UserDto();
        BeanCopier beanCopier = BeanCopier.create(User.class, UserDto.class, false);
        beanCopier.copy(user, userDto, null);
        log.info("userDto:{}", userDto);
    }
}
