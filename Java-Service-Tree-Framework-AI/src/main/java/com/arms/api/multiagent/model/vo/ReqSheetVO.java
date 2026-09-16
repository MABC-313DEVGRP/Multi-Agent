package com.arms.api.multiagent.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 요구사항정의서 파일을 읽은 결과. 읽지 못하면 parsed=false 와 사유(reason)만 담는다.
 */
@Getter
@Builder
public class ReqSheetVO {

    public static final String UNSUPPORTED = "UNSUPPORTED";
    public static final String ENCRYPTED = "ENCRYPTED";
    public static final String NO_HEADER = "NO_HEADER";
    public static final String NO_DATA = "NO_DATA";
    public static final String TOO_LARGE = "TOO_LARGE";
    public static final String UNREADABLE = "UNREADABLE";

    private final boolean parsed;
    private final String reason;
    private final String fileName;
    private final String sheetName;
    private final List<String> sheetNames;
    private final List<String> headers;
    private final Map<String, Integer> roles;
    private final List<List<String>> rows;

    public static ReqSheetVO of(String fileName, String sheetName, List<String> sheetNames,
                                List<String> headers, Map<String, Integer> roles, List<List<String>> rows) {
        return ReqSheetVO.builder()
                .parsed(true)
                .fileName(fileName)
                .sheetName(sheetName)
                .sheetNames(sheetNames)
                .headers(headers)
                .roles(roles)
                .rows(rows)
                .build();
    }

    public static ReqSheetVO failed(String fileName, String reason) {
        return ReqSheetVO.builder()
                .parsed(false)
                .reason(reason)
                .fileName(fileName)
                .build();
    }

    public static ReqSheetVO noData(String fileName, String sheetName) {
        return ReqSheetVO.builder()
                .parsed(false)
                .reason(NO_DATA)
                .fileName(fileName)
                .sheetName(sheetName)
                .build();
    }
}
