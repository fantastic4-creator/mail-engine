package com.mailengine.api;

import com.mailengine.data.PlatformStateStore;
import com.mailengine.domain.SuppressionRecord;
import com.mailengine.service.UnsubscribeTokenService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UnsubscribeController {

    private final UnsubscribeTokenService unsubscribeTokenService;
    private final PlatformStateStore store;

    public UnsubscribeController(UnsubscribeTokenService unsubscribeTokenService, PlatformStateStore store) {
        this.unsubscribeTokenService = unsubscribeTokenService;
        this.store = store;
    }

    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(
            @RequestParam UUID tenant,
            @RequestParam String email,
            @RequestParam UUID campaign,
            @RequestParam String sig) {
        if (!unsubscribeTokenService.validateToken(tenant, email, campaign, sig)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid unsubscribe token");
        }
        store.saveSuppression(new SuppressionRecord(
                UUID.randomUUID(), tenant, email, "Unsubscribed", Instant.now()));
        return ResponseEntity.ok("You have been unsubscribed.");
    }
}
