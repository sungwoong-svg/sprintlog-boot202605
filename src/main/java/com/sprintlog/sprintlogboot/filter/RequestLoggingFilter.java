package com.sprintlog.sprintlogboot.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/*
OncePerRequestFilter - 한 요청당 한번만 필터가 동작되는 것을 보장합니다.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, // 요청 관련 정보를 담은 객체
                                  HttpServletResponse response, // 응답 관련 정볼르 담은 객체
                                  FilterChain filterChain) // 필터 통과 여부를 결정할 객체
      throws ServletException, IOException {

    long startedAt = System.currentTimeMillis();
    try {
      // 다음 필터로 요청, 응답 객체를 전달
      // diFilter을 호출하지 않으면 다음 필터로 요청, 응답 객체가 넘어가지 않습니다(요청이 멈춤)
      filterChain.doFilter(request, response);
    } finally {
        long tookMs = System.currentTimeMillis() - startedAt;
        log.info("[AUDIT] {} {} -> {} ({}ms)",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            tookMs);
    }

  }
}
