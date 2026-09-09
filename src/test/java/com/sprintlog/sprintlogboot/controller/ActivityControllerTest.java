package com.sprintlog.sprintlogboot.controller;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintlog.sprintlogboot.config.SecurityConfig;
import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.service.ActivityDashboard;
import com.sprintlog.sprintlogboot.service.ActivityService;
import com.sprintlog.sprintlogboot.service.FileService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class) // 우리 security 규칙을 테스트에도 적용해라
// POST/PUT/DELETE 요청은 인증을 요구하게 됐으므로 웹 계층 테스트에 기본 인증 사용자를 부여합니다.
@WithMockUser
@DisplayName("ActivityController 웹 계층 테스트")
class ActivityControllerTest {

  @Autowired
  MockMvc mvc; // 진짜 서버를 띄우지 않고도 HTTP 요청과 응답을 가짜로 시뮬레이션 해 주는 스프링의 도구

  // 의존 관계가 있는 객체들은 모두 가짜로 채우자 - 웹 계층만 집중.
  @MockitoBean
  ActivityService service;
  @MockitoBean
  ActivityDashboard dashboard;
  @MockitoBean
  FileService fileService;

  // SecurityConfig가 Remember-me 용으로 PersistentTokenRepository와 UserDetailsService를 요구하게 됨.
  // 여기는 Controller 슬라이스 테스트이고, @WebMvcTest는 DB를 실제로 사용하지 않기 때문에 빈 등록이 진행되지 않음.
  // 테스트 실패를 방지하기 위해 가짜로 채운다.
  @MockitoBean
  PersistentTokenRepository persistentTokenRepository;
  @MockitoBean
  UserDetailsService userDetailsService;

  private LearningActivity sample;

  @BeforeEach
  void setUp() {
    sample = new LearningActivity(
        ActivityCategory.LECTURE, "스프링 강의", 30, Visibility.PUBLIC, "이강사", null, null);

    // id 는 원래 DB 가 부여하지만, 여기선 서비스가 가짜라 직접 심어 응답 JSON 의 id 를 확인할 수 있게 한다.
    ReflectionTestUtils.setField(sample, "id", 1L);
  }

  @Nested
  @DisplayName("GET /{id}")
  class GetById {

    @Test
    @DisplayName("존재하면 200 + JSON 본문(id, title)")
    void 존재하면_200() throws Exception {
      // given
      given(service.get(1L)).willReturn(sample);

      // when & then
      mvc.perform(get("/api/v1/activities/1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.title").value("스프링 강의"))
          .andExpect(jsonPath("$._links.self").exists()); // HATEOAS 링크는 존재한다.
    }

    @Test
    @DisplayName("없으면 예외 → 404 + ProblemDetail(code A001)")
    void 없으면_404() throws Exception {
      given(service.get(999L)).willThrow(new ActivityNotFoundException(999L));

      mvc.perform(get("/api/v1/activities/999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.code").value("A001"));   // ErrorCode 가 실린다
    }

    @Test
    @DisplayName("GET (목록) - 페이징 파라미터가 전달되면 페이지에 해당하는 목록이 전달된다.")
    void 목록_페이징_조회() throws Exception {
      // given
      Page<LearningActivity> page = new PageImpl<>(List.of(sample), PageRequest.of(0, 20), 1);
      given(service.page(eq("id"), eq(1), eq(5), eq(null))).willReturn(page);

      // when & then
      mvc.perform(get("/api/v1/activities")
              .param("sort", "id")
              .param("page", "1")
              .param("size", "5"))
          .andExpect(status().isOk());
    }


  }

  @Nested
  @DisplayName("POST (생성) - multipart(data + 선택 file)")
  class Create {

    @Test
    @DisplayName("data 만 보내도 201 + Location (file 은 선택)")
    void 정상이면_201() throws Exception {
      given(service.create(any(), any(), any())).willReturn(sample);

      MockMultipartFile data = new MockMultipartFile("data", "data.json",
          MediaType.APPLICATION_JSON_VALUE,
          """
          {"category":"LECTURE","title":"스프링 강의","minutes":30,"visibility":"PUBLIC","instructorName":"이강사"}
          """.getBytes());

      mvc.perform(multipart("/api/v1/activities").file(data))
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", "/api/activities/1"))
          .andExpect(jsonPath("$.title").value("스프링 강의"));

      verify(fileService, never()).saveFile(any());   // 파일이 없으면 저장도 안 함
    }

    @Test
    @DisplayName("data + file 이면 201, 파일은 FileService로 저장된다.")
    void 파일첨부_201() throws Exception {
      given(service.create(any(), any(), any())).willReturn(sample);
      given(fileService.saveFile(any())).willReturn("saved-uuid.png"); // 저장했다 치고 파일명 반환(가짜)

      MockMultipartFile data = new MockMultipartFile("data", "data.json",
          MediaType.APPLICATION_JSON_VALUE,
          """
          {"category":"LECTURE","title":"스프링 강의","minutes":30,"visibility":"PUBLIC","instructorName":"이강사"}
          """.getBytes());

      // 가짜 이미지 데이터 추가 (실제 파일을 첨부할 필요는 전혀 없습니다. 컨트롤러가 그걸 신경쓰지 않고, FileService도 가짜입니다.)
      MockMultipartFile file
          = new MockMultipartFile("file", "proof.png",
          MediaType.IMAGE_PNG_VALUE, "이미지-바이트-데이터".getBytes());

      mvc.perform(multipart("/api/v1/activities").file(data).file(file))
          .andExpect(status().isCreated())
          .andExpect(header().string("Location", "/api/activities/1"))
          .andExpect(jsonPath("$.title").value("스프링 강의"));

      verify(fileService).saveFile(any());   // 파일 파트가 FileService에게 넘어갔냐?
    }

    @Test
    @DisplayName("제목이 비면 검증 실패 -> 400 + ProblemDetail(errors)")
    void 검증실패면_400() throws Exception {
      MockMultipartFile data = new MockMultipartFile("data", "data.json",
          MediaType.APPLICATION_JSON_VALUE,
          """
          {"category":"LECTURE","title":"","minutes":30,"visibility":"PUBLIC","instructorName":"이강사"}
          """.getBytes());

      mvc.perform(multipart("/api/v1/activities").file(data))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("C001"))
          .andExpect(jsonPath("$.errors").exists());

      verify(service, never()).create(any(), any(), any());   // 검증에서 막혀서 서비스까지 못 감.
    }
  }

  @Nested
  @DisplayName("수정(PUT)")
  class Update {

    @Test
    @DisplayName("존재하면 200 + 바뀐 값")
    void 수정_200() throws Exception {
      // given
      ReflectionTestUtils.setField(sample, "title", "새 제목");
      given(service.update(eq(1L), any(UpdateActivityRequest.class))).willReturn(sample);

      // when & then
      mvc.perform(put("/api/v1/activities/1")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"title\":\"새 제목\",\"visibility\":\"PUBLIC\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.title").value("새 제목"));
    }

    @Test
    @DisplayName("없으면 404 + ProblemDetail(A001)")
    void 없으면_404() throws Exception {
      given(service.update(eq(999L), any(UpdateActivityRequest.class)))
          .willThrow(new ActivityNotFoundException(999L));

      // when & then
      mvc.perform(put("/api/v1/activities/999")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"title\":\"x\",\"visibility\":\"PUBLIC\"}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("A001"));

    }


  }

  @Nested
  @DisplayName("DELETE /{id} (삭제)")
  class Delete {

    @Test
    @DisplayName("성공하면 204 (본문 없음)")
    void 삭제_204() throws Exception {
      mvc.perform(delete("/api/v1/activities/1"))
          .andExpect(status().isNoContent());

      verify(service).delete(1L);
    }

    @Test
    @DisplayName("없으면 404 — void 협력자라 willThrow 로 스텁")
    void 없으면_404() throws Exception {
      willThrow(new ActivityNotFoundException(999L)).given(service).delete(999L);

      mvc.perform(delete("/api/v1/activities/999"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("A001"));
    }
  }




}