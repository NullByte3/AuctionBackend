package club.nullbyte3.auction.websocket;

import lombok.Data;

@Data
public class WsMessage<T> {
    private String subject;
    private T payload;
}
