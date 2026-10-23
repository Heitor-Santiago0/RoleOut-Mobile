package br.com.roleout.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpoPushGatewayTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void distinguishesTicketsReceiptsAndProviderFailures() throws Exception {
        assertEquals("abc",ExpoPushGateway.parse(json.readTree("{\"status\":\"ok\",\"id\":\"abc\"}"),true).ticket());
        assertThrows(java.io.IOException.class,()->ExpoPushGateway.parse(json.readTree("{\"status\":\"ok\"}"),true));
        assertEquals("ok",ExpoPushGateway.parse(json.readTree("{\"status\":\"ok\"}"),false).status());
        assertEquals("DeviceNotRegistered",ExpoPushGateway.parse(json.readTree("{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}"),true).error());
        assertThrows(java.io.IOException.class,()->ExpoPushGateway.parse(json.readTree("{}"),false));
    }
}
