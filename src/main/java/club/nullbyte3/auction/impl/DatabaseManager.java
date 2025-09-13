package club.nullbyte3.auction.impl;

import club.nullbyte3.auction.AuctionBase;
import lombok.Getter;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// TODO: Use slf4j for logging instead of System.out/err
public class DatabaseManager extends AuctionBase {

    @Getter
    private SessionFactory sessionFactory;

    @Override
    public void enable() {
        try {
            Configuration configuration = new Configuration().configure();
            ensureDatabaseExists(configuration);
            sessionFactory = configuration.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Failed to create sessionFactory object." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private void ensureDatabaseExists(Configuration configuration) {
        Properties props = configuration.getProperties();
        String url = props.getProperty("hibernate.connection.url");
        String user = props.getProperty("hibernate.connection.username");
        String pass = props.getProperty("hibernate.connection.password");

        int lastSlash = url.lastIndexOf('/');
        if (lastSlash == -1 || lastSlash + 1 == url.length()) {
            System.err.println("Could not determine database name from URL: " + url);
            return;
        }

        String dbName = url.substring(lastSlash + 1);
        String baseUrl = url.substring(0, lastSlash + 1) + "postgres"; // default database, but we switch.

        try (Connection conn = DriverManager.getConnection(baseUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            boolean dbExists = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + dbName + "'").next();
            if (!dbExists) {
                System.out.println("Database '" + dbName + "' does not exist. Creating it...");
                stmt.executeUpdate("CREATE DATABASE " + dbName);
                System.out.println("Database '" + dbName + "' created successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Error while trying to create database: " + e.getMessage());
        }
    }

    @Override
    public void disable() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
