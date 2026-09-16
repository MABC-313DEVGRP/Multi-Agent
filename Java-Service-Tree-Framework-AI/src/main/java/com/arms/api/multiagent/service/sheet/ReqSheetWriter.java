package com.arms.api.multiagent.service.sheet;

import com.arms.api.multiagent.model.dto.ReqSheetExportDTO;
import com.arms.api.multiagent.model.vo.ReqSheetFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.WorkbookUtil;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * 변환 결과를 원본 요구사항정의서 컬럼 구성으로 되돌려 파일로 만든다. xlsx 가 실패하면 CSV(UTF-8 BOM)로 대신한다.
 * 담당자·요청자 실명은 역할로 바꾸고, 역할을 알 수 없으면 비운다(스킬 STEP 2 개인정보 제거).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReqSheetWriter {

    private static final String DEFAULT_SHEET = "요구사항정의서";

    // 엑셀 셀 한 칸이 담을 수 있는 최대 글자 수
    private static final int EXCEL_CELL_MAX = 32767;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String DEFECT_LABEL = "[결손 진단]";
    private static final String NAME_REMOVED = "📌 요청자·담당자 실명을 지웠어요";

    // 상세 내용 블록 순서와, 원본 셀에 블록 이름이 없을 때 쓰는 이름(요구사항정의서 양식 표기)
    private static final List<Map.Entry<String, String>> BLOCK_ORDER = List.of(
            Map.entry(ReqTaskAnswerParser.BACKGROUND, "[개요]"),
            Map.entry(ReqTaskAnswerParser.SCOPE, "[상세 기능 요구사항]"),
            Map.entry(ReqTaskAnswerParser.CONSTRAINT, "[제약 조건]"),
            Map.entry(ReqTaskAnswerParser.PREMISE, "[전제]"),
            Map.entry(ReqTaskAnswerParser.ACCEPTANCE, "[인수 조건]"),
            Map.entry(ReqTaskAnswerParser.DELIVERABLE, "[산출물]"));

    private static final Pattern BLOCK_LABEL = Pattern.compile("^\\[(.+)]$");
    // "DEV: 홍길동"
    private static final Pattern CODE_ROLE = Pattern.compile("^([A-Za-z]{2,10})\\s*[:：]\\s*\\S.*$");
    // "기획팀 홍길동"
    private static final Pattern TEAM_ROLE = Pattern.compile("^(\\S+?)(팀|본부|부|실)\\s+\\S.*$");
    private static final Map<String, String> CODE_NAMES = Map.of("DEV", "개발", "SE", "시스템");

    private final ReqTaskAnswerParser answerParser;

    public Optional<ReqSheetFileVO> write(ReqSheetExportDTO request) {
        List<String> headers = request.getHeaders() == null ? List.of() : request.getHeaders();
        Map<String, Integer> roles = ReqSheetRoles.roles(headers);
        Integer idColumn = roles.get(ReqSheetRoles.ID);
        if (idColumn == null
                || (!roles.containsKey(ReqSheetRoles.CONTENT) && !roles.containsKey(ReqSheetRoles.REMARK))) {
            return Optional.empty();
        }

        // 모델이 ID 대소문자를 바꿔 써도 원본 행과 맞춘다(프론트 convertedIds 와 같은 기준)
        Map<String, Map<String, List<String>>> sections = answerParser.parse(request.getAnswers()).entrySet().stream()
                .collect(Collectors.toMap(entry -> idKey(entry.getKey()), Map.Entry::getValue, (earlier, later) -> later));
        List<List<String>> sourceRows = request.getRows() == null ? List.of() : request.getRows();
        List<List<String>> rows = sourceRows.stream()
                .filter(row -> sections.containsKey(idKey(cell(row, idColumn))))
                .map(row -> toRow(headers.size(), roles, row, sections.get(idKey(cell(row, idColumn)))))
                .toList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(ReqSheetFileVO.xlsx(writeXlsx(request.getSheetName(), headers, rows, roles)));
        } catch (Exception e) {
            log.warn("ReqSheetWriter :: xlsx 생성 실패, CSV 로 대신 | error={}", e.getMessage());
            return Optional.of(ReqSheetFileVO.csv(writeCsv(headers, rows)));
        }
    }

    private List<String> toRow(int width, Map<String, Integer> roles, List<String> source,
                               Map<String, List<String>> section) {
        List<String> row = new ArrayList<>(IntStream.range(0, width).mapToObj(i -> cell(source, i)).toList());

        String title = String.join(" ", section.getOrDefault(ReqTaskAnswerParser.TITLE, List.of())).strip();
        Integer nameColumn = roles.get(ReqSheetRoles.NAME);
        if (nameColumn != null && !title.isEmpty()) {
            row.set(nameColumn, title);
        }

        boolean nameRemoved = false;
        for (String role : List.of(ReqSheetRoles.REQUESTER, ReqSheetRoles.ASSIGNEE)) {
            Integer column = roles.get(role);
            if (column == null) {
                continue;
            }
            String converted = toRole(row.get(column));
            if (converted == null) {
                row.set(column, "");
                nameRemoved = true;
            } else {
                row.set(column, converted);
            }
        }

        Integer contentColumn = roles.get(ReqSheetRoles.CONTENT);
        Integer remarkColumn = roles.get(ReqSheetRoles.REMARK);
        String blocks = blocks(contentColumn == null ? "" : cell(source, contentColumn), section);
        String defects = defects(section, nameRemoved);

        if (contentColumn != null) {
            row.set(contentColumn, remarkColumn == null ? join(blocks, defects) : blocks);
        }
        if (remarkColumn != null) {
            String appended = contentColumn == null ? join(blocks, defects) : defects;
            row.set(remarkColumn, join(row.get(remarkColumn), appended));
        }
        return row;
    }

    private String blocks(String originalContent, Map<String, List<String>> section) {
        Map<String, String> labels = originalLabels(originalContent);
        return BLOCK_ORDER.stream()
                .filter(entry -> section.containsKey(entry.getKey()))
                .map(entry -> labels.getOrDefault(entry.getKey(), entry.getValue())
                        + "\n" + String.join("\n", section.get(entry.getKey())))
                .collect(Collectors.joining("\n"));
    }

    // 원본 셀에 쓰인 블록 이름을 역할별로 다시 쓴다 — "[제약 조건 / 비기능 요구사항]" 같은 조직 표기를 지킨다
    private Map<String, String> originalLabels(String content) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String line : content.split("\n")) {
            Matcher matcher = BLOCK_LABEL.matcher(line.strip());
            if (!matcher.matches()) {
                continue;
            }
            String role = ReqSheetRoles.blockRoleOf(matcher.group(1));
            if (role != null) {
                labels.putIfAbsent(role, "[" + matcher.group(1).strip() + "]");
            }
        }
        return labels;
    }

    private String defects(Map<String, List<String>> section, boolean nameRemoved) {
        List<String> lines = new ArrayList<>(section.getOrDefault(ReqTaskAnswerParser.DEFECT, List.of()));
        if (nameRemoved) {
            lines.add(NAME_REMOVED);
        }
        return lines.isEmpty() ? "" : DEFECT_LABEL + "\n" + String.join("\n", lines);
    }

    // 실명은 역할로 바꾼다. 역할을 알 수 없으면 null(빈칸 처리)
    private String toRole(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || "-".equals(text)) {
            return text;
        }
        List<String> converted = new ArrayList<>();
        for (String part : text.split("[/,\n]")) {
            String item = part.strip();
            if (item.isEmpty()) {
                continue;
            }
            Matcher code = CODE_ROLE.matcher(item);
            Matcher team = TEAM_ROLE.matcher(item);
            String role;
            if (code.matches()) {
                String key = code.group(1).toUpperCase(Locale.ROOT);
                role = CODE_NAMES.getOrDefault(key, key) + " 담당";
            } else if (team.matches()) {
                role = team.group(1) + " 담당";
            } else {
                return null;
            }
            if (!converted.contains(role)) {
                converted.add(role);
            }
        }
        return converted.isEmpty() ? null : String.join(", ", converted);
    }

    private byte[] writeXlsx(String sheetName, List<String> headers, List<List<String>> rows,
                             Map<String, Integer> roles) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(
                    sheetName == null || sheetName.isBlank() ? DEFAULT_SHEET : sheetName));

            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setWrapText(true);
            bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);

            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.cloneStyleFrom(bodyStyle);
            headerStyle.setFont(bold);

            writeRow(sheet.createRow(0), headers, headerStyle);
            for (int i = 0; i < rows.size(); i++) {
                writeRow(sheet.createRow(i + 1), rows.get(i), bodyStyle);
            }
            for (int column = 0; column < headers.size(); column++) {
                sheet.setColumnWidth(column, columnWidth(column, roles) * 256);
            }
            sheet.createFreezePane(0, 1);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeRow(Row row, List<String> values, CellStyle style) {
        for (int column = 0; column < values.size(); column++) {
            String value = values.get(column) == null ? "" : values.get(column);
            if (value.length() > EXCEL_CELL_MAX) {
                log.warn("ReqSheetWriter :: 셀 글자 수 초과로 자름 | column={}, length={}", column, value.length());
                value = value.substring(0, EXCEL_CELL_MAX);
            }
            Cell cell = row.createCell(column);
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private int columnWidth(int column, Map<String, Integer> roles) {
        if (Objects.equals(roles.get(ReqSheetRoles.CONTENT), column)) {
            return 100;
        }
        if (Objects.equals(roles.get(ReqSheetRoles.NAME), column)) {
            return 32;
        }
        return 16;
    }

    private byte[] writeCsv(List<String> headers, List<List<String>> rows) {
        String text = Stream.concat(Stream.of(headers), rows.stream())
                .map(row -> row.stream().map(this::csvField).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(UTF8_BOM.length + body.length);
        out.write(UTF8_BOM, 0, UTF8_BOM.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private String csvField(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String idKey(String id) {
        return id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
    }

    private static String cell(List<String> row, int index) {
        return row != null && index < row.size() && row.get(index) != null ? row.get(index) : "";
    }

    private static String join(String first, String second) {
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasSecond = second != null && !second.isBlank();
        if (hasFirst && hasSecond) {
            return first + "\n\n" + second;
        }
        return hasFirst ? first : (hasSecond ? second : "");
    }
}
