package com.sprintlog.sprintlogboot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprintlog.sprintlogboot.domain.ActivityCategory;
import com.sprintlog.sprintlogboot.domain.LearningActivity;
import com.sprintlog.sprintlogboot.domain.Visibility;
import com.sprintlog.sprintlogboot.dto.request.CreateActivityRequest;
import com.sprintlog.sprintlogboot.dto.request.UpdateActivityRequest;
import com.sprintlog.sprintlogboot.exception.ActivityNotFoundException;
import com.sprintlog.sprintlogboot.repository.ActivityRepository;
import com.sprintlog.sprintlogboot.repository.AuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityService 슬라이스 테스트 (Mockito)")
class ActivityServiceTest {

  // ActivityService의 기능을 테스트 하는 것이기에, 나머지 의존 객체는 가짜로 채워 넣는다.
  @Mock
  ActivityRepository repository;
  @Mock
  AuditLogRepository auditLogRepository;
  @Mock
  AuditService auditService;
  @Mock
  FileStorage fileStorage;

  // 서비스의 create는 timer가 걸려 있음 -> MeterRegistry의 timer는 실제로 동작해야 합니다. (Mock 안됨!)
  // Spy를 걸어서 실제 기능이 동작할 수 있는 객체로 둔다.
  @Spy MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @InjectMocks
  ActivityService service; // 가짜 객체들을 주입받은 Service 객체를 사용한다.

  private LearningActivity sample() {
    return new LearningActivity(ActivityCategory.LECTURE, "옛 제목", 30, Visibility.PRIVATE, "이강사", null, null);
  }

  @Nested
  @DisplayName("조회 get")
  class Get {

    @Test
    @DisplayName("있으면 그 활동을 돌려준다.")
    void 있으면_반환() {
        // given
      LearningActivity activity = sample();
      when(repository.findById(1L)).thenReturn(Optional.of(activity));

      // when // then
      assertThat(service.get(1L)).isSameAs(activity);


    }

    @Test
    @DisplayName("없으면 ActivityNotFoundException 발생")
    void 없으면_예외() {
        // given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // when  // then
      assertThatThrownBy(() -> service.get(999L)).isInstanceOf(ActivityNotFoundException.class);

    }

  }

  @Nested
  @DisplayName("수정(update)")
  class Update {

    @Test
    @DisplayName("제목, 공개여부 바꾸고 저장한다.")
    void 정상_수정() {
      // given
      LearningActivity activity = sample();
//      when(repository.findById(1L)).thenReturn(Optional.of(activity)); // 기본 Mockito 방식
      given(repository.findById(1L)).willReturn(Optional.of(activity)); // BDD 방식의 Mockito 방식 (최근 선호)
      given(repository.save(any(LearningActivity.class))).willReturn(activity);

      // when
      service.update(1L, new UpdateActivityRequest("새 제목", Visibility.PUBLIC));


      // then
      assertThat(activity.getTitle()).isEqualTo("새 제목"); // 상태를 검증
      assertThat(activity.getVisibility()).isEqualTo(Visibility.PUBLIC); // 상태를 검증
      verify(repository).save(activity); // 행위를 검증(서비스가 협력자를 제대로 불렀니?)
    }

    @Test
    @DisplayName("대상이 없다면 예외 - 저장은 일어나지 않는다.")
    void 없으면_변경없음() {
        // given
        given(repository.findById(999L)).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> service.update(999L, new UpdateActivityRequest("x", Visibility.PUBLIC)))
            .isInstanceOf(ActivityNotFoundException.class);
        verify(repository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("삭제 delete")
  class Delete {
    @Test
    @DisplayName("존재하면 그 id로 삭제한다.(existById 확인)")
    void 정상_삭제 () {
        // given
      LearningActivity activity = sample();
      given(repository.findById(1L)).willReturn(Optional.of(activity));

        // when // then
      service.delete(1L);
      verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("없으면 예외 - 삭제는 일어나지 않는다.")
    void 없으면_삭제안함() {
        // given
      given(repository.findById(999L)).willReturn(Optional.empty());

      // when // then
      assertThatThrownBy(() -> service.delete(999L)).isInstanceOf(ActivityNotFoundException.class);
      verify(repository, never()).deleteById(any());
    }
  }

  @Nested
  @DisplayName("저장된 내용 검증 - ArgumentCaptor")
  class SavedContent {
    @Test
    @DisplayName("수정 시 저장되는 엔터티의 제목은 요청대로 바뀌어 있다.")
    void 저장내용_검증() {
      // given
      LearningActivity activity = sample();
      given(repository.findById(1L)).willReturn(Optional.of(activity));
      given(repository.save(any(LearningActivity.class))).willReturn(activity);

      // when
      service.update(1L, new UpdateActivityRequest("바뀐 제목", Visibility.PUBLIC));
      // ArgumentCaptor는 행위를 검증하는 verify를 시행할 때 호출하는 메서드의 매개값을 붙잡아서 활용할 수 있게 해줍니다.
      // update에서는 사실 ArgumentCaptor를 사용할 필요 없습니다. -> 샘플 데이터를 직접 생성해서 전달하기 때문에
      // 똑같은 객체를 수정해서 확인하면 끝입니다.
      // create에서는 비교 대상이 없어 전달된 값을 검증하기 위해서 ArgumentCaptor가 필요합니다.
      ArgumentCaptor<LearningActivity> captor = ArgumentCaptor.forClass(LearningActivity.class);

      // then
      verify(repository).save(captor.capture());
      assertThat(captor.getValue().getTitle()).isEqualTo("바뀐 제목");

        
    }
  }
  
  @Nested
  @DisplayName("생성 create - 내부에서 만든 엔터티를 ArgumentCaptor로 검증")
  class Create {
    @Test
    @DisplayName("요청을 매핑해 저장한다. - 저장하는 엔터티의 필드가 요청과 일치해야한다.")
    void 요청을_매핑해_저장한다() {
        // given
      CreateActivityRequest request = new CreateActivityRequest(
          ActivityCategory.LECTURE, "새 강의", 45, Visibility.PUBLIC,
          null, null, "이강사", null, null
      );

      // willReturn은 리턴할 객체를 직접 명시해줘야합니다.
      // create()를 테스트 할 때는 이 테스트 메서드 안에 리턴해줄 엔터티가 없어요. 그래서 willAnswer 씀.
      // 서비스가 레파지토리의 세이브를 호출하면 전달 받은 그 인자(엔터티)를 그대로 돌려주어라.
      given(repository.save(any(LearningActivity.class))).willAnswer(invocation -> invocation.getArgument(0));

      // when
      service.create(request, null, authentication.getName());
      ArgumentCaptor<LearningActivity> captor = ArgumentCaptor.forClass(LearningActivity.class);
        
    
        // then
      verify(repository).save(captor.capture()); // willAnswer가 돌려준 엔터티를 captor가 붙잡음
      LearningActivity saved = captor.getValue(); // 붙잡은 Entity를 돌려준다.

      // 내가 전달한 DTO의 값으로 Entity가 잘 매핑되었는지를 단언을 통해 검증
      assertThat(saved.getTitle()).isEqualTo("새 강의");
      assertThat(saved.getMinutes()).isEqualTo(45);
      assertThat(saved.getInstructorName()).isEqualTo("이강사");
      assertThat(saved.getCategory()).isEqualTo(ActivityCategory.LECTURE);      
        
    }    
  }
  
  @Nested
  @DisplayName("void 협력자 다루기 - willThrow")
  class VoidCollaborator {
    @Test
    @DisplayName("삭제 협력자가 실패하면 예외가 전파된다.")
    void 삭제_실패_전파() {
        // given
        given(repository.findById(1L)).willReturn(Optional.of(sample()));
        // deleteById를 호출하면서 1L을 주면 예외를 일부러 발생 시키겠다.
        willThrow(new RuntimeException("DB 오류")).given(repository).deleteById(1L);
    
        // when // then
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(RuntimeException.class);

        
    }
  }

  
}