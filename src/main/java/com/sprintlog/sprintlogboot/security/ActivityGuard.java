package com.sprintlog.sprintlogboot.security;

import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 소유권 기반 인가 판단을 담당하는 빈이다.
@Component("activityGuard")
@RequiredArgsConstructor
public class ActivityGuard {

  private final ActivityRepository repository;

  @Transactional(readOnly = true)
  public boolean isOwner(Long activityId, Long userId) {
    if (userId == null) {
      return false;   // uid 클레임이 없는 구 토큰 — 소유권을 인정하지 않는다(안전한 기본값)
    }
    return repository.findById(activityId)
        .map(activity -> activity.getOwner() != null
            && userId.equals(activity.getOwner().getId()))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isOwner(Long activityId, String email) {
    return repository.findById(activityId)
        .map(a -> a.getOwner() != null && email.equals(a.getOwner().getEmail()))
        .orElse(false);
  }

}
