package com.arms.api.multiagent.model.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 파일 산출물 요청. 변환한 행의 원본 값과, 대화에 나온 변환 답변(마크다운)을 함께 받는다.
 */
@Getter
@NoArgsConstructor
public class ReqSheetExportDTO {

    private String fileName;

    private String sheetName;

    private List<String> headers;

    private List<List<String>> rows;

    private List<String> answers;
}
