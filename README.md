使用netty搭建一个RPC框架
模块介绍：
- rpc-common：定义公共的RPC接口
- rpc-server：rpc服务端模块
- rpc-client：rpc客户端模块

启动：
- 启动rpc-server中的ServerBootstrap main方法
- 启动rpc-server中的ClientBoostrap main方法


# 使用Netty实现自定义RPC框架（一）



## 1.	Netty是什么

> Netty是JBOSS提供的一个异步、基于事件驱动的网络编程框架，大大简化了NIO的开发流程，借助Netty可以快速开发出一个网络应用，知名的Dubbo、ElasticSearch框架内部都使用了Netty。



为什么使用Netty？



传统的NIO缺点：

- NIO类库和API繁琐，使用麻烦
- 可靠性不强，开发工作量和难度大
- NIO的BUG



​	Netty优点：

- 对各自传输协议提供统一的API
- 高度可定制的线程模型
- 更高的吞吐量，更低的等待延迟
- 更少的资源消耗
- 最小化不必要的内存拷贝

## 2.	RPC是什么

> RPC，远程过程调用，目前主流的远程调用有两种，现在的服务间通信的方式也基本上是这两种。
>
> - 基于HTTP的resultful形式的广义远程调用，以springcloud的feign和resttemplate为代表，采用HTTP的7层调用协议，并且协议的参数和响应序列化基本以JSON和XML格式为主
> - 基于TCP的狭义的RPC远程调用，以阿里巴巴的Dubbo为代表，主要通过Netty来实现4层网络协议，NIO实现异步传输，序列化也可以是JSON或者hessian2以及java自带的序列化等，可以自行配置

## 3.	RPC框架实现



#### 3.1	RPC实现原理

一个RPC框架主要有三个角色：

- 服务端：暴露服务的服务提供方

- 客户端：调用远程服务的服务消费方

- 注册发现中心：服务注册与发现的注册中心

  如下图所示：

<img src="https://pic2.zhimg.com/80/v2-639aa98955832bfcde2499498b1bb229_720w.jpg" width="50%">


一次完整的RPC调用过程：

- 服务消费方以调用本地调用方式调用服务；
- client stud(服务端存根)接收到调用请求后负责将方法、参数等组装成能够进行网络传输的消息体；
- client stud找到服务地址， 并将消息发送到服务端；
- server stud接收到消息后进行解码；
- server stud以server和client约定的方式根据解码结果，调用本地服务；
- 本地服务执行方法并将结果返回给server stud；
- server stud将返回结果打包成消息发送到消费方；
- client stud接收到消息，并进行解码；
- 服务消费方得到最终结果。

RPC框架实现的思路就是将这些步骤封装起来，只要服务端和客户端约定好调用规则，即可实现远程调用。

#### 3.2	RPC实现步骤

我们在此只实现简单的服务远程调用，在下一篇文章中会添加zookeeper注册中心来实现服务的注册与发现。

主要实现目标：模仿Dubbo，约定好服务端和客户端接口和协议，消费者远程调用服务端，服务端返回一个字符串，底层使用netty来完成，序列化采用JSON，具体步骤：

- 创建一个公共的接口项目，并定义好可供远程调用的方法和接口；
- 创建一个服务提供者项目，该项目启动后会一直监听某个端口，并处理客户端发送的请求，按照约定格式返回数据；
- 创建一个服务消费者项目，该类需要透明地调用不存在本项目中的方法，内部使用Netty请求提供者并返回数据。

##### 3.2.1	公共项目

- 引入一些通用依赖：

```
  <dependency>
        <groupId>io.netty</groupId>
        <artifactId>netty-all</artifactId>
        <version>4.1.6.Final</version>
      </dependency>
  
      <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>fastjson</artifactId>
        <version>1.2.41</version>
      </dependency>
```

  

- 定义公共的接口以及可供调用的方法：

```
  public interface UserService {
    String sayHello(String word);
  }
  
```

- 定义一个序列化接口，该接口定义了两个方法，一是将对象转换为二进制，二是将二进制数据转换为对象

```
  public interface Serializer {
      /**
       * java对象转换为二进制
       *
       * @param object
       * @return
       */
      byte[] serialize(Object object) throws IOException;
  
      /**
       * 二进制转换成java对象
       *
       * @param clazz
       * @param bytes
       * @param <T>
       * @return
       */
      <T> T deserialize(Class<T> clazz, byte[] bytes) throws IOException;
  }
```

  

- 定义一个JSON序列化实现类：

```
  public class JSONSerializer implements Serializer {
      public byte[] serialize(Object object) throws IOException {
          return JSON.toJSONBytes(object);
      }
  
      public <T> T deserialize(Class<T> clazz, byte[] bytes) throws IOException {
          return JSON.parseObject(bytes, clazz);
      }
  }
  
```

  该类中采用FastJson框架来实现二进制数据与Java对象之间的互相转换。

- 定义RPC编码与解码器：

```
  public class RpcEncoder extends MessageToByteEncoder {
  
      private Class<?> clazz;
  
      private Serializer serializer;
  
      public RpcEncoder(Class<?> clazz, Serializer serializer) {
          this.clazz = clazz;
          this.serializer = serializer;
  
      }
  
      @Override
      protected void encode(ChannelHandlerContext channelHandlerContext, Object msg, ByteBuf byteBuf) throws Exception {
          if (clazz != null && clazz.isInstance(msg)) {
              byte[] bytes = serializer.serialize(msg);
              byteBuf.writeInt(bytes.length);
              byteBuf.writeBytes(bytes);
          }
      }
```

```
  
  public class RpcDecoder extends ByteToMessageDecoder {
  
      private Class<?> clazz;
  
      private Serializer serializer;
  
      public RpcDecoder(Class<?> clazz, Serializer serializer) {
          this.clazz = clazz;
          this.serializer = serializer;
      }
  
      protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
          if(byteBuf.isReadable()) {
              int dataLength = byteBuf.readableBytes();
              byte[] data = new byte[dataLength];
              byteBuf.readBytes(data);
              String s = new String(data);
              String substring = s.substring(4);
              RpcRequest rpcRequest = new JSONSerializer().deserialize(RpcRequest.class, substring.getBytes());
              System.out.println(rpcRequest);
              list.add(rpcRequest); // 将数据添加进去
          }
      }
  }
```

  在编码器和解码器中，分别实现`MessageToByteEncoder`和`ByteToMessageDecoder`两个接口，在`encode`和`decode`两个方法中，采用上一步定义好的JSON序列化对象定义了数据编码解码方式。

- 定义一个`RpcRequest`来封装客户端请求数据

```
  public class RpcRequest implements Serializable {
  
      /**
       * 请求对象的ID
       */
      private String requestId;
  
      /**
       * 类名
       */
      private String className;
  
      /**
       * 方法名
       */
      private String methodName;
  
      /**
       * 参数类型
       */
      private Class<?>[] parameterTypes;
  
      /**
       * 入参
       */
      private Object[] parameters;
      
      //省略getter/setter方法
  }
```

  该类中主要用于封装请求类名、方法名，参数类型，以及参数值。

##### 3.2.2	服务端实现

- 编写UserService实现类：

```
  @Service(value = "userServiceImpl")
  public class UserServiceImpl implements UserService {
  
    public String sayHello(String word) {
      return word;
    }
  
    public static void startServer(String hostName, int port) {
      NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
      ServerBootstrap serverBootstrap = new ServerBootstrap();
  
      serverBootstrap.group(eventLoopGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel socketChannel) throws Exception {
              ChannelPipeline pipeline = socketChannel.pipeline();
              //添加解码自定义序列化方式（JSON格式序列化）
              pipeline.addLast(new RpcDecoder(RpcRequest.class, new JSONSerializer()));
              //返回给客户端数据为字符串，因此编码方式为StringEncoder即可。
              pipeline.addLast(new StringEncoder());
              //自定义Handler
              pipeline.addLast(new ServiceHandler());
            }
          });
      serverBootstrap.bind(hostName, port);
      System.out.println("server start in port:" + port);
    }
  }
```

  该实现类定义了`sayHello`方法用来提供给客户端进行调用，`startServer`方法是主要`Netty`启动逻辑，在

  该方法中，主要逻辑有：

   - 定义两个线程池，

   - 初始化netty server启动脚手架，并绑定两个线程池

   - 添加数据编码解码序列化公司

   - 添加自定义handler来处理具体逻辑

   - 绑定端口，进行监听

     

- 自定义handler：

```
  public class ServiceHandler extends ChannelInboundHandlerAdapter {
  
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
          //解析封装的RpcRequest对象，并使用反射调用目标方法，响应字符串给客户端
          RpcRequest rpcRequest = (RpcRequest) msg;
          UserService userService = (UserService) BeanFactory.getInstance(rpcRequest.getClassName());
          String response = userService.sayHello(rpcRequest.getParameters()[0].toString());
          ctx.writeAndFlush("you are success:" + response);
      }
  }
```

  该类是核心的远程调用逻辑，服务器启动后，客户端的请求会被该方法读取到，该方法根据定义好的格式获取到请求对象，并使用返回调用对应的方法，并向客户端返回数据。

- 定义启动类：

```
  @SpringBootApplication
  public class ServerBootstrap {
  
      public static void main(String[] args) throws IllegalAccessException, InstantiationException {
          //初始IOC容器
          initIoc();
  
          //初始化服务器监听
          UserServiceImpl.startServer("localhost", 8999);
      }
  
      /**
       * 初始化IOC容器
       */
      private static void initIoc() throws InstantiationException, IllegalAccessException {
          //扫描项目中所有带Service、Repository、Controller注解的类
          Reflections reflections = new Reflections();
          Set<Class<?>> serviceAnnotations = reflections.getTypesAnnotatedWith(Service.class);
          for (Class<?> annotationClass : serviceAnnotations) {
              Object o = annotationClass.newInstance();
              Service annotation = annotationClass.getAnnotation(Service.class);
              //bean名称，唯一标识
              String beanName = annotation.value() == null || annotation.value() == "" ? annotationClass.getSimpleName() : annotation.value();
              //如果没有bean名称，默认以实现的接口名首字母小写的接口名，如果没有接口，则以自己类名首字母小写为bean name
              if(annotationClass.getInterfaces().length > 0) {
                  BeanFactory.addInstance(annotationClass.getInterfaces()[0].getSimpleName().substring(0, 1).toLowerCase() + annotationClass.getInterfaces()[0].getSimpleName().substring(1), o);
              }
              BeanFactory.addInstance(beanName, o);
          }
      }
  }
```

  在该启动类中，初始化了一个IOC容器将带有`@Service`注解的类扫描到IOC容器中，并初始化服务端监听逻辑；

- IOC涉及到的注解以及代码：

```
  @Target({ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Service {
      String value();
  }
```

```
  @Target({ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface SpringBootApplication {
  
  }
```

```
  public class BeanFactory {
  
      private static HashMap<String, Object> beans = new HashMap<String, Object>();
  
  
      public static Object getInstance(String beanName) {
          return beans.get(beanName);
      }
  
      public static Object getInstanceByClassType(Object classType) {
          final Set<String> keys = beans.keySet();
          for (String key : keys) {
              final Object o = beans.get(key);
              if(o.getClass().equals(classType)) {
                  return o;
              }
          }
          return null;
      }
  
      public static HashMap<String, Object> getBeans() {
          return beans;
      }
  
  
      public static void addInstance(String beanName, Object bean) {
          beans.put(beanName, bean);
      }
  
  }
```

- 启动`ServerBootstrap`中的main方法， 即可开始服务端监听。

###### 3.2.3	客户端实现

- 定义一个消费者类：

```
  public class RpcConsumer {
  
    //1.创建一个线程池对象  -- 它要处理我们自定义事件
    private static ExecutorService executorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  
    //2.声明一个自定义事件处理器  UserClientHandler
    private static UserClientHandler userClientHandler;
  
    //3.编写方法,初始化客户端  ( 创建连接池  bootStrap  设置bootstrap  连接服务器)
    public static void initClient() throws InterruptedException {
      //1) 初始化UserClientHandler
      userClientHandler  = new UserClientHandler();
      //2)创建连接池对象
      EventLoopGroup group = new NioEventLoopGroup();
      //3)创建客户端的引导对象
      Bootstrap bootstrap =  new Bootstrap();
      //4)配置启动引导对象
      bootstrap.group(group)
          //设置通道为NIO
          .channel(NioSocketChannel.class)
          //设置请求协议为TCP
          .option(ChannelOption.TCP_NODELAY,true)
          //监听channel 并初始化
          .handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel socketChannel) throws Exception {
              //获取ChannelPipeline
              ChannelPipeline pipeline = socketChannel.pipeline();
              //设置编码
              pipeline.addLast(new StringDecoder());
              pipeline.addLast(new RpcEncoder(RpcRequest.class, new JSONSerializer()));
              //添加自定义事件处理器
              pipeline.addLast(userClientHandler);
            }
          });
  
      //5)连接服务端
      bootstrap.connect("127.0.0.1",8999).sync();
    }
  
    //4.编写一个方法,使用JDK的动态代理创建对象
    // serviceClass 接口类型,根据哪个接口生成子类代理对象
    public static Object createProxy(Class<?> serviceClass){
      return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
          new Class[]{serviceClass}, new InvocationHandler() {
            public Object invoke(Object o, Method method, Object[] params) throws Throwable {
              //1)初始化客户端cliet
              if(userClientHandler == null){
                initClient();
              }
              //2)给UserClientHandler 设置param参数
              RpcRequest rpcRequest = new RpcRequest();
              System.out.println(method.getName());
  
              //调用类名约定：
              String className = serviceClass.getName().substring(0, 1).toLowerCase() + serviceClass.getName().substring(1);
              System.out.println(serviceClass.getName());
              rpcRequest.setClassName(className);
              rpcRequest.setMethodName(method.getName());
              rpcRequest.setRequestId(UUID.randomUUID().toString().replaceAll("-", ""));//生成一个唯一的请求ID
              Class<?>[] parameterTypes = new Class[]{Object.class};
              rpcRequest.setParameterTypes(parameterTypes);
              rpcRequest.setParameters(params);
              userClientHandler.setParam(rpcRequest);
  
              //3).使用线程池,开启一个线程处理处理call() 写操作,并返回结果
              Object result = executorService.submit(userClientHandler).get();
  
              //4)return 结果
              return result;
            }
          });
    }
  
  }
```

  该类中主要定义了创建服务端远程调用的代理对象生成代码。

- 客户端自定义事件处理器:

```
  public class UserClientHandler extends ChannelInboundHandlerAdapter implements Callable {
  
    //1.定义成员变量
    private ChannelHandlerContext context; //事件处理器上下文对象 (存储handler信息,写操作)
    private String response; // 记录服务器返回的数据
    private RpcRequest param; //记录将要返送给服务器的数据
  
    //2.实现channelActive  客户端和服务器连接时,该方法就自动执行
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
      //初始化ChannelHandlerContext
      this.context = ctx;
    }
  
  
    //3.实现channelRead 当我们读到服务器数据,该方法自动执行
    @Override
    public synchronized void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      //将读到的服务器的数据msg ,设置为成员变量的值
      response = msg.toString();
      notify();
    }
  
    //4.将客户端的数写到服务器
    public synchronized Object call() throws Exception {
      //context给服务器写数据
      context.writeAndFlush(param);
      wait();
      return response;
    }
  
    //5.设置参数的方法
  
    public void setParam(RpcRequest param) {
      this.param = param;
    }
  }
```

  该事件处理器能监听到服务器返回的数据，并将请求的数据传输到服务端。

- 客户端启动类：

```
  public class ClientBoostrap {
  
    public static void main(String[] args) throws InterruptedException {
      RpcConsumer consumer = new RpcConsumer();
      for (;;) {
        Thread.sleep(2000);
        //生成需要调用的目标类的代理对象
        UserService userService = (UserService) consumer.createProxy(UserService.class);
        //使用目标代理对象调用目标方法，并获取响应内容
        String response = userService.sayHello("zhangsan");
        System.out.println(response);
      }
    }
  }
```

- 启动客户端启动类的main方法，可以看到控制台输出：

```
  sayHello
  UserService
  you are success:zhangsan
  sayHello
  UserService
  you are success:zhangsan
```

  证明远程调用已经成功，至此，简单的RPC远程调用框架完成。

## 4.	总结

消费者无需通过jar包的形式引入具体的实现项目，而是通过远程TCP通信的形式，以一定的协议和代理 通过接口直接调用了方法，实现远程service间的调用，是分布式服务的基础。目前还差一个重要的组件：注册发现中心，会在下一篇文章中进行集成。
