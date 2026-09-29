package com.sprintlog.sprintlogboot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User user;

  @Column(nullable = false)
  private Instant expiresAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private RevokedReason revokedReason;

  public enum RevokedReason {
    ROTATED,
    LOGGED_OUT,
    REVOKED_ALL
  }

  public RefreshToken(String tokenHash, User user, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.user = user;
    this.expiresAt = expiresAt;
  }

  public void revoke(RevokedReason reason) {
    this.revokedReason = reason;
  }

  public boolean isRevoked() {
    return revokedReason != null;
  }

  public boolean isRotated() {
    return revokedReason == RevokedReason.ROTATED;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  public boolean isUsable(Instant now) {
    return !isRevoked() && !isExpired(now);
  }
}
