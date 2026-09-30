package com.sprintlog.sprintlogboot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintlog.sprintlogboot.filter.RequestLoggingFilter;
import com.sprintlog.sprintlogboot.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity // 이 클래스가 웹 보안 설정
@EnableMethodSecurity // 이게 있어야 서비스의 @PreAuthorize가 동작합니다.
public class SecurityConfig {

  /*
  SecurityFilterChain - Spring Security의 요청 처리 규칙을 정의하는 빈
  이 빈이 있으면 Spring boot의 기본 자동 설정 대신 '우리 규칙'이 적용된다.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
      // AuthenticationEntryPoint, AccessDeniedHandler는 밑에 빈등록 로직이 작성되어 있으므로,
      // securityFilterChain이 호출될 때 등록된 빈이 전달되도록 세팅
      AuthenticationEntryPoint restAuthenticationEntryPoint,
      AccessDeniedHandler restAccessDeniedHandler,
      JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    http
        // REST API는 브라우저 세션 폼이 아니라 클라이언트가 직접 요청하므로
        // 지금 단계에서는 CSRF 보호를 끈다. (세션 / 폼 기반으로 넘어갈 때 다시 다룬다)
        .csrf(csrf -> csrf.disable())

        // CORS(교차 출처 자원 공유). 다른 출처의 브라우저 요청을 허용한다.
        // Customizer.withDefaults(): 등록된 빈 중 CorsConfigurationSource 타입의 빈이 있다면 기본 적용하겠다.
        .cors(Customizer.withDefaults())

        // XSS 방어를 돕는 보안 응답 헤더 - Content-Security-Policy
        // default-src 'self' = 기본적으로 같은 출처의 리소스만 로드 허용 -> 외부 악성 스크립트 주입을 완화.
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
        )

        // 서버로 들어오는 요청 중 어떤 요청을 허용할 것인가에 대한 설정
        // 이 안에서 경로별 인증 및 권한 체크 진행이 가능가
        .authorizeHttpRequests(auth -> auth
            // ── 공개(permitAll) — 로그인 전에도 되어야 하는 것들 ──
            .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()           // 회원가입
            .requestMatchers("/api/v1/auth/login").permitAll()                        // 로그인 처리
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()    // Access Token 재발급 요청
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/whoami").permitAll()       // 익명 확인용 데모
            .requestMatchers(HttpMethod.GET, "/api/v1/activities/**", "/api/activities/**").permitAll() // 활동 조회는 공개(SprintLog 도메인)
            .requestMatchers("/", "/login.html", "/index.html", "/favicon.svg", "/assets/**").permitAll() // 정적 리소스
            .requestMatchers("/h2-console/**", "/my-api").permitAll()
            // ── 역할 기반 ──
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/me/**").hasRole("USER")
            // 권한 변경 API 는 관리자만(메서드 레벨 @PreAuthorize 와 두 겹).
            .requestMatchers(HttpMethod.PUT, "/api/v1/auth/role").hasRole("ADMIN")
            // ── 그 외 전부 로그인 필요(기본 잠금) ──
            //   활동 쓰기(POST/PUT/DELETE)·현재 사용자(/me) 등은 자동으로 여기에 걸린다.
            //   소유권 등 세밀한 검사는 서비스의 @PreAuthorize 가 이어서 한다(두 겹 방어).
            .anyRequest().authenticated()
        )
        .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // -> JWT 세션 안씁니다.
        )
        // 필터단에서 발생한 커스텀 예외 처리 등록 로직
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(restAuthenticationEntryPoint)
            .accessDeniedHandler(restAccessDeniedHandler)
        )
        // HTTP Basic 인증을 켠다. Authorization 헤더에 Basic <email:password(Base64)> 형식으로 전달되면
        // DaoAuthenticationProvider를 통해 로그인 검증을 수행하고 SecurityContext에 인증을 채운다.
        // 매 요청마다 자격증명을 실어 보내는 무상태(stateless) 방식
        .httpBasic(Customizer.withDefaults())

        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new RequestLoggingFilter(), JwtAuthenticationFilter.class);
    return http.build();
  }

  /*
  BCryptPasswordEncoder - 비밀번호를 단방향 해시로 인코딩 / 검증하는 빈
  평문 저장 금지 원칙을 코드로 실현할 준비물 입니다.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  /*
  static인 이유: 이 계층 빈은 보안 인프라가 초기화되는 설정 단계에 확실하게 잡혀야 한다.
  static으로 선언하면 다른 빈의 조기 초기화 부작용 없이 이를 보장받을 수 있다.
  나중에 컨트롤러에 @PreAuthorize(메서드 보안) 도입 시 static을 붙이지 않으면 동작하지 않는 사례가 있다.
   */
  @Bean
  static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("ADMIN").implies("USER")
        .build();
  }

  // 미인증(401) 응답을 ProblemDetail JSON으로 커스텀할 수 있는 객체.
  @Bean
  AuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
    return (request, response, authException) -> {
      Object jwtError = request.getAttribute(JwtAuthenticationFilter.ATTR_JWT_ERROR);
      if (JwtAuthenticationFilter.ERROR_EXPIRED.equals(jwtError)) {
        writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
            "AUTH_401_EXPIRED", "토큰이 만료되었습니다. 다시 로그인해 주세요.");
      } else if (JwtAuthenticationFilter.ERROR_INVALID.equals(jwtError)) {
        writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
            "AUTH_401_INVALID", "유효하지 않은 토큰입니다.");
      } else {
        writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
            "AUTH_401", "인증이 필요합니다. 로그인 후 다시 시도하세요.");
      }
    };
  }

  // 권한 부족(403) 응답을 ProblemDetail JSON으로 커스텀할 수 있는 객체.
  @Bean
  AccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, deniedException) ->
        writeProblem(objectMapper, response, HttpStatus.FORBIDDEN, "AUTH_403", "이 작업을 수행할 권한이 없습니다.");

  }

  /** 401/403 공통 — ProblemDetail 을 JSON 으로 직접 응답 본문에 쓴다. */
  private static void writeProblem(ObjectMapper objectMapper, HttpServletResponse response,
      HttpStatus status, String code, String detail) throws IOException {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setProperty("code", code);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), pd);
  }


  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    // 허용할 출처 (운영에서는 실제 프론트 도메인 주소가 들어갑니다.)
    config.setAllowedOrigins(List.of("http://localhost:63342", "http://localhost:3000"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true); // 자격 증명 (쿠키, 인증 헤더) 허용

    // 위에서 만든 규칙을 모든 URL 경로에 적용하겠다.
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
  }


}