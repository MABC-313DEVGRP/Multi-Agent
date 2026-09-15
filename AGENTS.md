# AGENTS.md — MultiAgent (A-RMS) 개발 에이전트 규약

## 1. 프로젝트 개요 (실제 코드 기준)

- Spring Boot 3.5.6 + WebFlux(Reactive), Actuator, springdoc(Swagger UI / v3 API docs)
- Spring AI 1.0.0-M8 + `spring-ai-starter-model-openai` (Upstage Solar Pro 4)
  - base-url: `https://api.upstage.ai`, model: `solar-pro4`
  - API 키는 `UPSTAGE_API_KEY` 환경변수(application.yml에 `${UPSTAGE_API_KEY}`로 참조)
- frontend: 바닐라 JS (`index.html`, `multiagent.js` 681줄, `multiagent.css`)
  - SSE로 `/multiagent/stream` 소비, 중단은 `/multiagent/stop-stream`, 상태 확인은 `/multiagent/validate`
- 현재 구현된 에이전트는 PM 에이전트(`req-to-task` 스킬) 하나
- Orchestrator: `MultiAgentServiceImpl`(`UserQueryService<MultiAgentDTO>` 구현)이 진입점
  - 현재는 PM 하나로만 위임(라우팅 분기는 없음)
- 엔드포인트(실제):
  - POST `/multiagent/stream` (text/event-stream)
  - POST `/multiagent/generate`
  - GET `/multiagent/stop-stream?sessionId=`
  - POST `/multiagent/validate` (AI 호출 없음, 상태 확인용)
  - GET `/swagger-ui.html`, GET `/v3/api-docs`
- 추상 파이프라인: `UserQueryAbstractController<S, Q>` + `UserQueryService<Q>` 인터페이스
  - `stream()`, `generate()`, `stopStream()` 계약
  - `UserQueryAbstractController`의 주석 예시는 `/query/**` 기준이나, 실제 프로젝트는 `/multiagent/**`로 사용
- 세션 상태: 현재 `PmAgentService` 내부의 `ConcurrentHashMap<String, AtomicBoolean>`(in-memory)
- DTO:
  - `UserQueryDTO` (추상): queryText, sessionId, language(ko 기본), createdAt, metadata
  - `MultiAgentDTO` (`src/main/java/com/arms/api/multiagent/model/dto/`): `UserQueryDTO`를 상속한 빈 클래스(현재 추가 필드 없음)
- 빌드: Gradle (Java 21), 그룹 `com.arms`, 버전 `0.0.1-SNAPSHOT`
- 배포: Docker (gradle 빌드 스테이지 → temurin:21 JRE), docker-entrypoint.sh, Swarm/K8s 지향
- 환경: logback-dev/stg/live 분리. docker-entrypoint는 `SPRING_PROFILES_ACTIVE=multiagent-local` 사용
  - `application-multiagent-local.yml`이 언급되나 현재 파일이 보이지 않음(필요 시 생성/확인 대상)

> `MultiAgentDTO` 정의 위치는 `src/main/java/com/arms/api/multiagent/model/dto/MultiAgentDTO.java`로 확인됨. 현재는 추가 필드 없이 `UserQueryDTO`만 상속.

## 2. Subagent catalog

### 2.1. Backend Agent

- 역할: Java/Spring 백엔드 신규 코드 작성 및 기존 구조 확장
- 범위:
  - 새 `UserQueryService<Q>` 구현체, `UserQueryAbstractController` 상속 컨트롤러
  - `MultiAgentDTO` 하위 DTO 추가(에이전트별 추가 필드가 필요할 때)
  - 신규 REST 엔드포인트, DTO, 예외 처리, 검증
  - config 클래스(`WebResourceConfig`, `OpenApiConfig`, `MultiAgentCorsConfig` 등) 수정/확장
  - Reactive 유틸/스트림 가공 로직
  - (필요 시) 세션 저장소를 `ConcurrentHashMap`에서 정식 저장소로 교체 — DB/Storage가 생기면 해당 전문 agent와 협업
  - `application-multiagent-local.yml` 등 프로파일별 설정 정비(필요시)
- 건드리는 파일:
  - `src/main/java/com/arms/api/multiagent/`
  - `src/main/java/com/arms/egovframework/javaservice/aigenerate/l_query/`
  - `src/main/java/com/arms/config/`
  - `src/main/resources/application.yml`
  - `src/main/resources/application-*.yml` (필요 시)
- 입력: 어떤 도메인/기능을 추가할지, 어떤 역할을 맡길지
- 출력:
  - 신규/수정 자바 파일
  - 필요 시 `application.yml` 또는 프로파일별 설정 변경
  - 가능하면 빌드 확인 결과 포함(별도 Testing Agent에게 넘길지 여부는 Orchestrator가 결정)
- 금지:
  - 프론트엔드/JS 직접 수정 (→ Frontend Agent)
  - 스킬 내용 설계 (→ AI/Skill Agent)
  - Docker/배포 인프라 (→ DevOps Agent)

### 2.2. Spring AI Agent

- 역할: Spring AI를 이용한 에이전트 서비스/도구/프롬프트 설계 및 구현
- 범위:
  - 새 `*Service`(예: `CodeAgentService`, `TestAgentService`) — `ChatClient`, system prompt, tools 전달, 스트리밍/중단 패턴
  - 새 `*Tools` — `@Tool` 등록, `SKILL.md` 로드/제공 방식
  - `PmAgentService`의 system prompt / tool description 튜닝
  - 도구 설명(trigger description) 작성, progressive disclosure 구조 설계
  - Spring AI 빈 구성 관련 필요 작업(OpenAiChatModel 자동 구성 외 추가 구성이 필요하면 담당)
- 건드리는 파일:
  - `src/main/java/com/arms/api/multiagent/service/pm/` (확장 포함)
  - `src/main/resources/skills/<skill>/SKILL.md` (스킬 본문 자체는 AI/Skill Agent 주도로 쓰되, 도구와의 연결도 같이 볼 수 있음)
  - 필요 시 `application.yml` (model/연결 설정)
- 입력: 어떤 에이전트를 새로 만들지, 기존 에이전트 행동을 어떻게 바꿀지
- 출력:
  - `*Service.java`, `*Tools.java` 등
  - system prompt/tool description 수정안
- 금지:
  - 스킬의 '지침 본문(SKILL.md)' 내용 설계는 주로 AI/Skill Agent가 하되, Spring AI Agent는 그것이 도구에 어떻게 붙어 흐르는지까지 책임진다.

### 2.3. AI/Skill Agent

- 역할: `SKILL.md`(스킬 지침 본문) 자체를 설계/작성/개선
- 범위:
  - 새 스킬(`SKILL.md`) 작성: frontmatter(`name`, `description`) + 본문(절차, 출력 형식, 예외 처리, 예시 등)
  - 기존 `req-to-task` 스킬 개선
  - 스킬의 `description`(frontmatter)이 곧 `@Tool`의 trigger description이므로, Spring AI Agent와 협의해 description을 정한다
- 건드리는 파일:
  - `src/main/resources/skills/<skill-name>/SKILL.md`
- 입력: 어떤 작업을 이 스킬로 풀지, 어떤 규칙/출력 형식을 따를지
- 출력:
  - 완성된 `SKILL.md`
  - 필요 시 해당 스킬을 붙일 도구/서비스의 수정 방향 제시 (→ Spring AI Agent에게 넘김)
- 금지:
  - 실제 자바 서비스/컨트롤러 코드 직접 작성 (그건 Backend/Spring AI Agent)

### 2.4. Frontend Agent

- 역할: 정적 프론트엔드(`index.html`, `multiagent.js`, `multiagent.css`) 수정/확장
- 범위:
  - 새 UI 위젯, 채팅 UX, 에이전트 선택/전환 UI, 진행률/중단 UX
  - `multiagent.js`의 SSE 파싱/스트리밍/중단/렌더링 로직 개선
  - 경량 마크다운 렌더러 확장
  - CSS 스타일링
  - 서버 API 변경 시 프론트 연동 수정(예: `/multiagent/**` 호출 규격 변경 대응)
- 건드리는 파일:
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/js/multiagent.js`
  - `src/main/resources/static/css/multiagent.css`
- 입력: 어떤 UI/UX를 바꾸거나 추가할지
- 출력:
  - 수정된 HTML/JS/CSS
  - 필요 시 신규 정적 리소스
- 금지:
  - 자바 백엔드 코드 직접 작성

### 2.5. DevOps Agent

- 역할: Docker/배포/환경 구성/CI
- 범위:
  - `Dockerfile` 최적화 (멀티스테이지, JRE, non-root, 헬스체크, graceful shutdown 등)
  - `docker-entrypoint.sh` 개선 (프로필, 환경변수 검증, graceful shutdown, timezone 등)
  - `docker-compose.yml` / K8s 매니페스트(`Deployment`, `Service`, `Ingress` 등) 작성/정비
  - 환경별 profile 분리/검증 (dev/stg/live + `multiagent-local` 등)
  - CI(GitHub Actions 등) — 빌드/테스트/이미지/배포
  - Actuator 기반 health/liveness/readiness 구성
  - 누락된 프로파일 설정 파일(예: `application-multiagent-local.yml`) 정비
- 건드리는 파일:
  - `Dockerfile`, `docker-entrypoint.sh`, `.dockerignore`
  - `spinnaker.properties` 등 배포 관련 파일
  - 필요 시 루트 또는 `deploy/` 디렉터리 생성
  - 필요 시 `src/main/resources/application-*.yml`
- 입력: 어떤 환경/파이프라인을 구성할지
- 출력:
  - 수정된 배포/CI 파일
- 금지:
  - 비즈니스 로직/스킬/프론트 내용 설계

### 2.6. Testing Agent

- 역할: 테스트 코드 작성 및 검증 시나리오 정리
- 범위:
  - `UserQueryService` 구현체, 컨트롤러, 서비스 단위/통합 테스트
  - `PmAgentService`의 스트리밍/중단 로직 테스트 (모킹된 ChatClient/OpenAiChatModel 사용)
  - `PmAgentTools`의 `SKILL.md` 로드/파싱 테스트
  - 필요 시 프론트 SSE/중단/렌더링에 대한 테스트
  - 스킬/프롬프트 동작 검증을 위한 입출력 시나리오 세트(단순 테스트 코드 + 예시 입력/기대 출력)
- 건드리는 파일:
  - `src/test/` (현재 실제 테스트 파일은 없음, 빌드그레이들에 test 의존성은 있음)
- 입력: 무엇을 테스트할지, 어떤 시나리오를 검증할지
- 출력:
  - 테스트 클래스, 모킹 구성, 검증 시나리오
- 금지:
  - 프로덕션 비즈니스 로직 자체를 새로 설계하는 건 해당 role에게 맡김

## 3. Orchestrator 역할 및 위임 규칙

### 3.1. Orchestrator의 일

- 사용자 요청을 읽고, 이 AGENTS.md의 subagent catalog와 매칭해 적절한 agent(들)를 `delegate_task`로 호출한다.
- 요청이 여러 영역으로 걸치면 직렬/병렬로 나눈다.
- 모호하면 사용자에게 `clarify`로 확인한다(추정해서 마음대로 agent를 붙이지 않는다).
- 각 subagent가 돌려준 결과를 받아서, 전체 작업이 하나의 흐름으로 이어지도록 통합/조정한다.

현재 코드상 `MultiAgentServiceImpl`이 Orchestrator 역할을 하지만, 사용자 요청을 자동 분석해 subagent로 위임하는 로직은 아직 없다. 이 규약은 **개발 워크플로우용** Orchestrator 위임 규칙이다.

### 3.2. 요청 분류 절차

1. 요청의 목표 한 문장을 뽑아낸다(예: "PM 에이전트처럼 코드 생성 에이전트를 추가해라").
2. 그 목표를 이루는 데 필요한 변경 영역을 AGENTS.md §2 카탈로그에 매핑한다.
3. 한 agent의 범위에 전부 들어가는지 확인한다. 들어가면 몰아준다.
4. 여러 agent 범위에 걸치면, 먼저 만들어야 하는 쪽을 먼저 위임하고, 나머지에는 앞 단계 결과를 맥락으로 넘긴다.
5. 여전히 경계가 모호하면 clarify로 사용자에게 확인한다.

### 3.3. 기본 매칭 기준

- "백엔드 API/Service/Controller/DTO/설정" 관련 → Backend Agent (필요하면 Spring AI Agent와 협업)
- "새로운 AI 에이전트 서비스/도구/@Tool/프롬프트/스트리밍" 관련 → Spring AI Agent (스킬 본문이 필요하면 AI/Skill Agent도 같이)
- "스킬 지침(SKILL.md) 내용 설계/개선" → AI/Skill Agent (도구 연동은 Spring AI Agent)
- "프론트 UI/JS/CSS/채팅/SSE 연동" → Frontend Agent
- "배포/Docker/CI/환경" → DevOps Agent
- "테스트 코드/검증 시나리오" → Testing Agent
- 범위를 모르겠으면 먼저 clarify

### 3.4. 여러 agent가 필요할 때의 기본 순서

- 순서 의존이 있으면 먼저 만들어야 하는 쪽을 먼저 위임한다(예: 스킬 본문 → 도구 → 서비스 → 컨트롤러 → 프론트 연동).
- 독립 작업은 병렬 `delegate_task`로 처리한다(예: 스킬 본문을 쓰는 동안 프론트 UI를 병행).
- 병렬 작업 사이에 공유가 필요하면, 한 작업이 끝난 뒤 그 결과를 명시해서 다음 context에 넣는다. 파일 경로를 구체적으로 적어 다른 agent가 그 파일을 읽을 수 있게 한다.

### 3.5. subagent에게 넘겨야 하는 최소 맥락

각 subagent에게는 최소한 다음을 함께 준다.

- 이 AGENTS.md의 해당 agent §2 항목(역할/범위/금지)
- 현재 요청의 구체적 목표
- 의존이 있는 선행 작업 결과가 있으면 그 파일 경로와 핵심 내용
- 결과물을 어디 파일/경로에 어떤 형식으로 넣어야 하는지
- 검증 기준(가능하면 §3.7 참조)

필요하면 현재 프로젝트의 관련 파일 일부를 그대로 붙여 넣는다(예: `PmAgentService`, `PmAgentTools`, `MultiAgentServiceImpl`, `MultiAgentController`가 참고 예시일 때).

### 3.6. 모호할 때 clarify 기준

- 요청이 한 agent의 범위로 안 떨어지거나, agent 역할이 두 개 이상 겹칠 때
- "새로운 에이전트"처럼 말만 있고 어떤 도메인/기능을 담당할지 안 정해졌을 때
- 프론트/백엔드/스킬 중 어디가 먼저 필요한지 판단이 안 설 때
- 필요한 선행 정보(예: API 변경 규격, 프로파일 설정 여부, DTO 확장 필요 여부)가 아직 확보되지 않았을 때

clarify는 질문을 1회 호출로 몰아서 묻는다. 한 질문에 여러 하위 질문을 넣어도 된다.

### 3.7. 작업 결과의 통합 및 검증

- Orchestrator는 개별 agent 결과를 단순 요약하지 않고, 서로 맞물리는 지점을 확인한다.
  - 예: 새 스킬이 생기면 그 스킬의 `description`이 도구의 `@Tool` 설명과 맞물리는지
  - 예: 백엔드 API가 바뀌면 프론트 SSE 호출 규격/필드도 맞는지
  - 예: 새 에이전트가 추가되면 Orchestrator 라우팅이 실제로 그 에이전트로 가는지
- 가능하면 변경 후 빌드/테스트 통과 여부까지 확인한다. 확인이 필요한 작업은 Testing Agent에게 넘길 수 있다.
- 외부 부작용이 있는 작업은 검증 가능한 핸들/경로를 확인한다(예: 파일 경로, 엔드포인트 경로).

### 3.8. 작업 결과 보고 규칙(공통)

- agent는 작업 종료 시 변경한 파일 목록과 변경의 요점을 보고한다.
- 빌드/테스트가 필요한 작업은, 가능하면 그 결과를 함께 보고한다.
- 외부 부작용이 있는 작업(배포/업로드 등)은 검증 가능한 핸들/경로를 함께 남긴다.

## 4. 요청 → agent 매칭 예시

- "PM 에이전트처럼 코드 생성 에이전트를 새로 만들어줘"
  → Spring AI Agent(서비스/도구) + AI/Skill Agent(스킬 본문) + Backend Agent(DTO/컨트롤러/라우팅 필요시)
- "req-to-task 스킬의 모호 표현 사전을 늘려줘"
  → AI/Skill Agent
- "채팅에 에이전트 전환 드롭다운을 넣어줘"
  → Frontend Agent (+ Backend/Spring AI Agent가 에이전트 목록을 제공해야 하면 그쪽도 협업)
- "Dockerfile을 멀티스테이지로 바꾸고 헬스체크 넣어줘"
  → DevOps Agent
- "application-multiagent-local.yml 만들고 필요한 profile 설정 채워줘"
  → DevOps Agent (+ Backend Agent가 실제 필요한 설정을 정리하면 협업)
- "PmAgentService 스트리밍 테스트를 만들어줘"
  → Testing Agent

## 5. 공통 규약

### 5.1. 코드 스타일

- 기존 프로젝트 conventions을 따른다. 현재 명시적 convention 파일(`CLAUDE.md`/`AGENTS.md`/`.cursorrules`)은 없으므로, 이 AGENTS.md가 우선 규약이다.
- lombok(`@Slf4j`, `@Getter`, `@SuperBuilder`, `@NoArgsConstructor`, `@EqualsAndHashCode(callSuper = true)` 등)을 기존 코드처럼 사용한다.
- Reactive 반환(`Flux`/`Mono`)을 필요 맥락에 맞게 쓴다.
- DTO는 `UserQueryDTO` 상속/확장 패턴을 고려한다.

### 5.2. 스킬 형식

- 경로: `src/main/resources/skills/<skill-name>/SKILL.md`
- frontmatter: `---\nname: ...\ndescription: "..."\n---\n`
- `description`은 짧아야 하며, `@Tool`의 trigger description으로 그대로 쓸 수 있어야 한다.

### 5.3. 에이전트 서비스 패턴 (참조)

- `PmAgentService` 패턴을 표준 예시로 사용한다:
  - `ChatClient` + system prompt + tools + `stream().content().takeUntil(중단 플래그)`
  - 세션 상태는 `ConcurrentHashMap<String, AtomicBoolean>` 또는 향후 정식 저장소로 교체
- Orchestrator(`MultiAgentServiceImpl`)는 현재 PM 하나로만 위임하며, 라우팅이 확장되면 질의 내용에 따라 분기한다.

### 5.4. 엔드포인트/경로 참고

- 실제 컨트롤러 매핑: `/multiagent` (`MultiAgentController`)
- `UserQueryAbstractController`의 주석 예시는 `/query/**`이지만, 이 프로젝트는 `/multiagent/**`로 쓴다.
- 프론트는 `/multiagent/stream`, `/multiagent/stop-stream`, `/multiagent/validate`를 호출한다.

### 5.5. 결과 보고 규칙

- agent는 작업 종료 시 변경한 파일 목록과 변경의 요점을 보고한다.
- 빌드/테스트가 필요한 작업은, 가능하면 그 결과를 함께 보고한다.
- 외부 부작용이 있는 작업(배포/업로드 등)은 검증 가능한 핸들/경로를 함께 남긴다.

## 6. 명시적 제외

- DB/Infrastructure Agent는 현재 구성하지 않는다. 향후 세션/기록의 영구 저장이 필요해지면 별도로 둔다.
