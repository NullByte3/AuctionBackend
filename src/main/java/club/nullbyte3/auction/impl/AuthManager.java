package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.User;
import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.User;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

@Slf4j
public class AuthManager extends AuctionBase {

    private SessionFactory sessionFactory;

    @Override
    public void enable() {
        this.sessionFactory = find(DatabaseManager.class).getSessionFactory();
    }

    // TODO: Ensure password is strong enough.
    public void register(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        if (username == null || password == null) {
            ctx.status(400).result("Username and password are required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();

            User existingUser = session.createQuery("FROM User WHERE username = :username", User.class)
                    .setParameter("username", username)
                    .uniqueResult();

            if (existingUser != null) {
                ctx.status(409).result("User already exists.");
                return;
            }

            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
            String authToken = UUID.randomUUID().toString();

            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPasswordHash(passwordHash);
            newUser.setAuthToken(authToken);

            session.save(newUser);
            session.getTransaction().commit();

            ctx.json(authToken);
        }
    }

    public void login(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        if (username == null || password == null) {
            ctx.status(400).result("Username and password are required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            User user = session.createQuery("FROM User WHERE username = :username", User.class)
                    .setParameter("username", username)
                    .uniqueResult();

            if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
                ctx.status(401).result("Invalid credentials.");
                return;
            }
            ctx.json(user.getAuthToken());
        }
    }

    public void validate(Context ctx) {
        String token = ctx.formParam("token");

        if (token == null) {
            ctx.status(400).result("Token is required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            User user = session.createQuery("FROM User WHERE authToken = :token", User.class)
                    .setParameter("token", token)
                    .uniqueResult();

            if (user == null) {
                ctx.status(401).result("Invalid token.");
                return;
            }
            ctx.json(user);
        }
    }
}
