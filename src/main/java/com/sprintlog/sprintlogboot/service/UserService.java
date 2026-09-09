package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.Role;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.dto.request.SignUpRequest;
import com.sprintlog.sprintlogboot.dto.response.UserResponse;
import com.sprintlog.sprintlogboot.exception.BusinessException;
import com.sprintlog.sprintlogboot.exception.ErrorCode;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import com.sprintlog.sprintlogboot.security.CustomUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;

  private final SessionRegistry sessionRegistry;

  @Transactional
  public UserResponse register(SignUpRequest request) {
    // 1. 이메일 중복 확인 (409 status)
    if (userRepository.existsByEmail(request.email())) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXIST);
    }

    // 2. 비밀번호는 반드시 암호화해서 저장 (평문 저장 금지)
    String hashedPassword = passwordEncoder.encode(request.password());
//    passwordEncoder.matches("원문 비밀번호", "암호화된 비밀번호 ") -> equals 비교 x, matches로 패턴 일치 여부 비교

    // 3. 저장
    User user = new User(request.nickname(), request.email(), hashedPassword);
    User saved = userRepository.save(user);

    log.info("[USER] 회원 가입 완료 - id={}, email={}", saved.getId(), saved.getEmail());

    return UserResponse.from(user);

  }

  @Transactional
  public UserResponse changeRole(String email, Role newRole) {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    user.changeRole(newRole);

    expireSessionOf(email);

    User saved = userRepository.save(user);
    return UserResponse.from(saved);
  }

  private void expireSessionOf(String email) {
    for (Object principal : sessionRegistry.getAllPrincipals()) {
      if (principal instanceof CustomUserDetails details
      && details.getUsername().equals(email)) {
        List<SessionInformation> sessions = sessionRegistry.getAllSessions(principal, false);
        sessions.forEach(SessionInformation::expireNow);         

      }
    }
  }

}
