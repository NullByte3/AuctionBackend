package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Item;
import club.nullbyte3.auction.db.User;
import club.nullbyte3.auction.db.Bid;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

// TODO: Hide the seller auth token and password hash when returning item data.
@Slf4j
public class ItemManager extends AuctionBase {

    private SessionFactory sessionFactory;

    @Override
    public void enable() {
        this.sessionFactory = find(DatabaseManager.class).getSessionFactory();
    }

    public void getAllItems(Context ctx) {
        try (Session session = sessionFactory.openSession()) {
            List<Item> items = session.createQuery("FROM Item WHERE isActive = true", Item.class).list();
            ctx.json(items);
        }
    }

    public void getItemById(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        try (Session session = sessionFactory.openSession()) {
            Item item = session.get(Item.class, id);
            if (item != null) {
                ctx.json(item);
            } else {
                ctx.status(404).result("Item not found");
            }
        }
    }

    public void createItem(Context ctx) {
        String authToken = ctx.formParam("auth_token");
        if (authToken == null) {
            ctx.status(401).result("Auth token is required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            User seller = session.createQuery("FROM User WHERE authToken = :authToken", User.class)
                    .setParameter("authToken", authToken)
                    .uniqueResult();

            if (seller == null) {
                ctx.status(401).result("Invalid auth token.");
                return;
            }

            Item item = new Item();
            item.setItemName(ctx.formParam("item_name"));
            item.setItemImage(ctx.formParam("item_image"));
            BigDecimal price = new BigDecimal(Objects.requireNonNull(ctx.formParam("item_price")));
            item.setItemPrice(price);
            item.setItemDescription(ctx.formParam("item_description"));
            item.setBidIncrement(new BigDecimal(Objects.requireNonNull(ctx.formParam("bid_increment"))));
            item.setSeller(seller);

            session.save(item);
            session.getTransaction().commit();

            AuctionManager.addItem(item);
            ctx.status(201).json(item.getId());
        } catch (Exception e) {
            log.error("Failed to create item", e);
            ctx.status(400).result("Invalid item data: " + e.getMessage());
        }
    }

    public BigDecimal placeBid(User user, Item item) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            // refresh item to get the latest from db.
            session.refresh(item);
            BigDecimal currentBid = session.createQuery(
                            "SELECT MAX(b.price) FROM Bid b WHERE b.item = :item", BigDecimal.class)
                    .setParameter("item", item)
                    .uniqueResultOptional()
                    .orElse(item.getItemPrice());

            BigDecimal minimumBid = currentBid.add(item.getBidIncrement());
            BigDecimal newPrice = minimumBid; // Currently we only accept bidding the next increment
            if (newPrice.compareTo(minimumBid) < 0) {
                throw new IllegalArgumentException("Bid must be at least " + minimumBid);
            }
            Bid bid = new Bid();
            bid.setItem(item);
            bid.setUser(user);
            bid.setPrice(newPrice);
            session.save(bid);
            session.getTransaction().commit();
            return newPrice;
        } catch (Exception e) {
            log.error("Failed to place bid", e);
            throw e;
        }
    }
}
