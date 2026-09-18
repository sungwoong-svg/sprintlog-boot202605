package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.ActivityAuditLog;
import com.sprintlog.sprintlogboot.dto.response.AuditLogResponse;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 같은 클래스 안에서 메서드를 호출하면 Spring의 트랜잭션 프록시를 거치지 않아서 트랜잭션이 무시됩니다.
// 그래서 독립된 트랜잭션을 보여드리기 위해 서로 다른 빈으로 분리해서 프록시를 거치게 해야한다.
@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  // 시도 이력을 '독립된 트랜잭션'으로 남긴다.
  // '활동 등록' 로직이 별도의 트랜잭션을 가지고 있어도 이 메서드는 자기만의 트랜잭션을 새로 열 것이다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logAttempt(String action, String detail) {
    auditLogRepository.save(new ActivityAuditLog(action, detail));
  }

  @Transactional(readOnly = true)
  public List<AuditLogResponse> findAllRecentFirst() {
    return auditLogRepository.findAllByOrderByIdDesc().stream()
        .map(AuditLogResponse::from)
        .toList();
  }
}
