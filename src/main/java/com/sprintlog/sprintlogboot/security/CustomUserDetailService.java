package com.sprintlog.sprintlogboot.security;

import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

  private final UserRepository userRepository;

  // 스프링 시큐리티가 로그인 요청이 들어올 때마다 알아서 이 메서드를 호출해서 검사를 진행합니다.
    /*
    동작 흐름:

    1. 사용자가 로그인 시도 (email, password 입력)
    2. Spring Security가 이 메서드를 호출
    3. 우리는 DB에서 User를 찾아서
    4. CustomUserDetails로 감싸서 반환
    5. Spring Security가 비밀번호를 검증

    사용자를 찾지 못하면 UsernameNotFoundException을 던집니다.
     */

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    return new CustomUserDetails(user);
  }
}
