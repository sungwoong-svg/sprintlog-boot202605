package com.sprintlog.sprintlogboot.security;


import com.sprintlog.sprintlogboot.domain.PersistentLogin;
import com.sprintlog.sprintlogboot.repository.PersistentLoginRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
PersistentTokenRepository를 JPA로 직접 구현하는 클래스
Spring의 내장 JdbcTokenRepositoryImpl 대신 우리가 4개 메서드를 직접 구현해서
토큰이 어떻게 저장, 갱신, 조회, 삭제되는지 전 과정을 JPA로 일관되게 처리한다.

@Component로 빈 등록하면 SecurityConfig의 rememberMe().tokenRepository(...)가 이 구현체를 주입받아 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaPersistentTokenRepository implements PersistentTokenRepository {

  private final PersistentLoginRepository repository;

  // 로그인(로그인 유지 체크) 성공 시 1회 호출 - Spring이 준 토큰을 새 행으로 DB에 저장
  @Override
  @Transactional
  public void createNewToken(PersistentRememberMeToken token) {
    PersistentLogin entity = new PersistentLogin(
        token.getSeries(),
        token.getUsername(),
        token.getTokenValue(),
        toLocalDateTime(token.getDate())
    );
    repository.save(entity);
  }

  // 자동 로그인 성공마다 호출 - Spring이 준 새 토큰으로 회전(series는 그대로)
  @Override
  @Transactional
  public void updateToken(String series, String tokenValue, Date lastUsed) {
    repository.findBySeries(series).ifPresentOrElse(
        entity -> entity.update(tokenValue, toLocalDateTime(lastUsed)),
        () -> {
          log.warn("[REMEMBER-ME] 갱신 대상 없음 - series={}", series);
          // 부가 로직 추가로 더 작성 가능 (알림 전송, 관리자가 수동 삭제, 강제 로그아웃 등등...)
        }
    );
  }

  // 자동 로그인 시 호출 - 쿠키의 series로 저장된 토큰을 찾아서 Spring 규격 객체로 돌려준다. (없으면 null)
  @Override
  @Transactional(readOnly = true)
  public PersistentRememberMeToken getTokenForSeries(String seriesId) {
    return repository.findBySeries(seriesId)
        .map(e -> new PersistentRememberMeToken(
            e.getUsername(), e.getSeries(), e.getToken(), toDate(e.getLastUsed())
        ))
        .orElse(null);
  }

  // 로그아웃 등에서 그 사용자의 모든 토큰 삭제
  @Override
  @Transactional
  public void removeUserTokens(String username) {
    long count = repository.countByUsername(username);
    repository.deleteByUsername(username);
    log.info("[REMEMBER-ME] 토큰 삭제 - user={}, 삭제 수={}", username, count);
  }

  private static LocalDateTime toLocalDateTime(Date date) {
    return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  private static Date toDate(LocalDateTime dateTime) {
    return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
  }


}