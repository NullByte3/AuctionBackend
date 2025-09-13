package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Item;
import club.nullbyte3.auction.db.User;
import io.javalin.http.Context;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.util.List;

// TODO: Use slf4j for logging instead of System.out/err
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
            item.setItemPrice(new BigDecimal(ctx.formParam("item_price")));
            item.setItemDescription(ctx.formParam("item_description"));
            item.setBidIncrement(new BigDecimal(ctx.formParam("bid_increment")));
            item.setSeller(seller);

            session.save(item);
            session.getTransaction().commit();

            ctx.status(201).json(item.getId());
        } catch (Exception e) {
            ctx.status(400).result("Invalid item data: " + e.getMessage());
        }
    }
}
