// SprintLog 로그인 페이지 스크립트 (SPA/CSRF 방식).
// [CP122→CP127c] CSP(default-src 'self')가 인라인 <script> 를 막으므로 같은 출처 외부 파일로 분리한다.
(() => {
  // 실패(?error)/로그아웃(?logout)/세션만료(?expired) 안내
  const params = new URLSearchParams(location.search);
  const msg = document.getElementById('msg');
  if (params.has('error'))   { msg.textContent = '이메일 또는 비밀번호가 올바르지 않습니다.'; msg.className = 'msg error'; }
  if (params.has('logout'))  { msg.textContent = '로그아웃되었습니다.'; msg.className = 'msg info'; }
  if (params.has('expired')) { msg.textContent = '세션이 만료되었습니다. 다시 로그인해 주세요.'; msg.className = 'msg error'; }

  // 쿠키에서 XSRF-TOKEN(raw) 읽기 — CookieCsrfTokenRepository.withHttpOnlyFalse() 라 JS 가 읽을 수 있다.
  function xsrfToken() {
    const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : '';
  }

  // 폼 제출을 JS 가 가로챈다 → ① CSRF 토큰 발급받아 쿠키 확보 → ② 그 값을 X-XSRF-TOKEN 헤더로 실어 로그인
  document.getElementById('loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    await fetch('/api/v1/auth/csrf-token', { credentials: 'same-origin' });     // ① XSRF-TOKEN 쿠키 세팅
    const res = await fetch('/login', {                                          // ② 로그인
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': xsrfToken()                                              // ★ 쿠키의 raw 토큰을 헤더로
      },
      body: new URLSearchParams(new FormData(e.target))                          // username·password·remember-me
    });
    if (res.ok) {
      location.href = '/api/v1/auth/whoami';   // 200(성공) → 로그인 상태 확인 페이지로
    } else {
      msg.textContent = '이메일 또는 비밀번호가 올바르지 않습니다.'; msg.className = 'msg error';  // 401(실패)
    }
  });
})();
