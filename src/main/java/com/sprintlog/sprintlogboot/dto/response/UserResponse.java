package com.sprintlog.sprintlogboot.dto.response;

import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;

/**
 * 사용자 응답 DTO.
 * 절대 password 를 담지 않는다 — 해시라도 응답 본문에 노출하지 않는 것이 원칙.
 */
public record UserResponse(
    Long id,
    String email,
    String nickname,
    Role role
) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
  }
}
