package com.sprintlog.sprintlogboot.controller;

import com.sprintlog.sprintlogboot.dto.response.AuditLogResponse;
import com.sprintlog.sprintlogboot.service.AuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

  private final AuditService auditService;

  /**
   * 활동 변경 감사 로그 조회 — 최근 것부터.
   *
   * ⚠ 이 메서드의 권한 판정은 DB 를 보지 않는다. 토큰의 role 클레임에서 온 권한으로 결정된다.
   *   "이 사람이 관리자인가" 는 토큰이 답할 수 있는 질문이기 때문이다.
   *   (반면 "이 활동이 이 사람 것인가" 는 토큰이 답할 수 없다 — ActivityGuard 가 DB 로 확인한다.)
   */
  @GetMapping("/audit-logs")
  @PreAuthorize("hasRole('ADMIN')")
  public List<AuditLogResponse> auditLogs() {
    return auditService.findAllRecentFirst();
  }

}
