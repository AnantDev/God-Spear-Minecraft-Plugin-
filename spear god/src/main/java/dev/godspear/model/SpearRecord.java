package dev.godspear.model;

import java.time.Instant;
import java.util.UUID;

public record SpearRecord(UUID spearId, UUID ownerId, String ownerName, SpearStage stage, int kills,
                          Instant createdAt, String pluginVersion, boolean destroyed) {
    public SpearRecord withProgress(SpearStage s, int k) { return new SpearRecord(spearId, ownerId, ownerName, s, k, createdAt, pluginVersion, destroyed); }
    public SpearRecord markDestroyed() { return new SpearRecord(spearId, ownerId, ownerName, stage, kills, createdAt, pluginVersion, true); }
}
