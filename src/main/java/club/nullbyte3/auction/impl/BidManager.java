package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Item;
import club.nullbyte3.auction.db.User;
import club.nullbyte3.auction.websocket.BidRequest;
import club.nullbyte3.auction.websocket.BidResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsMessageContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Slf4j
public class BidManager extends AuctionBase implements Consumer<WsConfig> {

    private final Map<Long, Set<WsContext>> itemSubscriptions = new ConcurrentHashMap<>();
    private SessionFactory sessionFactory;
    private ItemManager itemManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void enable() {
        this.sessionFactory = find(DatabaseManager.class).getSessionFactory();
        this.itemManager = find(ItemManager.class);
    }

    @Override
    public void accept(WsConfig wsConfig) {
        wsConfig.onConnect(ctx -> log.info("WS: {} connected!", ctx.sessionId()));
        wsConfig.onClose(ctx -> {
            log.info("WS: {} disconnected!", ctx.sessionId());
            itemSubscriptions.values().forEach(subscribers -> subscribers.remove(ctx));
        });
        wsConfig.onMessage(this::onMessage);
    }

    private void onMessage(WsMessageContext ctx) {
        try {
            BidRequest bidRequest = ctx.messageAsClass(BidRequest.class);
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
                if (bidRequest.getPrice() == null) { // subscribe-request.
                    itemSubscriptions.computeIfAbsent(itemId, k -> new CopyOnWriteArraySet<>()).add(ctx);
                    log.info("User {} subscribed to item {}", user.getUsername(), itemId);
                } else { // bid-request.
                    BigDecimal newPrice = bidRequest.getPrice();
                    itemManager.placeBid(user, item, newPrice);
                    broadcastPriceUpdate(item.getId(), newPrice);
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            ctx.send("Error: " + e.getMessage());
        }
    }

    public void broadcastPriceUpdate(Long itemId, BigDecimal newPrice) {
        Set<WsContext> subscribers = itemSubscriptions.get(itemId);
        if (subscribers != null) {
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
    }
}
