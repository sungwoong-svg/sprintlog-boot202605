package com.sprintlog.sprintlogboot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/*
OncePerRequestFilter - 한 요청당 한번만 필터가 동작되는 것을 보장합니다.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

  /** MDC 에 넣을 열쇠 이름. 로그 패턴의 %X{traceId} 와 짝이다. */
  public static final String TRACE_ID = "traceId";

  /** 응답(과 다음 시간엔 요청)에 싣는 추적 번호 헤더 이름. 이름을 한 곳에 모아 둔다. */
  public static final String TRACE_HEADER = "X-Trace-Id";


  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    // 이 요청에만 붙는 짧은 번호. 로그 줄마다 따라다니게 해서 '같은 요청의 줄' 을 묶는다.
    String incoming = request.getHeader(TRACE_HEADER);
    String traceId = (incoming != null && !incoming.isBlank())
        ? incoming
        : UUID.randomUUID().toString().substring(0, 8);
    MDC.put(TRACE_ID, traceId);

    // ⭐ 응답에도 실어 보낸다. 사용자가 "오류 화면에 이 번호가 떴어요" 라고 문의하면
    //   우리는 그 번호로 로그를 찾는다. (예전 RequestIdFilter 가 하던 일을 여기로 가져왔다.)
    response.setHeader(TRACE_HEADER, traceId);

    long startedAt = System.currentTimeMillis();
    try {
      // 다음 필터(→ 최종적으로 컨트롤러)로 요청을 넘긴다. 이 호출을 빼먹으면 요청이 여기서 멈춘다.
      filterChain.doFilter(request, response);
    } finally {
      // 요청이 다 처리되고 돌아온 뒤(응답 직전) 로깅한다 — 상태코드·소요시간을 알 수 있는 시점.
      long tookMs = System.currentTimeMillis() - startedAt;
      log.info("[AUDIT] {} {} -> {} ({}ms)",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          tookMs);

      // ⚠ 반드시 지운다. 일꾼(스레드)은 풀에서 재사용되므로, 안 지우면
      //    다음 요청이 남의 번호를 달고 로그를 찍는다.
      MDC.remove(TRACE_ID);
    }
  }
}
