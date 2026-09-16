package com.arms.api.multiagent.model.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 내려받을 파일. 파일명은 영문·숫자로 짓는다(스킬 STEP 5).
 */
@Getter
@Builder
public class ReqSheetFileVO {

    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_TYPE = "text/csv;charset=UTF-8";

    private final String fileName;
    private final String contentType;
    private final byte[] bytes;

    public static ReqSheetFileVO xlsx(byte[] bytes) {
        return ReqSheetFileVO.builder()
                .fileName("requirements_tasks.xlsx")
                .contentType(XLSX_TYPE)
                .bytes(bytes)
                .build();
    }

    public static ReqSheetFileVO csv(byte[] bytes) {
        return ReqSheetFileVO.builder()
                .fileName("requirements_tasks.csv")
                .contentType(CSV_TYPE)
                .bytes(bytes)
                .build();
    }
}
