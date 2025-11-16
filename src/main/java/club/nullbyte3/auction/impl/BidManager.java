package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Item;
import club.nullbyte3.auction.db.User;
import club.nullbyte3.auction.websocket.BidRequest;
import club.nullbyte3.auction.websocket.BidResponse;
import club.nullbyte3.auction.websocket.BidsResponse;
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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
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
            ctx.enableAutomaticPings(3, TimeUnit.SECONDS);
            log.info("WS: {} connected!", ctx.sessionId());
            Item item = AuctionManager.getCurrentItem();
            if (item != null) {
                WsMessage<Item> msg = new WsMessage<>("current_item", item);
                ctx.send(objectMapper.writeValueAsString(msg));
                WsMessage<BidsResponse> priceMsg = new WsMessage<>("current_bids",
                        new BidsResponse(getTotalBids(item)));
                ctx.send(objectMapper.writeValueAsString(priceMsg));
            }
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
                    break;
                case "current_item":
                    handleCurrentItemRequest(ctx);
                    break;
                default:
                    ctx.send("Unknown message type");
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            ctx.send("Error: " + e.getMessage());
        }
    }

    private void handleBidRequest(WsMessageContext ctx, WsMessage<BidRequest> wsMessage) {
        BidRequest bidRequest = objectMapper.convertValue(wsMessage.getPayload(), BidRequest.class);
        String authToken = bidRequest.getAuthtoken();

        if (authToken == null) {
            ctx.send("Auth token is  required.");
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

            Item item = AuctionManager.getCurrentItem();
            if (item == null || !item.isActive()) {
                ctx.send("Item not found or is not active.");
                return;
            }
            if (bidRequest.getPrice() != null) { // bid-request.
                BigDecimal newPrice = itemManager.placeBid(user, item);
                AuctionManager.resetTimer();
                broadcastTimerReset();
                broadcastPriceUpdate(item.getId(), newPrice, user);
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

    public void broadcastPriceUpdate(Long itemId, BigDecimal newPrice, User bidder) {
        log.info("Broadcasting price update for item {} to {} subscribers", itemId, subscribers.size());
        BidResponse response = new BidResponse(newPrice, bidder.getUsername(), bidder.getId());
        subscribers.forEach(subscriber -> {
            try {
                WsMessage<BidResponse> msg = new WsMessage<>("price_update", response);
                subscriber.send(objectMapper.writeValueAsString(msg));
            } catch (Exception e) {
                log.error("Failed to send price update to {}", subscriber.sessionId(), e);
            }
        });
    }

    public void broadcastNewAuction() {
        Item item = AuctionManager.getCurrentItem();
        log.info("Broadcasting new auction for item: {}", item);
        if (item != null) {
            log.info("Broadcasting new auction for item {} to {} subscribers", item.getId(), subscribers.size());
            subscribers.forEach(subscriber -> {
                try {
                    WsMessage<Item> msg = new WsMessage<>("new_auction", item);
                    subscriber.send(objectMapper.writeValueAsString(msg));
                } catch (Exception e) {
                    log.error("Failed to send new auction to {}", subscriber.sessionId(), e);
                }
            });
        }
    }

    public void broadcastAuctionEnd(Item item) {
        if (item != null) {
            subscribers.forEach(subscriber -> {
                try {
                    WsMessage<Item> msg = new WsMessage<>("auction_end", item);
                    subscriber.send(objectMapper.writeValueAsString(msg));
                } catch (Exception e) {
                    log.error("Failed to send end auction to {}", subscriber.sessionId(), e);
                }
            });
        }
    }

    public void broadcastTimerReset() {
        Item item = AuctionManager.getCurrentItem();
        if (item != null) {
            subscribers.forEach(subscriber -> {
                try {
                    WsMessage<String> msg = new WsMessage<>("timer_update", item.getEndAt().toString());
                    subscriber.send(objectMapper.writeValueAsString(msg));
                } catch (Exception e) {
                    log.error("Failed to send timer reset to {}", subscriber.sessionId(), e);
                }
            });
        }
    }

    public User getHighestBidder(Item item) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            // refresh item to get the latest from db.
            session.refresh(item);
            User topBidder = session.createQuery(
                            "SELECT b.user FROM Bid b WHERE b.item = :item ORDER BY b.price DESC", User.class)
                    .setParameter("item", item)
                    .setMaxResults(1)
                    .getSingleResult();
            session.getTransaction().commit();
            return topBidder;
        } catch (Exception e) {
            return null;
        }
    }

    public BidResponse[] getTotalBids(Item item) {
        try (Session session = sessionFactory.openSession())  {
            session.beginTransaction();
            session.refresh(item);
            List<Object[]> items = session.createQuery(
                            "SELECT b.price, b.user FROM Bid b WHERE b.item = :item", Object[].class)
                    .setParameter("item", item)
                    .setMaxResults(20)
                    .getResultList();
            BidResponse[] bids = items.stream()
                    .map(row -> new BidResponse((BigDecimal) row[0], ((User) row[1]).getUsername(), ((User) row[1]).getId()))
                    .toArray(BidResponse[]::new);
            return bids;
        } catch (Exception e) {
            return new BidResponse[0];
        }
    }

    public void updateItem(Item item) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.update(item);
            session.getTransaction().commit();
        } catch (Exception e) {
            log.error("Failed to update item {}", item.getId(), e);
        }
    }
}
