import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author hgvgh
 * @version 1.0
 * @description
 * @date 2020/12/26
 */
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

    //从ZK服务器上获取服务端IP端口列表
    ZkClient zkClient = new ZkClient(ZkConstant.ZK_SERVER_STR);
    List<String> children = zkClient.getChildren(ZkConstant.RPC_PARENT_NODE_NAME);

    //根据ZK中存储的服务端节点列表启动RPC服务端连接
    startServerByZkNodeList(children, zkClient);

    //注册该节点监听，如果下面的子节点列表发生变化，则重新进行服务端连接
    zkClient.subscribeChildChanges(ZkConstant.RPC_PARENT_NODE_NAME, new IZkChildListener() {
      public void handleChildChange(String parentPath, List<String> currentChilds) throws Exception {
        System.out.println("检测到有子节点发生改变，重新获取子节点列表");
        startServerByZkNodeList(currentChilds, zkClient);
      }
    });
  }

  /**
   * 根据ZK中存储的服务端节点列表启动RPC服务端连接
   * @param children
   */
  private static Bootstrap startServerByZkNodeList(List<String> children, ZkClient zkClient) throws InterruptedException {
    System.out.println(children);
    userClientHandler = new UserClientHandler();
    Bootstrap bootstrap = new Bootstrap();
    //2)创建连接池对象
    EventLoopGroup group = new NioEventLoopGroup();
    //3)创建客户端的引导对象
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

    //获取节点数据(服务器响应时间)
    String ip = null;
    int port = 0;
    int chooseTime = Integer.MAX_VALUE ;
    for (String child : children) {
      Object o = zkClient.readData(ZkConstant.RPC_PARENT_NODE_NAME + "/" + child);
      if(o == null) {
        continue;
      }
      int time = Integer.parseInt(o.toString());
      if(time < chooseTime) {
        chooseTime = time;
        ip = child.split("-")[0];
        port = Integer.parseInt(child.split("-")[1]);
      }
    }


   //如果ZK服务器上还未保存服务端的响应时间信息，则随机选择一台服务端进行绑定
    if(chooseTime == 0 || ip == null) {
      //随机选择一台服务器进行绑定
      int i = new Random().nextInt(children.size());
      String randomServerStr = children.get(i);
      ip = randomServerStr.split("-")[0];
      port = Integer.parseInt(randomServerStr.split("-")[1]);
    }

    bootstrap.connect(ip, port).sync();
    System.out.println("绑定了>..." + ip + port);
    return bootstrap;
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

            //调用类名约定：
            String className = serviceClass.getName().substring(0, 1).toLowerCase() + serviceClass.getName().substring(1);
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
