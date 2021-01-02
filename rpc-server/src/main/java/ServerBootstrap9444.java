import anno.Service;
import anno.SpringBootApplication;
import org.reflections.Reflections;

import java.util.Set;

/**
 * @author hgvgh
 * @version 1.0
 * @description
 * @date 2020/12/26
 */
@SpringBootApplication
public class ServerBootstrap9444 {

    public static void main(String[] args) throws IllegalAccessException, InstantiationException {
        //初始IOC容器
        initIoc();

        //初始化服务器监听
        UserServiceImpl.startServer("localhost", 9444);
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
