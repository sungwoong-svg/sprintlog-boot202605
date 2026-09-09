package com.sprintlog.sprintlogboot.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  // 프론트엔드에서 /api/auth/csrf-token을 호출해서 토큰을 달라고 요청해도 spring은 당장 사용할 거 아니면 토큰을 생성하지 않음.
  // 강제로 토큰 생성해서 쿠키에 담으라고 명령을 내림. -> response 헤더에 Set-Cookie라는 이름으로 토큰이 실려 나갑니다.
  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    // 응답에 토큰을 실을 때는 항상 Xor(BREACH 방어).
    this.xor.handle(request, response, csrfToken);
    // deferred 토큰을 '지금' 로드시켜 CookieCsrfTokenRepository 가 쿠키(XSRF-TOKEN)를 세팅하도록 강제한다.
    csrfToken.get();
  }

  // 프론트엔드가 토큰을 보내면 어떻게 해석할지 결정하는 메서드
  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // 요청 헤더에 실제 토큰 값이 들어있는지 확인합니다.
    String headerValue = request.getHeader(csrfToken.getHeaderName());
    
    // 요청에 헤더가 있으면(SPA 가 쿠키의 raw 토큰을 헤더로 보낸 경우) plain 으로 읽고,
    // 그 외(서버 렌더링 폼의 _csrf 파라미터)면 Xor 로 읽는다.
    return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
        .resolveCsrfTokenValue(request, csrfToken);
  }
}