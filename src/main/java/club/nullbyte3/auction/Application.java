package club.nullbyte3.auction;

import club.nullbyte3.auction.impl.AuthManager;
import club.nullbyte3.auction.impl.DatabaseManager;
import club.nullbyte3.auction.impl.ItemManager;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class Application {

    private final Map<Class<?>, AuctionBase> modules = new HashMap<>();
    @Getter
    private Javalin app;

    public static void main(String[] args) {
        new Application().start(7070);
    }

    public boolean start(int port) {
        // Setup our modules.
        getModule(DatabaseManager.class);
        AuthManager authManager = getModule(AuthManager.class);
        ItemManager itemManager = getModule(ItemManager.class);

        // Send a signal to all modules to start up.
        // We need to enable the database manager first, as other modules depend on it.
        getModule(DatabaseManager.class).enable();
        modules.values().stream().filter(m -> !(m instanceof DatabaseManager)).forEach(AuctionBase::enable);

        // Start up Javalin (creates a separate thread, so we don't have to do a while true loop).
        app = Javalin.create(config -> config.jsonMapper(new JavalinJackson())).start(port);

        // Auth endpoints
        app.post("/auth/register", authManager::register);
        app.post("/auth/login", authManager::login);
        // Item endpoints
        app.get("/item", itemManager::getAllItems);
        app.get("/item/{id}", itemManager::getItemById);
        app.post("/item", itemManager::createItem);
        // Send a signal to all modules to shut down when we exit.
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        return true;
    }

    public void shutdown() {
        if (app != null) {
            app.stop();
        }
        modules.values().forEach(AuctionBase::disable);
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
