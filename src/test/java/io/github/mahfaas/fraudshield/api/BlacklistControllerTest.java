package io.github.mahfaas.fraudshield.api;


import io.github.mahfaas.fraudshield.blacklist.BlacklistEntity;
import io.github.mahfaas.fraudshield.blacklist.BlacklistService;
import io.github.mahfaas.fraudshield.blacklist.BlacklistType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BlacklistController.class)
class BlacklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlacklistService blacklistService;

    @Test
    @DisplayName("GET /api/v1/blacklist — should return all entries")
    void shouldGetAllEntries() throws Exception {
        BlacklistEntity entity = BlacklistEntity.builder()
                .id(1L).type(BlacklistType.IP).value("10.0.0.1")
                .reason("suspicious").createdAt(Instant.now()).build();

        when(blacklistService.findAll()).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/blacklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("10.0.0.1"))
                .andExpect(jsonPath("$[0].type").value("IP"));
    }

    @Test
    @DisplayName("GET /api/v1/blacklist/IP — should return entries by type")
    void shouldGetByType() throws Exception {
        BlacklistEntity entity = BlacklistEntity.builder()
                .id(1L).type(BlacklistType.IP).value("10.0.0.1")
                .reason("test").createdAt(Instant.now()).build();

        when(blacklistService.findByType(BlacklistType.IP)).thenReturn(List.of(entity));

        mockMvc.perform(get("/api/v1/blacklist/IP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("10.0.0.1"));
    }

    @Test
    @DisplayName("POST /api/v1/blacklist — should create entry")
    void shouldCreateEntry() throws Exception {
        BlacklistEntity saved = BlacklistEntity.builder()
                .id(1L).type(BlacklistType.BIN).value("427600")
                .reason("fraud").createdAt(Instant.now()).build();

        when(blacklistService.addEntry(eq(BlacklistType.BIN), eq("427600"), eq("fraud")))
                .thenReturn(saved);

        String json = """
                {"type":"BIN","value":"427600","reason":"fraud"}
                """;

        mockMvc.perform(post("/api/v1/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("427600"));
    }

    @Test
    @DisplayName("DELETE /api/v1/blacklist/IP/10.0.0.1 — should remove entry")
    void shouldDeleteEntry() throws Exception {
        mockMvc.perform(delete("/api/v1/blacklist/IP/10.0.0.1"))
                .andExpect(status().isNoContent());

        verify(blacklistService).removeEntry(BlacklistType.IP, "10.0.0.1");
    }

    @Test
    @DisplayName("POST /api/v1/blacklist — should return 400 on duplicate")
    void shouldReturn400OnDuplicate() throws Exception {
        when(blacklistService.addEntry(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Entry already exists"));

        String json = """
                {"type":"IP","value":"10.0.0.1","reason":"dup"}
                """;

        mockMvc.perform(post("/api/v1/blacklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Entry already exists"));
    }
}
