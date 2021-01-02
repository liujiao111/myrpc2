import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import org.I0Itec.zkclient.ZkClient;

import java.util.List;

public class ZkServerCron {

    public static void main(String[] args) throws InterruptedException {
        ZkClient zkClient = new ZkClient(ZkConstant.ZK_SERVER_STR);
        while (true) {
            //每隔5秒去连接服务器

            List<String> children = zkClient.getChildren(ZkConstant.RPC_PARENT_NODE_NAME);
            for (String child : children) {
                String ip = child.split("-")[0];
                String port = child.split("-")[1];
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
                            }
                        });
                long start = System.currentTimeMillis();
                //获取unix时间戳至今的秒数

                bootstrap.connect(ip, Integer.parseInt(port));
                long end = System.currentTimeMillis();
                long time = (end - start);

                //如果耗时超过5秒，则认为该节点失效，从ZK节点上删除
                if(time > 5000) {
                    zkClient.delete(ZkConstant.RPC_PARENT_NODE_NAME + "/" + ip + "-" + port);
                }

                //将该节点数据修改为耗时时间
                zkClient.writeData(ZkConstant.RPC_PARENT_NODE_NAME + "/" + ip + "-" + port, time + "");

            }
            Thread.sleep(5000);
        }
    }
}
