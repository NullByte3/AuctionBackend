package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Item;
import club.nullbyte3.auction.db.User;
import club.nullbyte3.auction.websocket.BidRequest;
import club.nullbyte3.auction.websocket.BidResponse;
import club.nullbyte3.auction.websocket.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Slf4j
public class BidManager extends AuctionBase implements Consumer<WsConfig> {
    private final Set<WsContext> subscribers = new HashSet<>();
    private SessionFactory sessionFactory;
    private ItemManager itemManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void enable() {
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.registerModule(new JavaTimeModule());
        this.sessionFactory = find(DatabaseManager.class).getSessionFactory();
        this.itemManager = find(ItemManager.class);
    }

    @Override
    public void accept(WsConfig wsConfig) {
        wsConfig.onConnect(ctx -> {
            log.info("WS: {} connected!", ctx.sessionId());
            subscribers.add(ctx);
        });
        wsConfig.onClose(ctx -> {
            log.info("WS: {} disconnected!", ctx.sessionId());
            subscribers.remove(ctx);
        });
        wsConfig.onMessage(this::onMessage);
    }

    private void onMessage(WsMessageContext ctx) {
        try {
            WsMessage wsMessage = ctx.messageAsClass(WsMessage.class);
            switch (wsMessage.getSubject()) {
                case "bid":
                    handleBidRequest(ctx, wsMessage);
                case "current_item":
                    handleCurrentItemRequest(ctx);
                default:
                    ctx.send("Unknown message type");
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            ctx.send("Error: " + e.getMessage());
        }
    }

    private void handleBidRequest(WsMessageContext ctx, WsMessage<BidRequest> wsMessage) {
        BidRequest bidRequest = wsMessage.getPayload();
        String authToken = bidRequest.getAuthtoken();
        Long itemId = bidRequest.getItem_id();

        if (authToken == null || itemId == null) {
            ctx.send("Auth token and item ID are required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            User user = session.createQuery("FROM User WHERE authToken = :authToken", User.class)
                    .setParameter("authToken", authToken)
                    .uniqueResult();

            if (user == null) {
                ctx.send("Invalid auth token.");
                return;
            }

            Item item = session.get(Item.class, itemId);
            if (item == null || !item.isActive()) {
                ctx.send("Item not found or is not active.");
                return;
            }
            if (bidRequest.getPrice() != null) { // bid-request.
                BigDecimal newPrice = bidRequest.getPrice();
                itemManager.placeBid(user, item, newPrice);
                broadcastPriceUpdate(item.getId(), newPrice);
            }
        }
    }

    @SneakyThrows
    private void handleCurrentItemRequest(WsMessageContext ctx) {
        Item item = AuctionManager.getCurrentItem();
        if (item != null) {
            ctx.send(objectMapper.writeValueAsString(item));
        } else {
            ctx.send("null");
        }
    }

    public void broadcastPriceUpdate(Long itemId, BigDecimal newPrice) {
        log.info("Broadcasting price update for item {} to {} subscribers", itemId, subscribers.size());
        BidResponse response = new BidResponse(newPrice);
        subscribers.forEach(subscriber -> {
            try {
                subscriber.send(objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                log.error("Failed to send price update to {}", subscriber.sessionId(), e);
            }
        });
    }

    public void broadcastNewAuction() {
        Item item = AuctionManager.getCurrentItem();
        System.out.println("Broadcasting new auction for item: " + item);
        if (item != null) {
            log.info("Broadcasting new auction for item {} to {} subscribers", item.getId(), subscribers.size());
            subscribers.forEach(subscriber -> {
                try {
                    subscriber.send(objectMapper.writeValueAsString(item));
                } catch (Exception e) {
                    log.error("Failed to send new auction to {}", subscriber.sessionId(), e);
                }
            });
        }
    }
}
