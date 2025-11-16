package club.nullbyte3.auction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class AuctionBase {

    private Application instance;

    public void enable() {
        // This is intentionally left blank, to be overridden by subclasses.
        // This is just an interface method.
    }

    public void disable() {
        // This is intentionally left blank, to be overridden by subclasses.
        // This is just an interface method.
    }

    public <T extends AuctionBase> T find(Class<T> clazz) {
        return instance.getModule(clazz);
    }

}
