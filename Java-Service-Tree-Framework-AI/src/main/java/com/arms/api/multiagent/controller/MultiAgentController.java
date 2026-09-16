package com.arms.api.multiagent.controller;

import com.arms.api.multiagent.model.dto.MultiAgentDTO;
import com.arms.api.multiagent.model.vo.ReqSheetVO;
import com.arms.api.multiagent.service.MultiAgentServiceImpl;
import com.arms.api.multiagent.service.sheet.ReqSheetService;
import com.arms.egovframework.javaservice.aigenerate.l_query.controller.UserQueryAbstractController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/multiagent")
@Tag(name = "MultiAgentController", description = "PMBOK 기반 PM 어시스턴트 (오케스트레이터 + PM expert 서브에이전트)")
public class MultiAgentController
        extends UserQueryAbstractController<MultiAgentServiceImpl, MultiAgentDTO> {

    private final ReqSheetService reqSheetService;

    @Autowired
    public MultiAgentController(MultiAgentServiceImpl service, ReqSheetService reqSheetService) {
        setQueryService(service);
        this.reqSheetService = reqSheetService;
    }

    @Operation(summary = "[Excel] 요구사항정의서 파일 읽기",
            description = "📎 로 올린 .xlsx·.csv 원본 바이트를 받아 요구사항 시트의 헤더와 행을 돌려준다. 파일은 저장하지 않는다.")
    @PostMapping(value = "/excel/parse", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ReqSheetVO> parseExcel(@RequestParam("fileName") String fileName,
                                       @RequestBody Flux<DataBuffer> body) {
        return reqSheetService.parse(fileName, body);
    }

    @Operation(summary = "[Excel] 변환 결과 파일 내려받기",
            description = "변환한 행과 변환 답변을 받아 원본 컬럼 구성의 xlsx(실패 시 CSV UTF-8 BOM)를 돌려준다. 옮길 행이 없으면 422.")
    @PostMapping(value = "/excel/export")
    public Mono<ResponseEntity<byte[]>> exportExcel(@RequestBody Flux<DataBuffer> body) {
        return reqSheetService.export(body)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(file.getContentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(file.getFileName()).build().toString())
                        .body(file.getBytes()))
                .defaultIfEmpty(ResponseEntity.unprocessableEntity().build());
    }
}
