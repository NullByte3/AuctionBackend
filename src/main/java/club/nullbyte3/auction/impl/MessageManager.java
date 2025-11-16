package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import club.nullbyte3.auction.db.Message;
import club.nullbyte3.auction.db.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MessageManager extends AuctionBase {
    private SessionFactory sessionFactory;

    @Override
    public void enable() {
        this.sessionFactory = find(DatabaseManager.class).getSessionFactory();
        loadDefaultMessages(); // Loads up default messages from messages.json in the resources folder.
    }

    private void loadDefaultMessages() {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<Map<String, Map<String, String>>> typeRef = new TypeReference<>() {};
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("messages.json")) {
            if (in == null) {
                log.warn("messages.json not found, skipping this task.");
                return;
            }

            Map<String, Map<String, String>> languages = objectMapper.readValue(in, typeRef);
            try (Session session = sessionFactory.openSession()) {
                Transaction tx = session.beginTransaction();
                for (Map.Entry<String, Map<String, String>> langEntry : languages.entrySet()) {
                    String language = langEntry.getKey();
                    for (Map.Entry<String, String> messageEntry : langEntry.getValue().entrySet()) {
                        String key = messageEntry.getKey();
                        String value = messageEntry.getValue();

                        // if exists, skip.
                        long count = session.createQuery("SELECT count(*) FROM Message WHERE language = :lang AND messageKey = :key", Long.class)
                                .setParameter("lang", language)
                                .setParameter("key", key)
                                .getSingleResult();

                        if (count == 0) {
                            Message message = new Message();
                            message.setLanguage(language);
                            message.setMessageKey(key);
                            message.setMessageValue(value);
                            session.save(message);
                            log.info("Loaded default message: lang={}, key={}", language, key);
                        }
                    }
                }
                tx.commit();
            }
        } catch (Exception e) {
            log.error("Failed to load default messages from messages.json", e);
        }
    }

    public void getMessagesByLanguage(Context ctx) {
        String lang = ctx.pathParam("lang");
        try (Session session = sessionFactory.openSession()) {
            List<Message> messages = session.createQuery("FROM Message WHERE language = :lang", Message.class)
                    .setParameter("lang", lang)
                    .list();
            Map<String, String> messageMap = messages.stream()
                    .collect(Collectors.toMap(Message::getMessageKey, Message::getMessageValue, (existing, replacement) -> replacement));
            ctx.json(messageMap);
        } catch (Exception e) {
            log.error("Error getting messages for language {}", lang, e);
            ctx.status(500).result("Internal server error");
        }
    }

    public void createMessage(Context ctx) {
        String authToken = ctx.formParam("auth_token");
        if (authToken == null) {
            ctx.status(401).result("Auth token is required.");
            return;
        }

        String language = ctx.formParam("language");
        String messageKey = ctx.formParam("message_key");
        String messageValue = ctx.formParam("message_value");

        if (language == null || messageKey == null || messageValue == null) {
            ctx.status(400).result("language, message_key, and message_value are required.");
            return;
        }

        try (Session session = sessionFactory.openSession()) {
            // WARNING: no admin check, because we don't have roles yet.
            User user = session.createQuery("FROM User WHERE authToken = :authToken", User.class)
                    .setParameter("authToken", authToken)
                    .uniqueResult();

            if (user == null) {
                ctx.status(401).result("Invalid auth token.");
                return;
            }

            Message message = new Message();
            message.setLanguage(language);
            message.setMessageKey(messageKey);
            message.setMessageValue(messageValue);

            session.beginTransaction();
            session.save(message);
            session.getTransaction().commit();

            ctx.status(201).json(message);
        } catch (Exception e) {
            log.error("Error creating message", e);
            ctx.status(500).result("Internal server error");
        }
    }
}
