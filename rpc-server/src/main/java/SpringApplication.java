public class SpringApplication {

    public static void run(Class<ServerBootstrap> serverBootstrapClass, String[] args) {
        UserServiceImpl.startServer("localhost", 8999);
    }
}
