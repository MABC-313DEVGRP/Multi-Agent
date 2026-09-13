package com.arms.api.multiagent.controller;

import com.arms.api.multiagent.model.dto.MultiAgentDTO;
import com.arms.api.multiagent.service.MultiAgentServiceImpl;
import com.arms.egovframework.javaservice.aigenerate.l_query.controller.UserQueryAbstractController;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/multiagent")
@Tag(name = "MultiAgentController", description = "MultiAgent(Orchestrator) AI 파이프라인 컨트롤러 — MABC 2026 결선 프로토타입")
public class MultiAgentController
        extends UserQueryAbstractController<MultiAgentServiceImpl, MultiAgentDTO> {

    @Autowired
    public MultiAgentController(MultiAgentServiceImpl service) {
        setQueryService(service);
    }
}
