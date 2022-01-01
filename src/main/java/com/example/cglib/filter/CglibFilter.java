package com.example.cglib.filter;

import com.example.cglib.proxy.HelloWorld;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.CallbackFilter;

import java.lang.reflect.Method;

/**
 * @author 王祥飞
 * @time 2021/12/30 2:10 PM
 */
@Slf4j
public class CglibFilter implements CallbackFilter {
    @Override
    public int accept(Method method) {
        if (method.getName().equals("sayHello")) {
            return 0;
        } else if (method.getName().equals("sayGoodBye")) {
            return 1;
        }
        return 1;
    }
}
