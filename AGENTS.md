# AGENTS.md — MultiAgent (A-RMS) 개발 에이전트 규약

## 1. 프로젝트 개요 (간략)

- Spring Boot 3.5.6 + WebFlux(Reactive), Actuator, springdoc Swagger
- Spring AI 1.0.0-M8 + Upstage Solar Pro 4 (base-url: `https://api.upstage.ai`, model: `solar-pro4`)
- frontend: 바닐라 JS (`index.html`, `multiagent.js` 681줄, `multiagent.css`)
- 현재는 PM 에이전트(`req-to-task` 스킬) 하나만 구현된 MABC 2026 프로토타입
- Orchestrator: `MultiAgentServiceImpl`이 `UserQueryService<MultiAgentDTO>`를 구현, 현재는 PM 하나로만 위임
- 추상 파이프라인: `UserQueryAbstractController<S, Q>` + `UserQueryService<Q>` 인터페이스 (`stream` / `generate` / `stopStream`)
- 세션 상태: 현재 `ConcurrentHashMap<String, AtomicBoolean>` (`PmAgentService` 내부)
- 빌드: Gradle (Java 21), Docker + Swarm/K8s 지향
- 환경: dev/stg/live logback 분리. `application.yml`은 현재 Spring AI(OpenAI/Upstage) 설정 포함

> `MultiAgentDTO`의 정확한 정의 위치는 아직 확인되지 않았다. Backend Agent가 먼저 찾고 정리한다.

## 2. Subagent catalog

### 2.1. Backend Agent

- 역할: Java/Spring 백엔드 신규 코드 작성 및 기존 구조 확장
- 범위:
  - 새 `UserQueryService<Q>` 구현체, 새 `UserQueryAbstractController` 상속 컨트롤러
  - `MultiAgentDTO` 정의 위치 확인/정리, 하위 DTO 추가
  - 신규 REST 엔드포인트, DTO, 예외 처리, 검증
  - config 클래스(`WebResourceConfig`, `OpenApiConfig`, `MultiAgentCorsConfig` 등) 수정/확장
  - Reactive 유틸/스트림 가공 로직
  - (필요 시) 세션 저장소를 `ConcurrentHashMap`에서 정식 저장소로 교체 — DB/Storage가 생기면 DB Agent와 협업
- 건드리는 파일:
  - `src/main/java/com/arms/api/multiagent/`
  - `src/main/java/com/arms/egovframework/.../l_query/`
  - `src/main/java/com/arms/config/`
  - `src/main/resources/application.yml` (필요 시)
- 입력: 어떤 도메인/기능을 추가할지, 어떤 역할을 맡길지
- 출력:
  - 신규/수정 자바 파일
  - 필요 시 `application.yml` 변경
  - 가능하면 빌드/테스트 확인 결과 포함 (별도 Testing Agent에게 넘길지 여부는 Orchestrator가 결정)
- 금지:
  - 프론트엔드/TS/JS 직접 수정 (→ Frontend Agent)
  - 스킬 내용 설계 (→ AI/Skill Agent)
  - Docker/배포 인프라 (→ DevOps Agent)

### 2.2. Spring AI Agent

- 역할: Spring AI를 이용한 에이전트 서비스/도구/프롬프트 설계 및 구현
- 범위:
  - 새 `*Service`(예: `CodeAgentService`, `TestAgentService`) — `ChatClient`, system prompt, tools 전달, 스트리밍/중단 패턴
  - 새 `*Tools` — `@Tool` 등록, `SKILL.md` 로드/제공 방식
  - `PmAgentService`의 system prompt / tool description 튜닝
  - 도구 설명(trigger description) 작성, progressive disclosure 구조 설계
- 건드리는 파일:
  - `src/main/java/com/arms/api/multiagent/service/pm/` (확장 포함)
  - `src/main/resources/skills/<skill>/SKILL.md` (스킬 본문 자체는 AI/Skill Agent 주도로 쓰되, 여기서 도구와의 연결도 같이 볼 수 있음)
  - 필요 시 `application.yml`
- 입력: 어떤 에이전트를 새로 만들지, 기존 에이전트 행동을 어떻게 바꿀지
- 출력:
  - `*Service.java`, `*Tools.java` 등
  - system prompt/tool description 수정안
- 금지:
  - 스킬의 "지침 본문(SKILL.md)" 내용 설계는 주로 AI/Skill Agent가 하되, Spring AI Agent는 그것이 도구에 어떻게 붙어 흐르는지까지 책임진다.

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
  - 서버 API 변경 시 프론트 연동 수정
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
  - `docker-entrypoint.sh` 개선
  - `docker-compose.yml` / K8s 매니페스트(`Deployment`, `Service`, `Ingress` 등) 작성/정비
  - 환경별 profile 분리/검증 (dev/stg/live 등)
  - CI(GitHub Actions 등) — 빌드/테스트/이미지/배포
  - Actuator 기반 health/liveness/readiness 구성
- 건드리는 파일:
  - `Dockerfile`, `docker-entrypoint.sh`, `.dockerignore`
  - `spinnaker.properties` 등 배포 관련 파일
  - 필요 시 루트 또는 `deploy/` 디렉터리 생성
- 입력: 어떤 환경/파이프라인을 구성할지
- 출력:
  - 수정된 배포/CI 파일
- 금지:
  - 비즈니스 로직/스킬/프론트 내용 설계

### 2.6. Testing Agent

- 역할: 테스트 코드 작성 및 검증 시나리오 정리
- 범위:
  - `UserQueryService` 구현체, 컨트롤러, 서비스 단위/통합 테스트
  - `PmAgentService`의 스트리밍/중단 로직 테스트(모킹 `ChatClient`/`OpenAiChatModel`)
  - `PmAgentTools`의 `SKILL.md` 로드/파싱 테스트
  - 필요 시 프론트 SSE/중단/렌더링 관련 테스트
  - 스킬/프롬프트 동작 검증용 입출력 시나리오 세트
- 건드리는 파일:
  - `src/test/` (현재 테스트 파일은 없음, 빌드그레이들에 test 의존성은 있음)
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

### 3.2. 기본 매칭 기준

- "백엔드 API/Service/Controller/DTO/설정" 관련 → Backend Agent (필요하면 Spring AI Agent와 협업)
- "새로운 AI 에이전트 서비스/도구/@Tool/프롬프트/스트리밍" 관련 → Spring AI Agent (스킬 본문이 필요하면 AI/Skill Agent도 같이)
- "스킬 지침(SKILL.md) 내용 설계/개선" → AI/Skill Agent (도구 연동은 Spring AI Agent)
- "프론트 UI/JS/CSS/채팅/SSE 연동" → Frontend Agent
- "배포/Docker/CI/환경" → DevOps Agent
- "테스트 코드/검증 시나리오" → Testing Agent
- 범위를 모르겠으면 먼저 clarify

### 3.3. 여러 agent가 필요할 때

- 같은 에이전트 내부에서 끝나는 일은 한 agent에게 몰아서 준다.
- 에이전트가 여러 개 필요하면, 의존 순서상 먼저 만들어야 하는 쪽을 먼저 위임하고, 그다음 에이전트에 "앞 단계 결과"를 맥락으로 넘긴다.
- 병렬로 해도 되는 독립 작업은 병렬 `delegate_task`로 처리한다.

### 3.4. 작업 결과 검증 기준(공통)

- Java 코드: 기존 패턴(`UserQueryService`, `UserQueryAbstractController`, `@Tool` 등)을 따르고, 컴파일/빌드상 문제가 없어야 한다. 가능하면 빌드 확인까지 수행한다.
- 스킬: frontmatter에 `name`/`description`이 있고, 본문은 progressive disclosure 설계 원칙을 따른다. `description`은 `@Tool` trigger로 쓸 수 있는 짧은 문장.
- 프론트: 기존 `multiagent.js`의 패턴(SSE, 중단, 채팅 상태 관리)을 해치지 않는다.
- DevOps: 기존 `Dockerfile`/`docker-entrypoint.sh`와 충돌하지 않고, 실제 기동/배포 맥락에서 말이 되어야 한다.

## 4. 요청 → agent 매칭 예시

- "PM 에이전트처럼 코드 생성 에이전트를 새로 만들어줘"
  → Spring AI Agent(서비스/도구) + AI/Skill Agent(스킬 본문) + Backend Agent(DTO/컨트롤러/라우팅 필요시)
- "req-to-task 스킬의 모호 표현 사전을 늘려줘"
  → AI/Skill Agent
- "채팅에 에이전트 전환 드롭다운을 넣어줘"
  → Frontend Agent (+ Backend/Spring AI Agent가 에이전트 목록을 제공해야 하면 그쪽도 협업)
- "Dockerfile을 멀티스테이지로 바꾸고 헬스체크 넣어줘"
  → DevOps Agent
- "PmAgentService 스트리밍 테스트를 만들어줘"
  → Testing Agent

## 5. 공통 규약

### 5.1. 코드 스타일

- 기존 프로젝트 conventions을 따른다. 현재 명시적 convention 파일(`CLAUDE.md`/`AGENTS.md`/`.cursorrules`)은 없으므로, 이 AGENTS.md가 우선 규약이다.
- lombok(`@Slf4j`, `@Getter`, `@SuperBuilder`, `@NoArgsConstructor` 등)을 기존 코드처럼 사용한다.
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
- Orchestrator(`MultiAgentServiceImpl`)는 라우팅 로직이 확장되면 질의 내용에 따라 분기한다.

### 5.4. 결과 보고 규칙

- agent는 작업 종료 시 변경한 파일 목록과 변경의 요점을 보고한다.
- 빌드/테스트가 필요한 작업은, 가능하면 그 결과를 함께 보고한다.
- 외부 부작용이 있는 작업(배포/업로드 등)은 검증 가능한 핸들/경로를 함께 남긴다.

## 6. 명시적 제외

- DB/Infrastructure Agent는 현재 구성하지 않는다. 향후 세션/기록의 영구 저장이 필요해지면 별도로 둔다.
