import java.io.Serializable;
import java.util.Arrays;

public class RpcResponse implements Serializable {

    /**

     * 请求对象的ID

     */

    private String requestId;

    /**

     * 返回数据

     */

    private Object result;

    /**

     * 消息

     */

    private String message;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
