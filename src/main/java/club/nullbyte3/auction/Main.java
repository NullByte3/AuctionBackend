package club.nullbyte3.auction;

import io.javalin.Javalin;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Main {

    private final Map<Class<?>, Object> modules = new HashMap<>();

    public static void main(String[] args) {
        Javalin app = Javalin.create(/*config*/)
                .get("/", ctx -> ctx.result("Hello World"))
                .start(7070);
    }

    public <T> T getModule(Class<T> clazz) {
        if (this.modules.containsKey(clazz) && this.modules.get(clazz) != null) {
            return (T) this.modules.get(clazz);
        }

        try {
            T module = clazz.getDeclaredConstructor().newInstance();
            this.modules.putIfAbsent(clazz, module);

            return module;
        } catch (Exception ex) {
            log.error("Failed to instantiate module: {}", clazz.getName(), ex);
            return null;
        }
    }

}
