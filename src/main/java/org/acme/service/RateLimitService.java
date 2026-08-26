package org.acme.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import org.acme.entity.RateLimitEntry;

@ApplicationScoped
public class RateLimitService {

  private static final Duration WINDOW = Duration.ofHours(1);

  // Returns true if allowed (and records the attempt), false if the caller already
  // hit maxAttempts within the current window for this endpoint+ipAddress.
  public boolean allowRequest(String endpoint, String ipAddress, int maxAttempts) {
    // Find the existing entry and increment the attempt count
    RateLimitEntry entry =
        RateLimitEntry.find("endpoint = ?1 and ipAddress = ?2", endpoint, ipAddress).firstResult();
    Instant now = Instant.now();

    if (entry == null) {
      entry = new RateLimitEntry();
      entry.setEndpoint(endpoint);
      entry.setIpAddress(ipAddress);
      entry.setAttemptCount(1);
      entry.setWindowStart(now);
      entry.persist();
      return true;
    }

    if (entry.getWindowStart().plus(WINDOW).isBefore(now)) {
      entry.setAttemptCount(1);
      entry.setWindowStart(now);
      return true;
    }

    if (entry.getAttemptCount() >= maxAttempts) {
      return false;
    }

    entry.setAttemptCount(entry.getAttemptCount() + 1);
    return true;
  }
}
