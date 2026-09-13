package com.arms.api.multiagent.service.pm;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PM 에이전트의 스킬 도구. MABC 2026 예선 통과작 {@code req-to-task}를 그대로 이식한다.
 *
 * <p>스킬 본문(SKILL.md)은 리소스 파일 원문을 그대로 읽어 반환한다 — 요약·재작성하지 않는다.
 * frontmatter의 {@code description}이 곧 이 도구의 트리거 설명이다(Claude Agent Skills와 동일한
 * progressive disclosure: 짧은 description은 항상 모델에 노출되고, 본문은 도구 호출 시에만 로드).</p>
 */
@Slf4j
@Service
public class PmAgentTools {

    private static final String SKILL_RESOURCE = "classpath:skills/req-to-task/SKILL.md";
    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
    private static final Pattern DESCRIPTION_LINE = Pattern.compile(
            "^description:\\s*\"?(.*?)\"?\\s*$", Pattern.MULTILINE);

    private final ResourcePatternResolver resourceLoader;

    private String skillBody;

    public PmAgentTools(ResourcePatternResolver resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadSkill() {
        Resource resource = resourceLoader.getResource(SKILL_RESOURCE);
        String raw;
        try (var in = resource.getInputStream()) {
            raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("PmAgentTools :: SKILL.md 로드 실패 — " + SKILL_RESOURCE, e);
        }

        Matcher m = FRONTMATTER.matcher(raw.strip());
        if (!m.matches()) {
            throw new IllegalStateException("PmAgentTools :: SKILL.md frontmatter 파싱 실패 (---로 시작하는 블록 필요)");
        }
        this.skillBody = m.group(2).strip();
        log.info("PmAgentTools :: req-to-task 스킬 로드 완료 | bodyLength={}", skillBody.length());
    }

    @Tool(description = "요구사항정의서 행이나 요구사항 문장을 개발 착수용 작업 지시서로 바꾼다. "
            + "'요구사항 정리해줘', '이 요구사항 티켓으로 만들어줘', '요구사항정의서 변환', '완료 조건 뽑아줘', "
            + "'요구사항 검토해줘' 등에 이 도구를 사용한다. 변환과 함께 완료 조건이 측정 가능한지 판정하고, "
            + "빠진 정보는 지어내지 않고 확인 질문으로 되돌린다. 요구사항을 새로 발굴·작성하거나 이슈 트래커에 "
            + "실제로 등록하는 일은 이 도구의 범위가 아니다.")
    public String loadReqToTaskSkill() {
        log.info("PmAgentTools :: req-to-task 스킬 호출됨");
        return skillBody;
    }
}
