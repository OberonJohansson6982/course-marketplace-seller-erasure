package dev.learning;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SellerDeletion {
    private final InfraiAccess infrai;
    private final String activeKeyId;

    public SellerDeletion(InfraiAccess infrai, @Value("${infrai.active-key-id}") String activeKeyId) {
        this.infrai = infrai;
        this.activeKeyId = activeKeyId;
    }

    public record Result(String sellerId, String successorId, MarketplaceHandoff.Plan handoff,
                         List<String> revokedSessions, String revokedCredentialId, String state) {}

    @PostMapping("/marketplace/sellers/delete")
    public Result delete(@RequestBody MarketplaceHandoff.Request request) {
        MarketplaceHandoff.Plan handoff = MarketplaceHandoff.plan(request);
        if (request.credentialId().equals(activeKeyId)) {
            throw new IllegalArgumentException("Use a distinct service credential to revoke the seller credential");
        }
        JsonNode data = infrai.sessions(request.sellerId());
        JsonNode sessions = data.path("items");
        if (!sessions.isArray()) throw new IllegalStateException("Expected a session collection");
        List<String> revoked = new ArrayList<>();
        for (JsonNode session : sessions) {
            String id = session.path("id").asText("");
            if (id.isBlank()) throw new IllegalStateException("Session id is required");
            infrai.revokeSession(id);
            revoked.add(id);
        }
        infrai.revokeCredential(request.credentialId());
        // The handoff is the local marketplace transition; persist it in your course/order store before retiring the seller record.
        return new Result(request.sellerId(), request.successorId(), handoff,
                List.copyOf(revoked), request.credentialId(), "READY_FOR_LOCAL_ERASURE");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(InfraiAccess.InfraiRejection.class)
    public ResponseEntity<String> rejected(InfraiAccess.InfraiRejection e) {
        int status = e.status() >= 400 && e.status() < 500 ? e.status() : 502;
        return ResponseEntity.status(status).body(e.code() + ": " + e.getMessage());
    }
}
