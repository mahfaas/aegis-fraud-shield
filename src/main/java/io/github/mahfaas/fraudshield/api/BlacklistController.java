package io.github.mahfaas.fraudshield.api;

import io.github.mahfaas.fraudshield.blacklist.BlacklistEntity;
import io.github.mahfaas.fraudshield.blacklist.BlacklistService;
import io.github.mahfaas.fraudshield.blacklist.BlacklistType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing IP and BIN blacklists.
 */
@RestController
@RequestMapping("/api/v1/blacklist")
@RequiredArgsConstructor
@Tag(name = "Blacklist", description = "CRUD operations for IP and BIN blacklists")
public class BlacklistController {

    private final BlacklistService blacklistService;

    @GetMapping
    @Operation(summary = "Get all blacklist entries")
    public List<BlacklistEntity> getAll() {
        return blacklistService.findAll();
    }

    @GetMapping("/{type}")
    @Operation(summary = "Get blacklist entries by type (IP or BIN)")
    public List<BlacklistEntity> getByType(@PathVariable BlacklistType type) {
        return blacklistService.findByType(type);
    }

    @PostMapping
    @Operation(summary = "Add a new blacklist entry")
    @ResponseStatus(HttpStatus.CREATED)
    public BlacklistEntity add(@RequestBody BlacklistRequest request) {
        return blacklistService.addEntry(request.type(), request.value(), request.reason());
    }

    @DeleteMapping("/{type}/{value}")
    @Operation(summary = "Remove a blacklist entry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable BlacklistType type, @PathVariable String value) {
        blacklistService.removeEntry(type, value);
    }

    /**
     * Request body for adding a blacklist entry.
     */
    public record BlacklistRequest(BlacklistType type, String value, String reason) {}
}
