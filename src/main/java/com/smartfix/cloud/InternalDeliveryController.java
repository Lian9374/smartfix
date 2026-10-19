package com.smartfix.cloud;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("distributed")
@RequestMapping("/internal/deliveries")
public class InternalDeliveryController {
    private final DeliveryCompletionService service;
    public InternalDeliveryController(DeliveryCompletionService service) { this.service = service; }
    public record Completion(@NotBlank @Size(max=200) String dedupKey, @Pattern(regexp="DELIVERED|SKIPPED") @NotNull String outcome) {}
    @PostMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable long id, @Valid @RequestBody Completion completion) {
        try { service.complete(id, completion.dedupKey(), completion.outcome()); }
        catch (IllegalArgumentException invalid) { return ResponseEntity.badRequest().build(); }
        return ResponseEntity.noContent().build();
    }
}
