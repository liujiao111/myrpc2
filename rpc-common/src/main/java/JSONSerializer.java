import com.alibaba.fastjson.JSON;

import java.io.IOException;

public class JSONSerializer implements Serializer {
    public byte[] serialize(Object object) throws IOException {
        return JSON.toJSONBytes(object);
    }

    public <T> T deserialize(Class<T> clazz, byte[] bytes) throws IOException {
        return JSON.parseObject(bytes, clazz);
    }
}
