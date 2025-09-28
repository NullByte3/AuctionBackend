package club.nullbyte3.auction.websocket;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BidRequest {
    private String authtoken;
    private BigDecimal price;
}
