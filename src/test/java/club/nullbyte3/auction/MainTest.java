package club.nullbyte3.auction;

import club.nullbyte3.auction.impl.AuthManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MainTest {

    private static Application app;
    private static String baseUrl;
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String authToken;
    private static String username = "testuser-" + UUID.randomUUID();
    private static String password = "password123";

    @BeforeAll
    static void startServer() {
        app = new Application();
        app.start(0);
        baseUrl = "http://localhost:" + app.getApp().port();
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.shutdown();
        }
    }

    @Test
    @Order(1)
    void testRegister() throws IOException, InterruptedException {
        String requestBody = "username=" + username + "&password=" + password;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/register"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().length() > 2);
    }

    @Test
    @Order(2)
    void testLogin() throws IOException, InterruptedException {
        String requestBody = "username=" + username + "&password=" + password;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        authToken = response.body().replace("\"", "");
        assertNotNull(authToken);
        assertFalse(authToken.isEmpty());
    }

    @Test
    @Order(3)
    void testCreateItem() throws IOException, InterruptedException {
        assertNotNull(authToken, "Auth token is null, login failed?");

        String requestBody = "auth_token=" + authToken +
                "&item_name=Test Item" +
                "&item_price=99.99" +
                "&item_description=Iranian Nuke" +
                "&bid_increment=1.00";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/item"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        long itemId = Long.parseLong(response.body());
        assertTrue(itemId > 0);
    }

    @Test
    @Order(4)
    void testGetAllItems() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/item"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode items = objectMapper.readTree(response.body());
        assertTrue(items.isArray());
        assertTrue(items.size() > 0);
        assertEquals("Test Item", items.get(0).get("itemName").asText());
    }
}
