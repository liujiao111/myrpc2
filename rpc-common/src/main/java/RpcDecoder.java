import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

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
            System.out.println(s);
            System.out.println(substring);
            RpcRequest rpcRequest = new JSONSerializer().deserialize(RpcRequest.class, substring.getBytes());
            System.out.println(rpcRequest);
            list.add(rpcRequest); // 将数据添加进去
        }
    }
}
