package de.leonard.flashcards.health;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataSource dataSource;

    @Test
    void returnsUpWhenDatabaseIsReachable() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("flashcard-generator"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void reportsDatabaseDownButStaysUpWhenConnectionFails() throws Exception {
        willThrow(new SQLException("connection refused")).given(dataSource).getConnection();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void allowsCrossOriginRequestsFromAngularDevServer() throws Exception {
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(anyInt())).willReturn(true);

        mockMvc.perform(get("/api/health")
                        .header("Origin", "http://localhost:4200")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
