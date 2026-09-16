package com.arms.api.multiagent.service.sheet;

import com.arms.api.multiagent.model.vo.ReqSheetVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 요구사항정의서 파일(.xlsx·.csv)을 헤더와 데이터 행으로 읽는다. 파일은 메모리에서만 다룬다.
 * 시트와 헤더 행은 스킬 STEP 1 역할 인식 점수로 고른다. 찾지 못하면 추측하지 않고 사유만 돌려준다.
 */
@Slf4j
@Component
public class ReqSheetReader {

    // 헤더 행은 앞쪽 몇 행 안에 있다고 본다(제목 행·빈 행이 앞에 오는 양식 대비)
    private static final int HEADER_SCAN_ROWS = 10;

    // 엑셀에서 CSV 로 저장할 때의 기본 인코딩
    private static final Charset MS949 = Charset.forName("MS949");

    public ReqSheetVO read(String fileName, InputStream in) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".xlsx")) {
                return readXlsx(fileName, in);
            }
            if (lower.endsWith(".csv")) {
                return readCsv(fileName, in);
            }
            return ReqSheetVO.failed(fileName, ReqSheetVO.UNSUPPORTED);
        } catch (EncryptedDocumentException e) {
            return ReqSheetVO.failed(fileName, ReqSheetVO.ENCRYPTED);
        } catch (Exception e) {
            log.warn("ReqSheetReader :: 파일을 읽지 못함 | fileName={} | error={}", fileName, e.getMessage());
            return ReqSheetVO.failed(fileName, ReqSheetVO.UNREADABLE);
        }
    }

    private ReqSheetVO readXlsx(String fileName, InputStream in) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            // 확장자만 .xlsx 인 구형 .xls 는 지원하지 않는다
            if (workbook.getSpreadsheetVersion() != SpreadsheetVersion.EXCEL2007) {
                return ReqSheetVO.failed(fileName, ReqSheetVO.UNSUPPORTED);
            }

            DataFormatter formatter = new DataFormatter();
            formatter.setUseCachedValuesForFormulaCells(true);

            List<String> sheetNames = new ArrayList<>();
            String bestSheet = null;
            List<List<String>> bestTable = null;
            int bestHeader = -1;
            long bestScore = -1;
            int bestRows = -1;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                if (workbook.isSheetHidden(i) || workbook.isSheetVeryHidden(i)) {
                    continue;
                }
                Sheet sheet = workbook.getSheetAt(i);
                sheetNames.add(sheet.getSheetName());

                List<List<String>> table = readTable(sheet, formatter);
                int header = headerRow(table);
                if (header < 0) {
                    continue;
                }
                long score = ReqSheetRoles.score(table.get(header));
                int rows = dataRows(table, header).size();
                if (score > bestScore || (score == bestScore && rows > bestRows)) {
                    bestSheet = sheet.getSheetName();
                    bestTable = table;
                    bestHeader = header;
                    bestScore = score;
                    bestRows = rows;
                }
            }

            if (bestSheet == null) {
                return ReqSheetVO.failed(fileName, ReqSheetVO.NO_HEADER);
            }
            return toVO(fileName, bestSheet, sheetNames, bestTable, bestHeader);
        }
    }

    private List<List<String>> readTable(Sheet sheet, DataFormatter formatter) {
        List<List<String>> table = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || row.getLastCellNum() < 0) {
                table.add(List.of());
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                values.add(cell == null ? "" : normalizeLineBreaks(formatter.formatCellValue(cell)));
            }
            table.add(values);
        }
        return table;
    }

    private ReqSheetVO readCsv(String fileName, InputStream in) throws IOException {
        List<List<String>> table = parseCsv(decode(in.readAllBytes()));
        int header = headerRow(table);
        if (header < 0) {
            return ReqSheetVO.failed(fileName, ReqSheetVO.NO_HEADER);
        }
        return toVO(fileName, null, List.of(), table, header);
    }

    // BOM 이 있으면 UTF-8, 없으면 엄격한 UTF-8 로 시도하고 깨지면 MS949 로 읽는다
    private String decode(byte[] bytes) throws CharacterCodingException {
        boolean bom = bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
        ByteBuffer buffer = bom ? ByteBuffer.wrap(bytes, 3, bytes.length - 3) : ByteBuffer.wrap(bytes);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer.duplicate())
                    .toString();
        } catch (CharacterCodingException e) {
            if (bom) {
                throw e;
            }
            return MS949.decode(buffer).toString();
        }
    }

    // RFC 4180 — 쉼표 구분, 따옴표 안의 쉼표·줄바꿈·"" 이스케이프
    private List<List<String>> parseCsv(String text) {
        List<List<String>> table = new ArrayList<>();
        List<String> row = new ArrayList<>();
        List<Character> field = new ArrayList<>();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.add('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.add(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(toText(field));
                field.clear();
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(toText(field));
                field.clear();
                table.add(row);
                row = new ArrayList<>();
            } else {
                field.add(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(toText(field));
            table.add(row);
        }
        return table;
    }

    private String toText(List<Character> chars) {
        return normalizeLineBreaks(chars.stream().map(String::valueOf).collect(Collectors.joining()));
    }

    private int headerRow(List<List<String>> table) {
        int best = -1;
        long bestScore = 0;
        for (int r = 0; r < Math.min(HEADER_SCAN_ROWS, table.size()); r++) {
            List<String> row = table.get(r);
            if (!ReqSheetRoles.isHeader(row)) {
                continue;
            }
            long score = ReqSheetRoles.score(row);
            if (score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        return best;
    }

    // 헤더 폭에 맞추고, 값이 모두 빈 행은 버린다(양식이 수백 행까지 스타일만 잡혀 있는 경우 대비)
    private List<List<String>> dataRows(List<List<String>> table, int header) {
        int width = table.get(header).size();
        return table.subList(header + 1, table.size()).stream()
                .map(row -> IntStream.range(0, width).mapToObj(i -> i < row.size() ? row.get(i) : "").toList())
                .filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
                .toList();
    }

    private ReqSheetVO toVO(String fileName, String sheetName, List<String> sheetNames,
                           List<List<String>> table, int header) {
        List<String> headers = table.get(header).stream().map(String::strip).toList();
        List<List<String>> rows = dataRows(table, header);
        if (rows.isEmpty()) {
            return ReqSheetVO.noData(fileName, sheetName);
        }
        return ReqSheetVO.of(fileName, sheetName, sheetNames, headers, ReqSheetRoles.roles(headers), rows);
    }

    private String normalizeLineBreaks(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
