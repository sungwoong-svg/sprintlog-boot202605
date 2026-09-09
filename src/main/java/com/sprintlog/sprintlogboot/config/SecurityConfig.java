package com.sprintlog.sprintlogboot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprintlog.sprintlogboot.filter.RequestIdFilter;
import com.sprintlog.sprintlogboot.filter.RequestLoggingFilter;
import com.sprintlog.sprintlogboot.security.LoginFailureHandler;
import com.sprintlog.sprintlogboot.security.LoginSuccessHandler;
import com.sprintlog.sprintlogboot.security.SpaCsrfTokenRequestHandler;
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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;
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
      PersistentTokenRepository persistentTokenRepository,
      UserDetailsService userDetailsService,
      AuthenticationSuccessHandler loginSuccessHandler,
      AuthenticationFailureHandler loginFailureHandler,
      SessionRegistry sessionRegistry) throws Exception {
    http
        // REST API는 브라우저 세션 폼이 아니라 클라이언트가 직접 요청하므로
        // 지금 단계에서는 CSRF 보호를 끈다. (세션 / 폼 기반으로 넘어갈 때 다시 다룬다)
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // csrf 쿠키는 JS가 읽어서 헤더에 실어야 되기 때문에 httpOnly를 false로
            .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
        )

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
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf-token").permitAll()  // CSRF 토큰 발급
            .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()           // 회원가입
            .requestMatchers("/login", "/logout").permitAll()                        // 로그인·로그아웃 처리
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/whoami").permitAll()       // 익명 확인용 데모
            .requestMatchers(HttpMethod.GET, "/api/v1/activities/**", "/api/activities/**").permitAll() // 활동 조회는 공개(SprintLog 도메인)
            .requestMatchers("/", "/login.html", "/index.html", "/favicon.svg", "/assets/**").permitAll() // 정적 리소스
            .requestMatchers("/h2-console/**").permitAll()
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
//                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) -> JWT는 세션 안씁니다.
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .invalidSessionUrl("/login.html?expired")
                // 세션 ID만 변경하고 세션 객체는 그대로 유지
                .sessionFixation(fixation -> fixation.changeSessionId())


                // 동시성 관련 설정은 이 블록 안에서 작성한다.
                .sessionConcurrency(concurrency -> concurrency
                    .maximumSessions(1) // 한 사용자 당 최대 세션 수
                    .maxSessionsPreventsLogin(false) // false: 새 로그인 시 이전 세션 만료, true: 이미 로그인 되어 있다면 새 로그인 차단.
                    .expiredUrl("/login.html?expired")
                    .sessionRegistry(sessionRegistry)
                )
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

        // 폼 로그인 (세션 기반)을 켠다.
        // 한 번 로그인하면 서버가 세션을 만들고 JSESSIONID 쿠키를 발급한다.
        // 이후 요청은 그 쿠키만으로 인증 유지된다 - 상태 유지(stateful) 방식
        .formLogin(form -> form
            .loginProcessingUrl("/login") // 폼이 POST 처리되는 URL(Spring이 가로챔)
            .successHandler(loginSuccessHandler)
            .failureHandler(loginFailureHandler)
            .permitAll() // 로그인 요청은 누구나 접근 가능
        )

        .rememberMe(remember -> remember
            .key("sprintlog-rememberme-secret-key") // 토큰 서명에 사용하는 비밀 키.
            .rememberMeParameter("remember-me") // 로그인 폼의 checkbox name과 똑같이 일치
            .tokenValiditySeconds(60 * 60 * 24 * 14) // 14일
            .tokenRepository(persistentTokenRepository) // 영구 토큰을 저장/조회할 곳
            .userDetailsService(userDetailsService) // 쿠키가 유효할 때 email로 사용자를 다시 로드하는 다리 역할 객체
        )

        .logout(logout -> logout
            .logoutUrl("/logout")   // POST /logout 으로 로그아웃
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)) // 204
            .invalidateHttpSession(true)    // 세션 무효화(기본값이지만 명시)
            .deleteCookies("JSESSIONID", "remember-me") // 세션 쿠키 삭제, 자동 로그인 쿠키도 삭제
        )

        .addFilterBefore(new RequestIdFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(new RequestLoggingFilter(), RequestIdFilter.class);
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
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  @Bean
  AuthenticationSuccessHandler loginSuccessHandler(ObjectMapper objectMapper) {
    return new LoginSuccessHandler(objectMapper);
  }

  @Bean
  AuthenticationFailureHandler loginFailureHandler(ObjectMapper objectMapper) {
    return new LoginFailureHandler(objectMapper);
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
    return (request, response, authException) ->
        writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED, "AUTH_401", "인증이 필요합니다. 로그인 후 다시 시도하세요.");
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

  // 서블릿 컨테이너의 세션 생성/소멸을 Spring 이벤트로 발행한다.
  // 동시 세션 제어에서도 세션 개수를 추적하려면 이 publisher가 필요하다.
  @Bean
  HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
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