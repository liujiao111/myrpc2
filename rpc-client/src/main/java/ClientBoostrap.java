/**
 * @author hgvgh
 * @version 1.0
 * @description
 * @date 2020/12/26
 */
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
