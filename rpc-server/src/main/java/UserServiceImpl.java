import anno.Service;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.server.ZKDatabase;

/**
 * @author hgvgh
 * @version 1.0
 * @description
 * @date 2020/12/26
 */
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

    //将节点IP和端口注册到ZK上
    binIpAndPortToZk(hostName, port);
  }

  private static void binIpAndPortToZk(String hostName, int port) {
    ZkClient zkClient = new ZkClient(ZkConstant.ZK_SERVER_STR);
    //创建一个临时节点，节点名称：IP-端口号
    if(! zkClient.exists(ZkConstant.RPC_PARENT_NODE_NAME + "/" + hostName + "-" + port)) {
      zkClient.createEphemeral( ZkConstant.RPC_PARENT_NODE_NAME + "/" + hostName + "-" + port);
    }

    System.out.println(" create zk node in zk, node name :" + ZkConstant.RPC_PARENT_NODE_NAME + "/" + hostName + "-" + port);
  }
}
