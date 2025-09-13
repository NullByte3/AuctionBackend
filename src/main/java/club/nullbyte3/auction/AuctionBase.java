package club.nullbyte3.auction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class AuctionBase {

    private Main instance;

    public void enable() {

    }

    public void disable() {

    }

    public <T extends AuctionBase> T find(Class<T> clazz) {
        return instance.getModule(clazz);
    }

}
