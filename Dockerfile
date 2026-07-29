
# --- 1.  build 스테이지 ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Docker는 빌드할 때마다 레이어 캐시를 만듭니다. 이전 빌드와 파일 내용이 바뀌지 않았다면 이전에 저장한 레이어 캐시를 그대로 재사용합니다.
# gradle 관련 설정이나 build.gradle에 있는 의존성 라이브러리 목록은 잘 바뀌지 않는 영역입니다.
# 밑에 있는 폴더와 파일을 미리 카피를 해놓고, gradlew에게 의존성 라이브러리를 미리 다운로드 해서 레이어 캐시를 저장해라
# --no-daemon: Docker 이미지를 빌드하는 컨테이너는 일회성이므로 메모리 누수 위험이 있는 gradle daemon 프로세스를 켜지 않도록 설정.
COPY gradlew setting.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# 소스코드 복사 후 실행 가능한 jar 빌드
# 개발 과정에서 테스트 이미지 빌드에서 제외
COPY src ./src
RUN ./gradlew clean build -x test

# --- 2.  run 스테이지 ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 네트워크 요청 도구인 curl 설치(리눅스의 postman), 불필요한 패키지 설치를 막고 패키지 목록 임시 파일들을 전부다 삭제해서 이미지 용량을 줄이자.
# curl 설치 이유: 헬스체크용
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# build 스테이지에서 만든 jar를 카피해서 app.jar 이름으로 붙여넣기
COPY --from=build /workspace/build/libs/sprintlog-boot-0.0.1-SNAPSHOT.jar app.jar

# 타임존 설정
ENV TZ=Asia/Seoul
RUN apk add --no-cache curl tzdata

# 운영 프로파일로 실행하기 위해 환경변수 값을 prod로 전달 (야믈에 dev 써있어도 이거 쓰면 최우선됨)
ENV SPRING_PROFILES_ACTIVE=prod

# sprintlog app: 8080, actuator: 9090
# EXPOSE는 문서용 키워드. 실제로 노출시키려면 컨테이너 생성 시 -p를 사용해서 노출시켜야 한다. (안써도 그만)
EXPOSE 8080 9090

# Actuator 헬스체크(별도 포트 9090, base-path /management). 부팅 시간 고려해 start-period 여유.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:9090/management/health || exit 1

# CMD, RUN은 기본 실행 명령을 의미, 컨테이너 실행 시에 다른 명령어가 주어지면 그 명령어로 대체될 수 있음.
# ENTRYPOINT는 반드시 실행되어야 할 명령어를 의미. 다른 명령어로 대체되지 않음.
# 스프링 부트는 무조건 -jar 옵션으로 실행되어야 하기 때문에 강조의 의미로 ENTRYPOINT 선언.
ENTRYPOINT ["java", "-jar", "app.jar"]