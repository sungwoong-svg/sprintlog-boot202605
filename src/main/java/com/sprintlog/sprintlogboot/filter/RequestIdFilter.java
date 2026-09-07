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

@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String MDC_REQUEST_ID_KEY = "requestId";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    // 1. Request ID 생성 또는 추출
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isEmpty()) {
      requestId = UUID.randomUUID().toString();
    }

    // 2. MDC에 저장 (로그에서 사용)
    MDC.put(MDC_REQUEST_ID_KEY, requestId);

    // 3. Response 헤더에 추가
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      log.info("Request ID: {} - Started", requestId);
      filterChain.doFilter(request, response);
    } finally {
      log.info("Request ID: {} - Completed", requestId);
      // 4. MDC 정리 (메모리 누수 방지)
      MDC.clear();
    }
  }

}
