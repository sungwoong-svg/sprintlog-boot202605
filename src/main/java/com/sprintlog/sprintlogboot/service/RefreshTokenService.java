package com.sprintlog.sprintlogboot.service;


import com.sprintlog.sprintlogboot.config.JwtProperties;
import com.sprintlog.sprintlogboot.domain.RefreshToken;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private final RefreshTokenRepository repository;
  private final JwtProperties properties;
  private final Clock clock;

  // 난수 생성용 객체. Math.random(), UUID.randomUUID() -> 크기가 작습니다.
  private final SecureRandom random = new SecureRandom();

  // 새 Refresh Token 발급 - 로그인 성공 시, 그리고 회전할 때마다.
  @Transactional
  public String issue(User user) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    Instant expiresAt = clock.instant().plus(properties.getRefreshTokenValidity());
    repository.save(new RefreshToken(hash(raw), user, expiresAt));
    return raw;
  }

  // 리프레시 토큰을 재발급하는 로직. 디비에서 조회 후 검사(만료, 무효화), 새롭게 발급
  @Transactional
  public Outcome rotate(String rawToken) {

    RefreshToken found = repository.findByTokenHash(hash(rawToken)).orElse(null);
    if (found == null) {
      log.warn("[REFRESH] 알 수 없는 토큰으로 재발급 시도");
      return Outcome.notFound();
    }

    if (found.isRotated()) {
      // 재사용 감지 — 정상 흐름에서는 절대 오지 않는 경로다.
      // 이메일을 '먼저' 읽어둔다. 아래 @Modifying 쿼리가 clearAutomatically 로
      // 영속성 컨텍스트를 비우면서 LAZY 프록시가 끊기기 때문이다(순서를 바꾸면 LazyInitializationException).
      String owner = found.getUser().getEmail();
      int killed = repository.revokeAllByUser(found.getUser());
      log.error("[REFRESH] ⚠ 재사용 감지 — 탈취 의심. user={}, 무효화한 토큰={}개", owner, killed);
      return Outcome.reuseDetected();
    }

    if (found.isRevoked()) {
      // 회전이 아닌 사유로 죽은 토큰(로그아웃·일괄 무효화) — 그냥 거부한다. 탈취 신호가 아니다.
      log.info("[REFRESH] 무효화된 토큰으로 재발급 시도 - 사유={}", found.getRevokedReason());
      return Outcome.notFound();
    }

    if (found.isExpired(clock.instant())) {
      log.info("[REFRESH] 만료된 토큰으로 재발급 시도 - user={}", found.getUser().getEmail());
      return Outcome.expired();
    }

    // 회전: 옛 것은 '지우지 않고' 무효화만 한다 — 흔적이 있어야 재사용을 감지할 수 있다.
    found.revoke(RefreshToken.RevokedReason.ROTATED);
    return Outcome.success(found.getUser(), issue(found.getUser()));
  }

  /** 로그아웃 — 지금 쓰던 Refresh Token 하나만 끊는다(다른 기기는 살려둔다). */
  @Transactional
  public void revokeOne(String rawToken) {
    repository.findByTokenHash(hash(rawToken))
        .ifPresent(t -> t.revoke(RefreshToken.RevokedReason.LOGGED_OUT));
  }

  /** 이 사용자의 모든 재발급 권리를 끊는다 — 비밀번호 변경·권한 변경·강제 로그아웃. */
  @Transactional
  public int revokeAll(User user) {
    String owner = user.getEmail();          // clearAutomatically 전에 읽어둔다(위 주석 참조)
    int killed = repository.revokeAllByUser(user);
    if (killed > 0) {
      log.info("[REFRESH] 전체 무효화 - user={}, {}개", owner, killed);
    }
    return killed;
  }

  /**
   * SHA-256 해시 — 저장·조회에 쓴다.
   *   '같은 입력 → 같은 출력' 이라 조회가 가능하고, 고엔트로피 난수라 대입 위험이 없다.
   *   (비밀번호는 반대 성질이라 BCrypt 를 쓴다 — RefreshToken 엔티티 주석 참조.)
   */
  private String hash(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 을 쓸 수 없는 환경입니다.", e);
    }
  }

  // 회전의 결과를 담을 레코드
  public record Outcome(Status status, User user, String refreshToken) {

    public enum Status { SUCCESS, NOT_FOUND, EXPIRED, REUSE_DETECTED }

    public static Outcome success(User user, String refreshToken) {
      return new Outcome(Status.SUCCESS, user, refreshToken);
    }

    public static Outcome notFound()      { return new Outcome(Status.NOT_FOUND, null, null); }
    public static Outcome expired()       { return new Outcome(Status.EXPIRED, null, null); }
    public static Outcome reuseDetected() { return new Outcome(Status.REUSE_DETECTED, null, null); }
  }

}
