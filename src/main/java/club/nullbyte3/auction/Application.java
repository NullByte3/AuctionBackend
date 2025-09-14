package club.nullbyte3.auction;

import club.nullbyte3.auction.impl.AuthManager;
import club.nullbyte3.auction.impl.DatabaseManager;
import club.nullbyte3.auction.impl.ItemManager;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Application {

    private final Map<Class<?>, AuctionBase> modules = new HashMap<>();

    public static void main(String[] args) {
        new Application().start();
    }

    public boolean start() {
        // Setup our modules.
        getModule(DatabaseManager.class);
        AuthManager authManager = getModule(AuthManager.class);
        ItemManager itemManager = getModule(ItemManager.class);

        // Send a signal to all modules to start up.
        modules.values().forEach(AuctionBase::enable);

        // Start up Javalin (creates a separate thread, so we don't have to do a while true loop).
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson())).start(7070);

        // Auth endpoints
        app.post("/auth/register", authManager::register);
        app.post("/auth/login", authManager::login);
        // Item endpoints
        app.get("/item", itemManager::getAllItems);
        app.get("/item/{id}", itemManager::getItemById);
        app.post("/item", itemManager::createItem);
        // Send a signal to all modules to shut down when we exit.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> modules.values().forEach(AuctionBase::disable)));
        return true;
    }

    /**
     * I've used this modular system in other complex projects, and it works super well. - Nullbyte3
     * This allows us to get an instance of any module we want, and if it doesn't exist, it will create it for us.
     * @param clazz The class of the module to get.
     * @return The module instance.
     * @param <T> The type of the module.
     */
    public <T extends AuctionBase> T getModule(Class<T> clazz) {
        if (this.modules.containsKey(clazz) && this.modules.get(clazz) != null) {
            return (T) this.modules.get(clazz);
        }

        try {
            T module = clazz.getDeclaredConstructor().newInstance();
            module.setInstance(this);
            this.modules.putIfAbsent(clazz, module);

            return module;
        } catch (Exception ex) {
            log.error("Failed to instantiate module: {}", clazz.getName(), ex);
            return null;
        }
    }
}
