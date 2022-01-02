# jdk及cglib动态代理原理

## 环境

![依赖关系图](https://cdn.jsdelivr.net/gh/youtiaoguagua/blog-img/blog/cglib.png)

* 日志框架logback，引入logback-classic即可引入日志实现层和api

* 测试框架junit,引入junit-jupiter即可，这是一个聚合pom

* 引入cglig库

  

## 原理分析

java动态代理有两种实现方式：

* JDK动态代理：利用反射机制生成一个实现代理接口的匿名类，在调用具体方法前调用InvokeHandler来处理。

* CGLIB动态代理：利用asm，修改字节码生成子类来处理。

  

### jdk动态代理

#### 代理方法执行分析



> 具体实现如下，使用jdk自带的`Proxy`实现`InvocationHandler`接口即可对代理对象进行增强



```java
HelloWorldImpl o = new HelloWorldImpl();
HelloWorld helloWorld = (HelloWorld) Proxy.newProxyInstance(HelloWorld.class.getClassLoader(), 		HelloWorldImpl.class.getInterfaces(), (obj, method, args) -> {
    log.info("调用方法开始：{}", method.getName());
    Object result = method.invoke(o, args);
    log.info("调用结果:{}", result);
    log.info("调用方法结束：{}", method.getName());
    return result;
});
log.info(helloWorld.sayHello("hello world"));
```

> 通过设置以下代码可以查看jdk动态生成的class文件

```java
//  设置java动态代理调试模式,在junit中设置此方法是无效的！
System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
```

下面为生成的代理类

```
├───com
│   └───sun
│       └───proxy
│               $Proxy0.class   # 生成的代理类
```

```java
public final String sayHello(String var1) throws  {
    try {
        return (String)super.h.invoke(this, m3, new Object[]{var1});
    } catch (RuntimeException | Error var3) {
        throw var3;
    } catch (Throwable var4) {
        throw new UndeclaredThrowableException(var4);
    }
}
```

上面为生成的的class文件反编译后的一个方法，很容易的看出，代理方法只是很简单的调用了`InvocationHandler`类中的`invoke`方法，在`invoke`方法中又会调用代理方法的`invoke`方法，这个`invoke`方法是反射方法，因此被代理的方法就被调用了，因此jdk动态代理是通过实现被代理类的接口，然后通过反射执行被代理方法实现的。



#### 生成代理类分析

> 下面咱们来看一下这个动态代理类是怎么生成的

下面为`Proxy.newProxyInstance`的代码：

```java
public static Object newProxyInstance(ClassLoader loader,
                                      Class<?>[] interfaces,
                                      InvocationHandler h)
    throws IllegalArgumentException
{
    Objects.requireNonNull(h);

    final Class<?>[] intfs = interfaces.clone();
    final SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
    }
    /*
     * Look up or generate the designated proxy class.
    */
    Class<?> cl = getProxyClass0(loader, intfs);
    ....
}
```

很容易看出`getProxyClass0`就是生成代理类的方法。

```java
    private static Class<?> getProxyClass0(ClassLoader loader,
                                           Class<?>... interfaces) {
        if (interfaces.length > 65535) {
            throw new IllegalArgumentException("interface limit exceeded");
        }

        // If the proxy class defined by the given loader implementing
        // the given interfaces exists, this will simply return the cached copy;
        // otherwise, it will create the proxy class via the ProxyClassFactory
        return proxyClassCache.get(loader, interfaces);
    }
```

比较有意思的一点是当被代理类的接口数量大于65535时就会报错，我们都知道一个类可以实现多个接口，但是这个接口数量也是有限制的，挺有意思的😂！

代理类是被放在一个弱引用map里的，接下来就是从map中取出接口的代理类。

```java
    private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
        proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

上面就是这个map构造方式，提供了key和value的构造工厂。

KeyFactory:

```java
//很简单的通过实现类的数量生成了一个Key对象    
private static final class KeyFactory
        implements BiFunction<ClassLoader, Class<?>[], Object>
    {
        @Override
        public Object apply(ClassLoader classLoader, Class<?>[] interfaces) {
            switch (interfaces.length) {
                case 1: return new Key1(interfaces[0]); // the most frequent
                case 2: return new Key2(interfaces[0], interfaces[1]);
                case 0: return key0;
                default: return new KeyX(interfaces);
            }
        }
    }
```

ProxyClassFactory：

```java
 private static final class ProxyClassFactory
        implements BiFunction<ClassLoader, Class<?>[], Class<?>>
    {
        // 代理类的前缀
        private static final String proxyClassNamePrefix = "$Proxy";
        // next number to use for generation of unique proxy class names
        private static final AtomicLong nextUniqueNumber = new AtomicLong();
        @Override
        public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {

            Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
            for (Class<?> intf : interfaces) {
                //验证类加载器是否将此接口的名称解析为相同的 Class 对象,简单点说就是这个接口是否可以被提供的类加载器加载。
                Class<?> interfaceClass = null;
                try {
                    interfaceClass = Class.forName(intf.getName(), false, loader);
                } catch (ClassNotFoundException e) {
                }
                if (interfaceClass != intf) {
                    throw new IllegalArgumentException(
                        intf + " is not visible from class loader");
                }
                // 确认是否是个接口
                if (!interfaceClass.isInterface()) {
                    throw new IllegalArgumentException(
                        interfaceClass.getName() + " is not an interface");
                }
                //验证接口是否是重复的
                if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(
                        "repeated interface: " + interfaceClass.getName());
                }
            }
            String proxyPkg = null;     // package to define proxy class in
            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;
            // 看注释挺绕的，应该就是想设置一个包名
            for (Class<?> intf : interfaces) {
                int flags = intf.getModifiers();
                if (!Modifier.isPublic(flags)) {
                    accessFlags = Modifier.FINAL;
                    String name = intf.getName();
                    int n = name.lastIndexOf('.');
                    String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
                    if (proxyPkg == null) {
                        proxyPkg = pkg;
                    } else if (!pkg.equals(proxyPkg)) {
                        throw new IllegalArgumentException(
                            "non-public interfaces from different packages");
                    }
                }
            }
            if (proxyPkg == null) {
                // if no non-public proxy interfaces, use com.sun.proxy package
                proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
            }

            // 设置代理类类名称
            long num = nextUniqueNumber.getAndIncrement();
            String proxyName = proxyPkg + proxyClassNamePrefix + num;

            //生成代理类
            byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
                proxyName, interfaces, accessFlags);
            try {
                return defineClass0(loader, proxyName,
                                    proxyClassFile, 0, proxyClassFile.length);
            } catch (ClassFormatError e) {
                throw new IllegalArgumentException(e.toString());
            }
        }
    }
```

通过上面我们可以很容易的了解代理类生成的过程了，`ProxyGenerator.generateProxyClass`应该是最重要的了，但是没必要深究了，无非就是生成方法，生成构造器等。另外上面提到在junit中无法通过设置变量生成代理类class文件，我们可以直接调用`ProxyGenerator.generateProxyClass`手动生成。



### Cglib动态代理分析



#### 代理方法执行分析

> 同jdk动态代理一样，首先要打开cglib的debug模式才能看见代理类

```java
        //  设置Cglib动态代理调试模式
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "target");
```

写一个最简单的使用方式：

```java
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
```

运行之后在target中看一下生成的代理类：

```
├───com
│   └───example
│       └───cglib
│           └───proxy
│               └───impl
│                       HelloWorldImpl$$EnhancerByCGLIB$$722f73d4$$FastClassByCGLIB$$5d1e80b1.class
│                       HelloWorldImpl$$EnhancerByCGLIB$$722f73d4.class
│                       HelloWorldImpl$$FastClassByCGLIB$$d5c59250.class
```

可以看见生成了三个类，下面来看一下这三个类都有什么吧，ヾ(≧▽≦*)o

1. `HelloWorldImpl$$EnhancerByCGLIB$$722f73d4`

   ```java
   public class HelloWorldImpl$$EnhancerByCGLIB$$722f73d4 extends HelloWorldImpl implements Factory {
       //省略大部分代码
       final String CGLIB$sayHello$1(String var1) {}
   
       public final String sayHello(String var1) {}
       //省略大部分代码
   }
   ```
   
   这个类类似于jdk动态代理生成的那个代理类，之后我们来看他是怎么执行的。
   
2. `HelloWorldImpl$$EnhancerByCGLIB$$722f73d4$$FastClassByCGLIB$$5d1e80b1.class`

   ```java
   public class HelloWorldImpl$$EnhancerByCGLIB$$722f73d4$$FastClassByCGLIB$$5d1e80b1 extends FastClass {
       public HelloWorldImpl$$EnhancerByCGLIB$$722f73d4$$FastClassByCGLIB$$5d1e80b1(Class var1) {}
   
       public int getIndex(Signature var1) {}
   
       public int getIndex(String var1, Class[] var2) {}
   
       public int getIndex(Class[] var1) {}
   
       public Object invoke(int var1, Object var2, Object[] var3) throws InvocationTargetException {}
   
       public Object newInstance(int var1, Object[] var2) throws InvocationTargetException {}
   
       public int getMaxIndex() {}
   }
   ```

   

   这个类里最重要的就是`getIndex`方法和`invoke`方法。

3. `HelloWorldImpl$$FastClassByCGLIB$$d5c59250.class`

   ```java
   public class HelloWorldImpl$$FastClassByCGLIB$$d5c59250 extends FastClass {
       public HelloWorldImpl$$FastClassByCGLIB$$d5c59250(Class var1) {}
   
       public int getIndex(Signature var1) {}
   
       public int getIndex(String var1, Class[] var2) {}
   
       public int getIndex(Class[] var1) {}
   
       public Object invoke(int var1, Object var2, Object[] var3) throws InvocationTargetException {}
   
       public Object newInstance(int var1, Object[] var2) throws InvocationTargetException {}
   
       public int getMaxIndex() {}
   }
   ```

   和上一个类相似，但是有本质区别。

   

> 下面将对上面的三个类进行分析

先不分析`enhancer.create`方法，首先上面的例子会调用`HelloWorld.sayHello`方法，然后调用代理类的`sayHello`方法。

```java
    public final String sayHello(String var1) {
        MethodInterceptor var10000 = this.CGLIB$CALLBACK_0;
        if (var10000 == null) {
            CGLIB$BIND_CALLBACKS(this);
            var10000 = this.CGLIB$CALLBACK_0;
        }

        return var10000 != null ? (String)var10000.intercept(this, CGLIB$sayHello$1$Method, new Object[]{var1}, CGLIB$sayHello$1$Proxy) : super.sayHello(var1);
    }
```

可以看见`sayHello`方法会去掉用上面设置的回调方法，然后就进入了我们自己写的`callBack`方法，`callback`方法的一个继承接口是`MethodInterceptor`。

```java
public interface MethodInterceptor extends Callback
{ 
    public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args,MethodProxy proxy) throws Throwable;
}
```

可以看见这个`intercept`方法有四个参数：

* `obj`是代理类
* `method`是被代理类的方法
* `args`是方法传入的参数
* `proxy`是代理类的方法

之后肯定是执行方法了，首先可以想到的是直接调用`method.invoke`方法，这个其实就是jdk动态代理执行方法的方式，使用反射执行，这样就没cglib的优势了。值得注意的是`invoke`的第一个参数不要直接传入参的`obj`,这样会造成死循环，至于为什么会这样后面会解释。

不使用`method.invoke`执行方法，那么只能使用`proxy`去执行方法了，问题又来了，这个`proxy`有两个方法`invoke`和`invokeSuper`方法，那么这两个方法有什么区别呢？

> 首先来看`invokeSuper`。

```java
public Object invokeSuper(Object obj, Object[] args) throws Throwable {
    try {
        init();
        FastClassInfo fci = fastClassInfo;
        return fci.f2.invoke(fci.i2, obj, args);
    } catch (InvocationTargetException e) {
        throw e.getTargetException();
    }
}
```


```java
private void init(){
    if (fastClassInfo == null)
    {
        synchronized (initLock)
        {
            if (fastClassInfo == null)
            {
                CreateInfo ci = createInfo;

                FastClassInfo fci = new FastClassInfo();
                fci.f1 = helper(ci, ci.c1);
                fci.f2 = helper(ci, ci.c2);
                fci.i1 = fci.f1.getIndex(sig1);
                fci.i2 = fci.f2.getIndex(sig2);
                fastClassInfo = fci;
                createInfo = null;
            }
        }
    }
}

public static MethodProxy create(Class c1, Class c2, String desc, String name1, String name2) {
    MethodProxy proxy = new MethodProxy();
    proxy.sig1 = new Signature(name1, desc);
    proxy.sig2 = new Signature(name2, desc);
    proxy.createInfo = new CreateInfo(c1, c2);
    return proxy;
}
```

```java
//HelloWorldImpl$$EnhancerByCGLIB$$722f73d4静态代码块
static void CGLIB$STATICHOOK1() {
    CGLIB$THREAD_CALLBACKS = new ThreadLocal();
    CGLIB$emptyArgs = new Object[0];
    CGLIB$sayHello$4$Method = var10000[4];
    CGLIB$sayHello$4$Proxy = MethodProxy.create(var1, var0, "(Ljava/lang/String;)Ljava/lang/String;", "sayHello", "CGLIB$sayHello$4");
}
```

可以看见`invokeSuper`首先调用了`init`方法，`init`方法需要一个`createInfo`这个`createInfo`方法是由`create`方法创建的，那么这个`create`是由什么调用的呢，通过debug可以看见是由我们的`HelloWorldImpl`代理类在静态代码块中调用的，可以看见这个代码块传递了方法参数，和代理类中的两个方法，查看这两个方法可以看出`sayHello`会调用`callback`而`CGLIB$sayHello$4`会调用被代理类的`sayHello`方法。

得到`ci`类之后之后会调用`helper`方法，这个方法主要就是生成两个`fastClass`类，通过`fastClass.getIndex`可以很快的得到执行的方法。而`f2`其实就是被代理类的执行方法，因此可以看出`fci.f2.invoke`其实执行的就是`CGLIB$sayHello$4`方法。从头到尾没有使用反射执行方法，简单的通过`super.sayHelloWorld`就完成了方法的执行。这也是cglib比jdk动态代理快的奥秘。

> 下面我们look look`proxy.invoke`方法

```java
public Object invoke(Object obj, Object[] args) throws Throwable {
    try {
        init();
        FastClassInfo fci = fastClassInfo;
        return fci.f1.invoke(fci.i1, obj, args);
    } catch (InvocationTargetException e) {
        throw e.getTargetException();
    } catch (IllegalArgumentException e) {
        if (fastClassInfo.i1 < 0)
            throw new IllegalArgumentException("Protected method: " + sig1);
        throw e;
    }
}
```

可以看见和`invokeSuper`几乎一样，只是最后调用的是`fci.f1.invoke`方法，在`invokeSuper`中我们传入的obj参数就是`MethodInterceptor#intercept`所传给我们的`obj`，如果在`invoke`也传入这个`obj`的话，那么将会进入死循环，为什么会出现死循环呢，从上面的`init`方法中我们可以得出`f1`其实执行的是`sayHello`方法，而代理对象的`sayHello`方法会接着执行`MethodInterceptor#intercept`方法，然后`MethodInterceptor#intercept`会接着执行`invoke`方法，从而陷入死循环，解决这个问题很简单，就是自己`new`一个`HelloWorldImpl`对象传入，这样执行的就是你传入的这个对象的`sayHello`方法而不是代理对象的。上面提到的如果你执行`method.invoke`传入参数的`obj`会陷入死循环是一个道理，因为执行的是代理类的`sayHello`，而代理类的`sayHello`会接着执行`callback`。



#### 生成代理类分析

下面我们来see see `enhancer.create`干了什么。

```java
public Object create() {
    classOnly = false;
    argumentTypes = null;
    return createHelper();
}

private Object createHelper() {
    preValidate();
    // 这个key其实就是个上下文，后面可以根据这个key创建代理类
    Object key = KEY_FACTORY.newInstance((superclass != null) ? superclass.getName() : null,
            ReflectUtils.getNames(interfaces),
            filter == ALL_ZERO ? null : new WeakCacheKey<CallbackFilter>(filter),
            callbackTypes,
            useFactory,
            interceptDuringConstruction,
            serialVersionUID);
    this.currentKey = key;
    Object result = super.create(key);
    return result;
}
```

```java
protected Object create(Object key) {
    ...
    this.key = key;
    Object obj = data.get(this, getUseCache());
    if (obj instanceof Class) {
        return firstInstance((Class) obj);
    }
    return nextInstance(obj);
    ...
}
```

 `data.get`将会生成`class`文件

```java
public V get(K key) {
    final KK cacheKey = keyMapper.apply(key);
    Object v = map.get(cacheKey);
    // 从map里找，如果有的话就不会构建class文件了
    if (v != null && !(v instanceof FutureTask)) {
        return (V) v;
    }

    return createEntry(key, cacheKey, v);
}

protected V createEntry(final K key, KK cacheKey, Object v) {
    FutureTask<V> task;
    boolean creator = false;
    if (v != null) {
        //如果有别的线程已经执行了，那么就不创建新线程了
        task = (FutureTask<V>) v;
    } else {
        task = new FutureTask<V>(new Callable<V>() {
            public V call() throws Exception {
                return loader.apply(key);
            }
        });
        Object prevTask = map.putIfAbsent(cacheKey, task);
        // 查看map中是否已经存在任务了
        if (prevTask == null) {   
            creator = true;
            task.run();
        } else if (prevTask instanceof FutureTask) {
            task = (FutureTask<V>) prevTask;
        } else {
            return (V) prevTask;
        }
    }

    V result;
    try {
        result = task.get();
    } catch (InterruptedException e) {
        throw new IllegalStateException("Interrupted while loading cache item", e);
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            throw ((RuntimeException) cause);
        }
        throw new IllegalStateException("Unable to load cache item", cause);
    }
    if (creator) {
        map.put(cacheKey, result);
    }
    return result;
}

```

`loader.apply`将会生成代理,调用`net.sf.cglib.proxy.Enhancer#generateClass`构造`org.objectweb.asm.ClassVisitor`，之后生成代理类，里头的逻辑比较多，就不一一分析了，主要是使用了asm库构造新类。



## Cglib的使用

```java
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
```

以上代码[获取](git@github.com:/youtiaoguagua/cglib.git)。
