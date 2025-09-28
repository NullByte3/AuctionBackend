package club.nullbyte3.auction.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsMessage<T> {
    private String subject;
    private T payload;
}
