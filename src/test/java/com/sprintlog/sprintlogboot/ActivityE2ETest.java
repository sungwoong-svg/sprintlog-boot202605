package com.sprintlog.sprintlogboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.sprintlog.sprintlogboot.domain.User;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import com.sprintlog.sprintlogboot.repository.UserRepository;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

// 가짜 흉내가 아니라 진짜 서버를 충돌 없는 랜덤 포트 번호로 띄워서 진짜 HTTP 통신 테스트를 진행하겠다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("활동 E2E 통합 테스트 (@SpringBootTest + TestRestTemplate)")
public class ActivityE2ETest {

  @LocalServerPort int port; // 서버가 실제로 배정받은 포트 번호를 이 필드에 주입.
  @Autowired
  TestRestTemplate rest; // 진짜 HTTP 요청을 보내는 클라이언트를 주입 받습니다.

  @Autowired
  ActivityRepository activityRepository;
  @Autowired
  AuditLogRepository auditLogRepository;
  @Autowired
  UserRepository userRepository;
  @Autowired
  PasswordEncoder passwordEncoder;

  private String base; // 공통 기본 url을 담아놓을 용도

  // build 폴더 아래에 폴더를 세팅하면 gitignore 대상이고, gradle clean 할 때 알아서 지워진다.
  static final Path uploadDir = Paths.get("./build/test-uploads");


  @BeforeEach
  void clean() {
    auditLogRepository.deleteAll();
    activityRepository.deleteAll();
    userRepository.deleteAll(); // 샘플 데이터를 비워서 시작 상태를 일정하게

    userRepository.save(new User("김춘식", "choon@naver.com", passwordEncoder.encode("password123")));

    base = "http://localhost:" + port + "/api/v1/activities";

    // 매 테스트마다 업로드 폴더를 비운다.
    File[] files = uploadDir.toFile().listFiles();
    if (files != null) {
      for (File file : files) {
        file.delete();
      }
    }
  }

  // 1x1 투명 PNG (진짜 이미지라, 나중에 이미지 검증이 붙어도 통과한다)
  private static final String PNG_1X1 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

  private Resource fileResource(String filename, byte[] bytes) {
    return new ByteArrayResource(bytes) {
      @Override
      public String getFilename() {
        return filename;   // 이게 없으면 파일 파트로 인식되지 않는다
      }
    };
  }

  private Resource png(String filename) {
    return fileResource(filename, Base64.getDecoder().decode(PNG_1X1));
  }

  // 업로드 폴더에 실제로 남아 있는 파일명 목록
  private List<String> uploadedFileNames() {
    File[] files = uploadDir.toFile().listFiles();
    return files == null ? List.of() : Arrays.stream(files).map(File::getName).toList();
  }

  //생성 요청을 멀티 파트로 보내주는 헬퍼 메서드입니다. JSON 문자 열을 넘기면 알아서 멀티파트로 포장해줍니다.
  private ResponseEntity<String> multipartCreate(String dataJson) {
    HttpHeaders dataHeaders = new HttpHeaders();
    dataHeaders.setContentType(MediaType.APPLICATION_JSON); // 헤더 정보를 세팅 (요청 컨텐트 타입이 JSON이다.)
    HttpEntity<String> dataPart = new HttpEntity<>(dataJson, dataHeaders); // 헤더와 바디를 하나의 객체로 감싼다.

    // 일반 Map은 하나의 키에 하나의 값만 넣을 수 있습니다.
    // MultiValueMap은 하나의 키에 여러개의 값을 리스트로 매핑해 줌. Map<K, List<V>>
    // TestRestTemplate이 multipart/form-data 본문을 생성할 때 MultiValueMap을 받도록 설계되어 있습니다.
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("data", dataPart);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA); // 전체 요청은 multipart/form-data 요청이다.

    // TestRestTemplate에게 POST 요청을 보내라고 명령합니다.
    // postForEntity(요청 보낼 url, 헤더와 바디 정보를 담은 HttpEntity, 응답 본문을 어떤 타입으로 받을지)
    return rest.withBasicAuth("choon@naver.com", "password123")
        .postForEntity(base, new HttpEntity<>(parts, headers), String.class);
  }

  // data와 file까지 받아서 멀티파트 포장
  private ResponseEntity<String> multipartCreate(String dataJson, Resource file, MediaType fileType) {
    HttpHeaders dataHeaders = new HttpHeaders();
    dataHeaders.setContentType(MediaType.APPLICATION_JSON);

    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("data", new HttpEntity<>(dataJson, dataHeaders));

    if (file != null) {
      HttpHeaders fileHeaders = new HttpHeaders();
      fileHeaders.setContentType(fileType);
      parts.add("file", new HttpEntity<>(file, fileHeaders));
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA); // 전체 요청은 multipart/form-data 요청이다.

    return rest.withBasicAuth("choon@naver.com", "password123")
        .postForEntity(base, new HttpEntity<>(parts, headers), String.class);
  }

  // 수정(PUT)은 JSON 본문(@RequestBody) 이라 그대로 보낸다.
  private HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  
  
  @Test
  @DisplayName("생성 -> 조회 왕복 - POST로 만든 활동을 그 Location으로 다시 GET하면 같은 활동이 온다.")
  void 생성하고_다시_조회() {
    // (1) 진짜 HTTP POST -> 컨트롤러 -> 서비스 -> 진짜 DB에 저장
    ResponseEntity<String> created = multipartCreate("""
                {"category":"LECTURE","title":"E2E 강의","minutes":45,
                 "visibility":"PUBLIC","instructorName":"이강사","studiedOn":"2026-01-01"}
                """);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    URI location = created.getHeaders().getLocation();
    assertThat(location).isNotNull(); // Location 헤더가 있을 것이다.

    // (2) 그 주소로 다시 GET -> 방금 저장한 게 진짜 DB에 나올 것이다.
    ResponseEntity<String> fetched = rest.getForEntity(
        "http://localhost:" + port + location.getPath(), String.class
    );
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((String) JsonPath.read(fetched.getBody(), "$.title")).isEqualTo("E2E 강의");
  }

  @Test
  @DisplayName("파일 첨부 생성 - 201, 진짜 바이트가 UUID 이름으로 저장된다.")
  void 파일과_함께_생성() throws IOException {
    // (1) 진짜 HTTP POST -> 컨트롤러 -> 서비스 -> 진짜 DB에 저장
    byte[] sent = Base64.getDecoder().decode(PNG_1X1);

    ResponseEntity<String> created = multipartCreate("""
                {"category":"LECTURE","title":"E2E 강의","minutes":45,
                 "visibility":"PUBLIC","instructorName":"이강사","studiedOn":"2026-01-01"}
                """, png("proof.png"), MediaType.IMAGE_PNG);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    URI location = created.getHeaders().getLocation();
    assertThat(location).isNotNull(); // Location 헤더가 있을 것이다.

    // 디스크를 직접 확인해서 첨부파일이 저장되었는지 확인한다.
    List<String> saved = uploadedFileNames();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0)).matches("[0-9a-f]{32}\\.png"); // 정규 표현식으로 UUID 파일명이 맞는지를 확인

    // 업로드 된 파일을 byte[]로 읽어와서 원본과 손상이 전혀 없는지를 확인
    assertThat(Files.readAllBytes(uploadDir.resolve(saved.get(0)))).isEqualTo(sent);
  }

  @Test
  @DisplayName("검증 실패 - 빈 제목이면 진짜 HTTP로도 400 + ProblemDetail(code C001)")
  void 검증실패면_400() {
    // when
    ResponseEntity<String> res = multipartCreate("""
                {"category":"LECTURE","title":"","minutes":45,
                 "visibility":"PUBLIC","instructorName":"이강사","studiedOn":"2026-01-01"}
                """);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat((String) JsonPath.read(res.getBody(), "$.code")).isEqualTo("C001");
  }

  @Test
  @DisplayName("없는 자원 - 존재하지 않는 id 조회는 404 + ProblemDetail(code A001)")
  void 없으면_404() {
    // when
    ResponseEntity<String> res = rest.getForEntity(base + "/999", String.class);

    // then
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat((String) JsonPath.read(res.getBody(), "$.code")).isEqualTo("A001");
  }

  // 전체 생명주기 시나리오 — E2E 의 진짜 값어치. 한 자원이 생성→수정→삭제 를 거치는 사용자 흐름 을
  // 진짜 HTTP 로 통과시키며, 각 단계의 결과가 *진짜 DB 에 이어져* 반영되는지 본다. (조각 테스트로는 못 보는 것)
  @Test
  @DisplayName("생명주기 — 생성→수정→조회→삭제→다시 조회하면 404")
  void 생성_수정_삭제_생명주기() {
    // 1) 생성(POST, 멀티파트) → 201, id 확보
    ResponseEntity<String> created = multipartCreate("""
                {"category":"LECTURE","title":"처음 제목","minutes":30,"visibility":"PUBLIC","instructorName":"이강사"}
                """);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long id = ((Number) JsonPath.read(created.getBody(), "$.id")).longValue();
    String one = base + "/" + id;

    // 2) 수정(PUT, JSON) → 200. postForEntity 처럼 지름길이 없어 exchange(HttpMethod.PUT, ...) 로 보낸다.
    ResponseEntity<String> updated = rest.withBasicAuth("choon@naver.com", "password123")
        .exchange(one, HttpMethod.PUT,
        json("{\"title\":\"바뀐 제목\",\"visibility\":\"PRIVATE\"}"), String.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((String) JsonPath.read(updated.getBody(), "$.title")).isEqualTo("바뀐 제목");

    // 3) 조회(GET) → 수정이 진짜 DB 에 반영됐는지 확인
    assertThat((String) JsonPath.read(rest.getForEntity(one, String.class).getBody(), "$.title"))
        .isEqualTo("바뀐 제목");

    // 4) 삭제(DELETE) → 204(본문 없음)
    ResponseEntity<Void> deleted = rest.withBasicAuth("choon@naver.com", "password123")
        .exchange(one, HttpMethod.DELETE, null, Void.class);
    assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 5) 다시 조회 → 이제 없다(404). 삭제까지 전 계층으로 이어져 반영됨.
    assertThat(rest.getForEntity(one, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
