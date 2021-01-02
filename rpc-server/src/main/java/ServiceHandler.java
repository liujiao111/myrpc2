import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.EventExecutorGroup;

import java.lang.reflect.Method;

/**
 * @author hgvgh
 * @version 1.0
 * @description
 * @date 2020/12/26
 */
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
