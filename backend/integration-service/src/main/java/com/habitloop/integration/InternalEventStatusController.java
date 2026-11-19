package com.habitloop.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class InternalEventStatusController {
    private final PartnerEventRepository events;
    InternalEventStatusController(PartnerEventRepository events) { this.events = events; }

    @PutMapping("/internal/integrations/events/{id}/status")
    void updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate request) {
        PartnerEventEntity event = events.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!List.of("PROCESSED", "REJECTED").contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be PROCESSED or REJECTED");
        }
        event.status = request.status(); event.rejectReason = request.rejectReason(); events.save(event);
    }

    public record StatusUpdate(@NotBlank String status, String rejectReason) {}
}
