package com.arms.api.multiagent.service.sheet;

import com.arms.api.multiagent.model.dto.ReqSheetExportDTO;
import com.arms.api.multiagent.model.vo.ReqSheetFileVO;
import com.arms.api.multiagent.model.vo.ReqSheetVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 요구사항정의서 파일 입력·파일 산출물. 서버는 파일을 보관하지 않는다 — 요청 본문을 메모리에서 읽고 바로 버린다.
 * 본문은 코덱 집계 한도(256KB)를 거치지 않도록 원본 바이트로 받아 여기서 상한을 건다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReqSheetService {

    // 업로드 파일 상한 — 프론트 AS_UPLOAD_MAX_BYTES 와 같은 값
    private static final int PARSE_MAX_BYTES = 5 * 1024 * 1024;

    // 내보내기 요청 본문 상한 — 변환한 행의 원본 값과 답변 마크다운을 담는다
    private static final int EXPORT_MAX_BYTES = 5 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final ReqSheetReader reqSheetReader;
    private final ReqSheetWriter reqSheetWriter;

    public Mono<ReqSheetVO> parse(String fileName, Flux<DataBuffer> body) {
        return DataBufferUtils.join(body, PARSE_MAX_BYTES)
                .publishOn(Schedulers.boundedElastic())
                .map(buffer -> read(fileName, buffer))
                .onErrorResume(DataBufferLimitException.class,
                        e -> Mono.just(ReqSheetVO.failed(fileName, ReqSheetVO.TOO_LARGE)))
                .defaultIfEmpty(ReqSheetVO.failed(fileName, ReqSheetVO.NO_DATA));
    }

    public Mono<ReqSheetFileVO> export(Flux<DataBuffer> body) {
        return DataBufferUtils.join(body, EXPORT_MAX_BYTES)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(buffer -> Mono.justOrEmpty(write(buffer)));
    }

    private ReqSheetVO read(String fileName, DataBuffer buffer) {
        try (InputStream in = buffer.asInputStream(true)) {
            ReqSheetVO sheet = reqSheetReader.read(fileName, in);
            log.info("ReqSheetService :: 파일 읽기 | fileName={}, parsed={}, reason={}, sheet={}, rows={}",
                    fileName, sheet.isParsed(), sheet.getReason(), sheet.getSheetName(),
                    sheet.getRows() == null ? 0 : sheet.getRows().size());
            return sheet;
        } catch (IOException e) {
            log.warn("ReqSheetService :: 파일 본문을 읽지 못함 | fileName={} | error={}", fileName, e.getMessage());
            return ReqSheetVO.failed(fileName, ReqSheetVO.UNREADABLE);
        }
    }

    private Optional<ReqSheetFileVO> write(DataBuffer buffer) {
        try (InputStream in = buffer.asInputStream(true)) {
            Optional<ReqSheetFileVO> file = reqSheetWriter.write(objectMapper.readValue(in, ReqSheetExportDTO.class));
            log.info("ReqSheetService :: 파일 만들기 | 결과={}", file.map(ReqSheetFileVO::getFileName).orElse("매핑된 행 없음"));
            return file;
        } catch (IOException e) {
            log.warn("ReqSheetService :: 내보내기 요청을 읽지 못함 | error={}", e.getMessage());
            return Optional.empty();
        }
    }
}
