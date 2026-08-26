package org.acme.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "RateLimitEntry",
    uniqueConstraints = @UniqueConstraint(columnNames = {"endpoint", "ipAddress"}))
public class RateLimitEntry extends PanacheEntity {
  @Column(nullable = false)
  private String endpoint;

  @Column(nullable = false)
  private String ipAddress;

  @Column(nullable = false)
  private int attemptCount;

  @Column(nullable = false)
  private Instant windowStart;

  // Getters
  public String getEndpoint() {
    return endpoint;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getWindowStart() {
    return windowStart;
  }

  // Setters
  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public void setAttemptCount(int attemptCount) {
    this.attemptCount = attemptCount;
  }

  public void setWindowStart(Instant windowStart) {
    this.windowStart = windowStart;
  }
}
