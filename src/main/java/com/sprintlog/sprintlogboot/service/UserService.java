package com.sprintlog.sprintlogboot.service;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.dto.request.SignUpRequest;
import com.sprintlog.sprintlogboot.dto.response.UserResponse;
import com.sprintlog.sprintlogboot.exception.BusinessException;
import com.sprintlog.sprintlogboot.exception.ErrorCode;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;


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

}
