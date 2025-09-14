package club.nullbyte3.auction;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    @org.junit.jupiter.api.Test
    void testSoftware() {
        Application app = new Application();
        assertTrue(app.start());
    }


}