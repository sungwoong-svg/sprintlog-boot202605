package com.sprintlog.sprintlogboot.repository;

import com.sprintlog.sprintlogboot.domain.ActivityAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<ActivityAuditLog, Long> {

  // 최근 이력부터 (id 내림차순) 변경내역 조회
  List<ActivityAuditLog> findAllByOrderByIdDesc();

}
