package com.znxsgl.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znxsgl.service.LlmService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 学生端考试/作业接口（小程序「我的-作业和考试」）
 *
 * 权限：学生（管理员可代查，便于联调测试）。
 *
 * ⚠️ 适配：本项目 context-path=/api，controller 映射基于应用内路径（不含 /api 前缀）。
 *
 * 接口清单（对外实际 URL 均带 /api 前缀）：
 *   GET  /student/exam/list          我的考试/作业列表（按学生所在班级，含提交状态）
 *   GET  /student/exam/{id}/detail   考试详情+题目（不含参考答案，防作弊）
 *   POST /student/exam/{id}/submit   提交答卷（客观题自动判分 + 主观题AI评分）
 *   GET  /student/exam/{id}/result   我的作答结果与成绩（含AI评语/教师调分）
 *
 * 评分规则：
 *   - 单选/多选/判断/填空：客观题自动判分
 *   - 简答/论述：AI 批量评分（失败则标记待教师评分）
 */
@RestController
@RequestMapping("/student/exam")
@PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
public class StudentExamController {

    private final JdbcTemplate jdbc;
    private final LlmService llmService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public StudentExamController(JdbcTemplate jdbc, LlmService llmService) {
        this.jdbc = jdbc;
        this.llmService = llmService;
    }

    // ==================== 我的考试/作业列表 ====================

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> student = getStudentInfo(userId);
        Long classId = toLong(student.get("classId"));
        if (classId == null) {
            // 学生未分配班级 → 空列表
            return ResponseEntity.ok(List.of());
        }

        // 本班已发布(1)/已结束(2)的考试作业，附带本人提交状态
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT h.id, h.type, h.title, h.course_name AS courseName, h.class_name AS className, " +
            "h.start_time AS startTimeRaw, h.end_time AS endTimeRaw, h.time_limit AS timeLimit, " +
            "h.total_score AS totalScore, h.pass_score AS passScore, h.question_count AS questionCount, " +
            "h.status, s.id AS submissionId, s.total_score AS myScore, s.submit_time AS submitTimeRaw " +
            "FROM exam_homework h LEFT JOIN exam_submission s ON s.exam_id = h.id AND s.user_id = ? " +
            "WHERE h.class_id = ? AND h.status IN (1, 2) ORDER BY h.id DESC", userId, classId);

        Timestamp now = new Timestamp(System.currentTimeMillis());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("type", row.get("type"));
            item.put("title", row.get("title"));
            item.put("courseName", row.get("courseName"));
            item.put("className", row.get("className"));
            item.put("startTime", fmt(row.get("startTimeRaw")));
            item.put("endTime", fmt(row.get("endTimeRaw")));
            item.put("timeLimit", row.get("timeLimit"));
            item.put("totalScore", row.get("totalScore"));
            item.put("passScore", row.get("passScore"));
            item.put("questionCount", row.get("questionCount"));

            boolean submitted = row.get("submissionId") != null;
            item.put("submitted", submitted);
            item.put("myScore", submitted ? row.get("myScore") : null);
            item.put("submitTime", submitted ? fmt(row.get("submitTimeRaw")) : null);

            // 展示状态：教师手动结束(status=2)或过了截止时间 → ended
            Timestamp end = toTs(row.get("endTimeRaw"));
            int status = toInt(row.get("status"), 1);
            boolean ended = status == 2 || (end != null && end.before(now));
            item.put("examStatus", ended ? "ended" : "ongoing");
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // ==================== 考试详情（不含答案） ====================

    @GetMapping("/{id}/detail")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> exam = loadExam(id);
        if (exam == null) {
            return ResponseEntity.status(404).body(Map.of("error", "考试/作业不存在"));
        }
        int status = toInt(exam.get("status"), 0);
        if (status == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "该考试/作业尚未发布"));
        }
        // 班级校验（管理员豁免）
        if (!isAdmin(auth)) {
            Map<String, Object> student = getStudentInfo(userId);
            Long myClass = toLong(student.get("classId"));
            Long examClass = toLong(exam.get("class_id"));
            if (examClass != null && !Objects.equals(examClass, myClass)) {
                return ResponseEntity.status(403).body(Map.of("error", "无权限查看其他班级的考试"));
            }
        }

        // 题目（不含 answer 字段）
        List<Map<String, Object>> questions = new ArrayList<>();
        List<Map<String, Object>> qRows = jdbc.queryForList(
            "SELECT id, question_type, content, options, score, difficulty " +
            "FROM exam_question WHERE exam_id = ? ORDER BY sort_order, id", id);
        for (Map<String, Object> q : qRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", q.get("id"));
            item.put("type", q.get("question_type"));
            item.put("content", q.get("content"));
            item.put("options", parseOptions(q.get("options")));
            item.put("score", q.get("score"));
            item.put("difficulty", q.get("difficulty"));
            questions.add(item);
        }

        // 本人提交状态
        Map<String, Object> mySub = findMySubmission(id, userId);

        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp end = toTs(exam.get("end_time"));
        Timestamp start = toTs(exam.get("start_time"));
        String examStatus = (status == 2 || (end != null && end.before(now))) ? "ended"
                : (start != null && start.after(now)) ? "not_started" : "ongoing";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.get("id"));
        result.put("type", exam.get("type"));
        result.put("title", exam.get("title"));
        result.put("description", exam.get("description"));
        result.put("courseName", exam.get("course_name"));
        result.put("className", exam.get("class_name"));
        result.put("startTime", fmt(exam.get("start_time")));
        result.put("endTime", fmt(exam.get("end_time")));
        result.put("timeLimit", exam.get("time_limit"));
        result.put("totalScore", exam.get("total_score"));
        result.put("passScore", exam.get("pass_score"));
        result.put("questionCount", exam.get("question_count"));
        result.put("examStatus", examStatus);
        result.put("submitted", mySub != null);
        result.put("submissionId", mySub == null ? null : mySub.get("id"));
        result.put("questions", questions);
        return ResponseEntity.ok(result);
    }

    // ==================== 提交答卷（自动判分 + AI评分） ====================

    @PostMapping("/{id}/submit")
    public ResponseEntity<Map<String, Object>> submit(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body,
                                                      Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> exam = loadExam(id);
        if (exam == null) {
            return ResponseEntity.status(404).body(Map.of("error", "考试/作业不存在"));
        }
        if (toInt(exam.get("status"), 0) != 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "该考试/作业当前不可作答"));
        }
        // 班级校验（管理员豁免）
        Map<String, Object> student = getStudentInfo(userId);
        if (!isAdmin(auth)) {
            Long myClass = toLong(student.get("classId"));
            Long examClass = toLong(exam.get("class_id"));
            if (examClass != null && !Objects.equals(examClass, myClass)) {
                return ResponseEntity.status(403).body(Map.of("error", "无权限作答其他班级的考试"));
            }
        }
        // 重复提交校验
        if (findMySubmission(id, userId) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "你已提交过该考试/作业，不能重复提交"));
        }
        // 时间窗校验
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp start = toTs(exam.get("start_time"));
        Timestamp end = toTs(exam.get("end_time"));
        if (start != null && now.before(start)) {
            return ResponseEntity.badRequest().body(Map.of("error", "考试尚未开始"));
        }
        if (end != null && now.after(end)) {
            return ResponseEntity.badRequest().body(Map.of("error", "考试/作业已截止，无法提交"));
        }

        // 学生答案：[{questionId, answer}]
        Map<Long, String> answerMap = new HashMap<>();
        Object answersObj = body.get("answers");
        if (answersObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> am) {
                    Long qId = toLong(am.get("questionId"));
                    Object ans = am.get("answer");
                    if (qId != null) {
                        answerMap.put(qId, ans == null ? "" : ans.toString());
                    }
                }
            }
        }

        // 加载全部题目（含参考答案）
        List<Map<String, Object>> questions = jdbc.queryForList(
            "SELECT id, question_type, content, options, answer, score " +
            "FROM exam_question WHERE exam_id = ? ORDER BY sort_order, id", id);

        // 创建提交记录
        jdbc.update(
            "INSERT INTO exam_submission (exam_id, user_id, student_name, student_no, status, submit_time) " +
            "VALUES (?,?,?,?,?,NOW())",
            id, userId, str(student.get("realName")), str(student.get("studentNo")), "completed");
        Long submissionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 逐题判分：客观题自动判分，主观题收集给 AI 批量评分
        List<Map<String, Object>> subjective = new ArrayList<>();
        List<Map<String, Object>> graded = new ArrayList<>(); // 待插入的答题明细
        for (Map<String, Object> q : questions) {
            Long qId = toLong(q.get("id"));
            String type = str(q.get("question_type"));
            String reference = str(q.get("answer"));
            String userAnswer = answerMap.getOrDefault(qId, "");
            int maxScore = toInt(q.get("score"), 0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", qId);
            item.put("questionType", type);
            item.put("questionContent", str(q.get("content")));
            item.put("referenceAnswer", reference);
            item.put("userAnswer", userAnswer);
            item.put("maxScore", maxScore);

            if (isObjective(type)) {
                boolean correct = gradeObjective(type, reference, userAnswer);
                item.put("autoScore", correct ? maxScore : 0);
                item.put("aiComment", null);
            } else {
                if (userAnswer.trim().isEmpty()) {
                    // 主观题未作答：直接 0 分，不耗费 AI
                    item.put("autoScore", 0);
                    item.put("aiComment", "未作答");
                } else {
                    item.put("autoScore", null);
                    subjective.add(item);
                }
            }
            graded.add(item);
        }

        // AI 批量评分主观题
        if (!subjective.isEmpty()) {
            aiGrade(subjective);
        }

        // 写入答题明细并汇总总分
        int totalScore = 0;
        int answeredCount = 0;
        for (Map<String, Object> item : graded) {
            Integer autoScore = toInteger(item.get("autoScore"));
            if (autoScore == null) autoScore = 0;
            if (!str(item.get("userAnswer")).trim().isEmpty()) answeredCount++;
            jdbc.update(
                "INSERT INTO exam_answer (submission_id, exam_id, question_id, question_type, question_content, " +
                "reference_answer, user_answer, max_score, auto_score, final_score, ai_comment) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                submissionId, id, item.get("questionId"), item.get("questionType"), item.get("questionContent"),
                item.get("referenceAnswer"), item.get("userAnswer"), item.get("maxScore"),
                autoScore, autoScore, item.get("aiComment"));
            totalScore += autoScore;
        }

        jdbc.update("UPDATE exam_submission SET total_score = ?, auto_score = ? WHERE id = ?",
                totalScore, totalScore, submissionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("totalScore", totalScore);
        result.put("totalQuestion", questions.size());
        result.put("answeredCount", answeredCount);
        result.put("passScore", exam.get("pass_score"));
        result.put("passed", totalScore >= toInt(exam.get("pass_score"), 60));
        result.put("msg", "提交成功");
        return ResponseEntity.ok(result);
    }

    // ==================== 我的作答结果 ====================

    @GetMapping("/{id}/result")
    public ResponseEntity<Map<String, Object>> result(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> exam = loadExam(id);
        if (exam == null) {
            return ResponseEntity.status(404).body(Map.of("error", "考试/作业不存在"));
        }
        Map<String, Object> mySub = findMySubmission(id, userId);
        if (mySub == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "你尚未参加该考试/作业"));
        }

        Long submissionId = toLong(mySub.get("id"));
        List<Map<String, Object>> answers = jdbc.queryForList(
            "SELECT question_id AS questionId, question_type AS questionType, question_content AS questionContent, " +
            "reference_answer AS referenceAnswer, user_answer AS userAnswer, max_score AS maxScore, " +
            "auto_score AS autoScore, final_score AS finalScore, ai_comment AS aiComment, " +
            "teacher_comment AS teacherComment " +
            "FROM exam_answer WHERE submission_id = ? ORDER BY id", submissionId);

        int totalScore = toInt(mySub.get("total_score"), 0);
        int passScore = toInt(exam.get("pass_score"), 60);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", exam.get("id"));
        result.put("type", exam.get("type"));
        result.put("title", exam.get("title"));
        result.put("courseName", exam.get("course_name"));
        result.put("totalScore", exam.get("total_score"));
        result.put("passScore", passScore);
        result.put("myScore", totalScore);
        result.put("passed", totalScore >= passScore);
        result.put("submitTime", fmt(mySub.get("submit_time")));
        result.put("answers", answers);
        return ResponseEntity.ok(result);
    }

    // ==================== 判分逻辑 ====================

    /** 客观题类型 */
    private boolean isObjective(String type) {
        return "single_choice".equals(type) || "multiple_choice".equals(type)
                || "true_false".equals(type) || "fill_blank".equals(type);
    }

    /** 客观题自动判分 */
    private boolean gradeObjective(String type, String reference, String userAnswer) {
        if (userAnswer == null || userAnswer.trim().isEmpty()) return false;
        String ref = reference == null ? "" : reference.trim();
        String ua = userAnswer.trim();
        return switch (type) {
            case "single_choice" -> {
                String refLetter = extractLetter(ref);
                String uaLetter = extractLetter(ua);
                // 两侧都能提取到字母 → 按字母比较；否则按文本比较
                yield !refLetter.isEmpty() && !uaLetter.isEmpty()
                        ? refLetter.equalsIgnoreCase(uaLetter)
                        : normalizeText(ref).equals(normalizeText(ua));
            }
            case "multiple_choice" -> extractLetters(ref).equals(extractLetters(ua));
            case "true_false" -> {
                String refBool = normalizeBool(ref);
                String uaBool = normalizeBool(ua);
                yield refBool != null && refBool.equals(uaBool);
            }
            case "fill_blank" -> normalizeText(ref).equals(normalizeText(ua));
            default -> false;
        };
    }

    /** 提取首个选项字母（A-H），如 "A.选项" → "A" */
    private String extractLetter(String s) {
        if (s == null || s.isEmpty()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^([A-Ha-h])(?:\\s*[.、．:：)]|$)").matcher(s.trim());
        if (m.find()) return m.group(1).toUpperCase();
        return "";
    }

    /** 提取全部选项字母并排序，如 "A,C" / "AC" / "A、C" → "AC" */
    private String extractLetters(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.trim();
        TreeSet<String> letters = new TreeSet<>();
        if (t.matches("[A-Ha-h]+")) {
            // 纯字母串（如 "AB"、"ACD"）：逐字符提取
            for (char c : t.toUpperCase().toCharArray()) {
                letters.add(String.valueOf(c));
            }
        } else {
            // 带分隔符或选项文本（如 "A,B"、"A、C"、"A.栈"）：按边界提取
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("[A-Ha-h](?=[\\s,，、.．:：)]|$)").matcher(t);
            while (m.find()) letters.add(m.group().toUpperCase());
        }
        return String.join("", letters);
    }

    /** 判断题归一化："对/正确/√/T/true/是" → T，"错/错误/×/F/false/否" → F */
    private String normalizeBool(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase();
        switch (t) {
            case "对", "正确", "√", "t", "true", "是", "y", "yes" -> { return "T"; }
            case "错", "错误", "×", "x", "f", "false", "否", "n", "no" -> { return "F"; }
            default -> { return null; }
        }
    }

    /** 文本归一化：去首尾空白、全角转半角、统一小写、压缩中间空白 */
    private String normalizeText(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (c >= 'Ａ' && c <= 'Ｚ') c = (char) (c - 'Ａ' + 'A');
            else if (c >= 'ａ' && c <= 'ｚ') c = (char) (c - 'ａ' + 'a');
            else if (c >= '０' && c <= '９') c = (char) (c - '０' + '0');
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString().toLowerCase();
    }

    /** AI 批量评分主观题（结果直接写回 item 的 autoScore/aiComment） */
    private void aiGrade(List<Map<String, Object>> subjective) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是严谨的阅卷老师，请对以下学生的主观题作答逐题评分。\n");
        sb.append("评分要求：\n");
        sb.append("1. 以参考答案为基准，从语义相关性、关键点覆盖度给分，允许表达不同但含义正确。\n");
        sb.append("2. 得分必须是 0 到该题满分之间的整数。\n");
        sb.append("3. 严格输出 JSON 数组，不要任何多余文字或 markdown 标记，元素结构：\n");
        sb.append("[{\"questionId\":题号,\"score\":得分,\"comment\":\"不超过40字的评分说明\"}]\n\n");
        for (Map<String, Object> item : subjective) {
            sb.append("题号 ").append(item.get("questionId")).append("（满分 ").append(item.get("maxScore")).append(" 分）：\n");
            sb.append("题干：").append(item.get("questionContent")).append('\n');
            String ref = str(item.get("referenceAnswer"));
            sb.append("参考答案：").append(ref.isEmpty() ? "（无参考答案，请按题意合理给分）" : ref).append('\n');
            sb.append("学生作答：").append(item.get("userAnswer")).append("\n\n");
        }

        String raw = null;
        try {
            raw = llmService.chat("你是严谨的阅卷助手，只输出合法 JSON。", sb.toString(), 4096);
        } catch (Exception e) {
            System.out.println("=== AI评分调用失败: " + e.getMessage());
        }

        Map<Long, int[]> scoreMap = new HashMap<>();       // qId -> [score]
        Map<Long, String> commentMap = new HashMap<>();
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                String text = raw.trim()
                        .replaceAll("(?s)^```(?:json)?\\s*", "")
                        .replaceAll("(?s)\\s*```$", "");
                int arrStart = text.indexOf('[');
                JsonNode node = arrStart >= 0
                        ? mapper.readTree(text.substring(arrStart))
                        : mapper.readTree(text);
                if (node != null && node.isArray()) {
                    for (JsonNode n : node) {
                        Long qId = toLong(n.path("questionId").asLong());
                        int score = n.path("score").asInt(-1);
                        if (qId != null && score >= 0) {
                            scoreMap.put(qId, new int[]{score});
                            commentMap.put(qId, n.path("comment").asText(""));
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("=== AI评分结果解析失败: " + e.getMessage());
            }
        }

        for (Map<String, Object> item : subjective) {
            Long qId = toLong(item.get("questionId"));
            int maxScore = toInt(item.get("maxScore"), 0);
            int[] score = scoreMap.get(qId);
            if (score != null) {
                // 分数限制在 0~满分
                item.put("autoScore", Math.max(0, Math.min(maxScore, score[0])));
                String comment = commentMap.getOrDefault(qId, "");
                item.put("aiComment", comment.isEmpty() ? "AI自动评分" : "AI点评：" + comment);
            } else {
                // AI 评分失败 → 待教师评分
                item.put("autoScore", 0);
                item.put("aiComment", "AI评分暂不可用，待教师评分");
            }
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** 加载考试（含全部字段） */
    private Map<String, Object> loadExam(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, type, class_id, class_name, course_id, course_name, title, description, " +
            "start_time, end_time, time_limit, total_score, pass_score, status, question_count " +
            "FROM exam_homework WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询本人某考试的提交记录 */
    private Map<String, Object> findMySubmission(Long examId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, total_score, auto_score, submit_time FROM exam_submission " +
            "WHERE exam_id = ? AND user_id = ? ORDER BY id DESC LIMIT 1", examId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询学生基本信息（班级/姓名/学号） */
    private Map<String, Object> getStudentInfo(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT real_name AS realName, student_no AS studentNo, class_id AS classId " +
            "FROM user WHERE id = ?", userId);
        return rows.isEmpty() ? new HashMap<>() : rows.get(0);
    }

    /** options 字段（JSON数组字符串）→ List<String> */
    private List<String> parseOptions(Object optionsJson) {
        List<String> options = new ArrayList<>();
        if (optionsJson == null) return options;
        try {
            JsonNode node = mapper.readTree(optionsJson.toString());
            if (node.isArray()) {
                for (JsonNode o : node) options.add(o.asText());
            }
        } catch (Exception ignored) { }
        return options;
    }

    private String fmt(Object ts) {
        if (ts == null) return null;
        try {
            if (ts instanceof Timestamp t) return t.toLocalDateTime().format(DTF);
            return ts.toString();
        } catch (Exception e) {
            return ts.toString();
        }
    }

    private Timestamp toTs(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof Timestamp t) return t;
            if (o instanceof LocalDateTime ldt) return Timestamp.valueOf(ldt);
        } catch (Exception ignored) { }
        return null;
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private int toInt(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }

    private Integer toInteger(Object o) {
        if (o == null) return null;
        try { return Integer.valueOf(o.toString()); } catch (Exception e) { return null; }
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        try { return Long.valueOf(o.toString()); } catch (Exception e) { return null; }
    }
}
