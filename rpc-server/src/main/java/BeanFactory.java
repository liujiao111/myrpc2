import java.util.HashMap;
import java.util.Set;

public class BeanFactory {

    private static HashMap<String, Object> beans = new HashMap<String, Object>();


    public static Object getInstance(String beanName) {
        return beans.get(beanName);
    }

    public static Object getInstanceByClassType(Object classType) {
        final Set<String> keys = beans.keySet();
        for (String key : keys) {
            final Object o = beans.get(key);
            if(o.getClass().equals(classType)) {
                return o;
            }
        }
        return null;
    }

    public static HashMap<String, Object> getBeans() {
        return beans;
    }


    public static void addInstance(String beanName, Object bean) {
        beans.put(beanName, bean);
    }

}
