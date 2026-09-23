package com.znxsgl.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.znxsgl.service.LlmService;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 考试/作业管理接口（教师/管理员）
 *
 * ⚠️ 适配：本项目 context-path=/api，controller 映射基于应用内路径（不含 /api 前缀）。
 *
 * 接口清单（对外实际 URL 均带 /api 前缀，下面均指应用内路径）：
 *   GET  /exam-homework/list                                 查询列表（教师/管理员）
 *   POST /exam-homework/publish                              发布考试/作业
 *   DELETE /exam-homework/{id}                               删除
 *   PUT  /exam-homework/{id}/status                          上架/下架
 *   POST /exam-homework/generate-by-range                     AI 按课程范围出题预览
 *   POST /exam-homework/generate-by-document                  AI 识别文档出题预览
 *   GET  /exam-homework/{examId}/submissions                 学生提交列表（含答题明细）
 *   POST /exam-homework/submission-answer/{answerId}/adjust-score   教师调整简答题分数
 */
@RestController
@RequestMapping("/exam-homework")
@PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
public class ExamHomeworkController {

    private final JdbcTemplate jdbc;
    private final LlmService llmService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ExamHomeworkController(JdbcTemplate jdbc, LlmService llmService) {
        this.jdbc = jdbc;
        this.llmService = llmService;
    }

    /** 确保考试作业相关表存在（幂等，方便首次部署直接可用） */
    @PostConstruct
    public void initSchema() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS exam_homework (
              id bigint NOT NULL AUTO_INCREMENT,
              type varchar(20) DEFAULT 'exam',
              class_id bigint DEFAULT NULL,
              class_name varchar(100) DEFAULT NULL,
              course_id bigint DEFAULT NULL,
              course_name varchar(200) DEFAULT NULL,
              title varchar(200) NOT NULL,
              description text,
              start_time datetime DEFAULT NULL,
              end_time datetime DEFAULT NULL,
              time_limit int DEFAULT 60,
              total_score int DEFAULT 100,
              pass_score int DEFAULT 60,
              status int DEFAULT 1,
              publish_mode varchar(20) DEFAULT 'immediate',
              scheduled_time datetime DEFAULT NULL,
              question_mode varchar(20) DEFAULT 'ai-range',
              question_count int DEFAULT 0,
              created_by bigint DEFAULT NULL,
              created_at datetime DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_class (class_id),
              KEY idx_course (course_id),
              KEY idx_created (created_by)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS exam_question (
              id bigint NOT NULL AUTO_INCREMENT,
              exam_id bigint NOT NULL,
              question_type varchar(20) DEFAULT NULL,
              content text,
              options json,
              answer text,
              score int DEFAULT 0,
              difficulty varchar(20) DEFAULT 'medium',
              sort_order int DEFAULT 0,
              PRIMARY KEY (id),
              KEY idx_exam (exam_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS exam_submission (
              id bigint NOT NULL AUTO_INCREMENT,
              exam_id bigint NOT NULL,
              user_id bigint DEFAULT NULL,
              student_name varchar(100) DEFAULT NULL,
              student_no varchar(50) DEFAULT NULL,
              status varchar(20) DEFAULT 'completed',
              total_score int DEFAULT 0,
              auto_score int DEFAULT NULL,
              submit_time datetime DEFAULT NULL,
              created_at datetime DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (id),
              KEY idx_exam (exam_id),
              KEY idx_user (user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS exam_answer (
              id bigint NOT NULL AUTO_INCREMENT,
              submission_id bigint NOT NULL,
              exam_id bigint DEFAULT NULL,
              question_id bigint DEFAULT NULL,
              question_type varchar(20) DEFAULT NULL,
              question_content text,
              reference_answer text,
              user_answer text,
              max_score int DEFAULT 0,
              auto_score int DEFAULT NULL,
              final_score int DEFAULT NULL,
              ai_comment text,
              teacher_comment text,
              adjust_count int DEFAULT 0,
              PRIMARY KEY (id),
              KEY idx_submission (submission_id),
              KEY idx_exam (exam_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """);
    }

    // ==================== 列表 ====================

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            Authentication auth) {

        Long userId = (Long) auth.getPrincipal();
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        StringBuilder sql = new StringBuilder(
            "SELECT id, type, title, class_name AS className, course_name AS courseName, " +
            "DATE_FORMAT(start_time,'%Y-%m-%d %H:%i') AS startTime, " +
            "DATE_FORMAT(end_time,'%Y-%m-%d %H:%i') AS endTime, " +
            "time_limit AS timeLimit, total_score AS totalScore, pass_score AS passScore, " +
            "question_count AS questionCount, status, publish_mode AS publishMode " +
            "FROM exam_homework WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        // 教师只能看到自己创建的，管理员可看全部
        if (!admin) {
            sql.append(" AND created_by = ? ");
            args.add(userId);
        }
        if (classId != null) {
            sql.append(" AND class_id = ? ");
            args.add(classId);
        }
        if (type != null && !type.isEmpty()) {
            sql.append(" AND type = ? ");
            args.add(type);
        }
        if (status != null && !status.isEmpty()) {
            Integer st = switch (status) {
                case "draft" -> 0;
                case "ended" -> 2;
                default -> 1;
            };
            sql.append(" AND status = ? ");
            args.add(st);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (title LIKE ? OR class_name LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY id DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return ResponseEntity.ok(rows);
    }

    // ==================== 发布 ====================

    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody Map<String, Object> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        String type = str(body, "type", "exam");
        Long classId = longVal(body, "classId");
        String title = str(body, "title");
        if (title.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "标题不能为空"));
        }
        String description = str(body, "description");
        String startStr = str(body, "startTime");
        String endStr = str(body, "endTime");
        int timeLimit = body.get("timeLimit") == null ? 60 : Integer.parseInt(body.get("timeLimit").toString());
        int totalScore = body.get("totalScore") == null ? 100 : Integer.parseInt(body.get("totalScore").toString());
        int passScore = body.get("passScore") == null ? 60 : Integer.parseInt(body.get("passScore").toString());
        String publishMode = str(body, "publishMode", "immediate");
        String scheduledStr = str(body, "scheduledTime");
        String questionMode = str(body, "questionMode", "ai-range");
        Long courseId = longVal(body, "courseId");

        // 班级名 / 课程名冗余
        String className = null;
        if (classId != null) {
            try {
                className = jdbc.queryForObject("SELECT class_name FROM class_info WHERE id = ?", String.class, classId);
            } catch (Exception ignored) { }
        }
        String courseName = null;
        if (courseId != null) {
            try {
                courseName = jdbc.queryForObject("SELECT course_name FROM course WHERE id = ?", String.class, courseId);
            } catch (Exception ignored) { }
        }

        // 状态：定时发布 → 草稿(0)，立即发布 → 进行中(1)
        int status = "scheduled".equals(publishMode) ? 0 : 1;
        Timestamp start = parseTs(startStr);
        Timestamp end = parseTs(endStr);
        Timestamp scheduled = parseTs(scheduledStr);

        // 题目
        List<Map<String, Object>> questions = new ArrayList<>();
        Object qs = body.get("questions");
        if (qs instanceof List<?> qList) {
            for (Object q : qList) {
                if (q instanceof Map<?, ?> qm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> qq = (Map<String, Object>) qm;
                    questions.add(qq);
                }
            }
        }
        int questionCount = questions.size();

        jdbc.update(
            "INSERT INTO exam_homework (type, class_id, class_name, course_id, course_name, title, description, " +
            "start_time, end_time, time_limit, total_score, pass_score, status, publish_mode, scheduled_time, " +
            "question_mode, question_count, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            type, classId, className, courseId, courseName, title, description, start, end,
            timeLimit, totalScore, passScore, status, publishMode, scheduled, questionMode, questionCount, userId);

        Long examId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 若题目数>1，均分总分；否则按题目自带分值
        int perScore;
        if (questionCount > 0 && body.get("questions") != null) {
            perScore = totalScore / questionCount;
        } else {
            perScore = totalScore;
        }

        int i = 0;
        for (Map<String, Object> q : questions) {
            String qtype = str(q, "type", "short_answer");
            String content = str(q, "content");
            String answer = str(q, "answer");
            int score = q.get("score") == null ? perScore : Integer.parseInt(q.get("score").toString());
            String difficulty = str(q, "difficulty", "medium");
            String optionsJson = null;
            Object opts = q.get("options");
            if (opts instanceof List<?> optList && !optList.isEmpty()) {
                ArrayNode arr = mapper.createArrayNode();
                for (Object o : optList) {
                    if (o != null) arr.add(o.toString());
                }
                optionsJson = arr.toString();
            }
            jdbc.update(
                "INSERT INTO exam_question (exam_id, question_type, content, options, answer, score, difficulty, sort_order) " +
                "VALUES (?,?,?,?,?,?,?,?)",
                examId, qtype, content, optionsJson, answer, score, difficulty, i++);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", examId);
        result.put("msg", "发布成功");
        return ResponseEntity.ok(result);
    }

    // ==================== 删除 / 上架下架 ====================

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!canManage(id, userId, auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限操作该考试/作业"));
        }
        jdbc.update("DELETE FROM exam_question WHERE exam_id = ?", id);
        jdbc.update("DELETE FROM exam_answer WHERE exam_id = ?", id);
        jdbc.update("DELETE FROM exam_submission WHERE exam_id = ?", id);
        jdbc.update("DELETE FROM exam_homework WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("msg", "删除成功"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body,
                                                            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!canManage(id, userId, auth)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限操作该考试/作业"));
        }
        Object s = body.get("status");
        if (s == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status 不能为空"));
        }
        int status = Integer.parseInt(s.toString());
        jdbc.update("UPDATE exam_homework SET status = ? WHERE id = ?", status, id);
        return ResponseEntity.ok(Map.of("msg", "状态已更新", "status", status));
    }

    // ==================== AI 出题预览 ====================

    @PostMapping("/generate-by-range")
    public ResponseEntity<Map<String, Object>> generateByRange(@RequestBody Map<String, Object> body) {
        Long courseId = longVal(body, "courseId");
        List<String> questionTypes = strList(body, "questionTypes");
        String difficulty = str(body, "difficulty", "medium");
        int count = body.get("count") == null ? 20 : Integer.parseInt(body.get("count").toString());

        String courseName = "本课程";
        if (courseId != null) {
            try {
                courseName = jdbc.queryForObject("SELECT course_name FROM course WHERE id = ?", String.class, courseId);
            } catch (Exception ignored) { }
        }

        String typesDesc = describeTypes(questionTypes);
        String prompt = "你是一位教学出题专家，课程为《" + courseName + "》。请根据课程知识点生成一套试题。\n"
            + "要求：\n"
            + "1. 共生成 " + count + " 道题。\n"
            + "2. 题型分布：必须只包含以下题型，且覆盖尽量均匀：" + typesDesc + "。\n"
            + "3. 难度：" + difficultyLabel(difficulty) + "。\n"
            + "4. 客观题（单选/多选/判断/填空）需给出参考答案。\n"
            + "5. 严格输出一个 JSON 数组，不要包含任何多余文字或 markdown 代码块标记。每个元素结构：\n"
            + "{\"type\":\"single_choice|multiple_choice|true_false|fill_blank|short_answer\",\"content\":\"题干\","
            + "\"options\":[\"A.选项文本\",\"B.选项文本\",\"C.选项文本\",\"D.选项文本\"],\"answer\":\"参考答案\",\"difficulty\":\"easy|medium|hard\",\"score\":5}\n"
            + "注意：选择题 options 每项以 A. B. C. D. 开头；判断题 answer 为“正确”或“错误”；填空/简答题无则省略 options 字段。";

        String raw = llmService.chat("你是严谨的试题生成助手，只输出合法 JSON。", prompt, 8192);
        List<Map<String, Object>> questions = parseQuestions(raw, questionTypes, count);

        if (questions.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "AI 生成题目失败，请稍后重试或减少题目数量", "questions", new ArrayList<>()));
        }
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    @PostMapping("/generate-by-document")
    public ResponseEntity<Map<String, Object>> generateByDocument(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "questionTypes", required = false) String questionTypesParam,
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @RequestParam(value = "count", required = false) String countParam) {

        List<String> questionTypes = new ArrayList<>();
        if (questionTypesParam != null && !questionTypesParam.isEmpty()) {
            for (String s : questionTypesParam.split(",")) {
                if (!s.trim().isEmpty()) questionTypes.add(s.trim());
            }
        }
        if (questionTypes.isEmpty()) {
            questionTypes = List.of("single_choice", "multiple_choice", "true_false");
        }
        int count = 20;
        if (countParam != null) {
            try { count = Integer.parseInt(countParam); } catch (Exception ignored) { }
        }

        // 抽取文本：.txt 直接读取；其他格式仅传递文件名，让 AI 尽力而为
        String docContent = "";
        String docName = file != null ? file.getOriginalFilename() : "未命名文档";
        if (file != null) {
            String fn = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            if (fn.endsWith(".txt") || "text/plain".equalsIgnoreCase(file.getContentType())) {
                try {
                    docContent = new String(file.getBytes(), StandardCharsets.UTF_8);
                    if (docContent.length() > 6000) docContent = docContent.substring(0, 6000);
                } catch (Exception ignored) { }
            }
        }

        String excerpt = docContent.isEmpty() ? "（无法直接解析该文档正文，请依据文档主题《" + docName + "》合理拟题）" : docContent;

        String prompt = "以下是一份教学文档，请根据其知识要点生成试题。\n文档主题：" + docName + "\n"
            + "文档内容（节选）：\n" + excerpt + "\n"
            + "要求：\n1. 共 " + count + " 道题。\n2. 题型只包含：" + describeTypes(questionTypes) + "。\n"
            + "3. 难度：" + difficultyLabel(difficulty) + "。\n"
            + "4. 严格输出 JSON 数组，无多余文字，元素结构同“按范围出题”约定（含 type/content/options/answer/difficulty/score）。";

        String raw = llmService.chat("你是严谨的试题生成助手，只输出合法 JSON。", prompt, 8192);
        List<Map<String, Object>> questions = parseQuestions(raw, questionTypes, count);
        if (questions.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "AI 识别文档生成题目失败，请确认文档内容后重试", "questions", new ArrayList<>()));
        }
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    // ==================== 学生提交管理 ====================

    @GetMapping("/{examId}/submissions")
    public ResponseEntity<List<Map<String, Object>>> submissions(@PathVariable Long examId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!isAdmin(auth)) {
            // 教师仅能看自己创建的考试
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam_homework WHERE id = ? AND created_by = ?", Integer.class, examId, userId);
            if (cnt == null || cnt == 0) {
                return ResponseEntity.status(403).body(Collections.emptyList());
            }
        }

        List<Map<String, Object>> subs = jdbc.queryForList(
            "SELECT id, exam_id AS examId, student_name AS studentName, student_no AS studentNo, " +
            "status, total_score AS totalScore, auto_score AS autoScore, " +
            "DATE_FORMAT(submit_time,'%Y-%m-%d %H:%i') AS submitTime " +
            "FROM exam_submission WHERE exam_id = ? ORDER BY id DESC", examId);

        for (Map<String, Object> sub : subs) {
            Long sid = ((Number) sub.get("id")).longValue();
            List<Map<String, Object>> answers = jdbc.queryForList(
                "SELECT id, question_type AS questionType, question_content AS questionContent, " +
                "reference_answer AS referenceAnswer, user_answer AS userAnswer, " +
                "max_score AS maxScore, auto_score AS autoScore, final_score AS finalScore, " +
                "ai_comment AS aiComment, teacher_comment AS teacherComment, adjust_count AS adjustCount " +
                "FROM exam_answer WHERE submission_id = ? ORDER BY id", sid);
            sub.put("answers", answers);
        }
        return ResponseEntity.ok(subs);
    }

    @PostMapping("/submission-answer/{answerId}/adjust-score")
    public ResponseEntity<Map<String, Object>> adjustScore(@PathVariable Long answerId,
                                                           @RequestBody Map<String, Object> body,
                                                           Authentication auth) {
        Object fs = body.get("finalScore");
        if (fs == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "finalScore 不能为空"));
        }
        int finalScore = Integer.parseInt(fs.toString());
        String teacherComment = str(body, "teacherComment");

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("SELECT id, adjust_count AS c, max_score AS m FROM exam_answer WHERE id = ?", answerId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "答题记录不存在"));
        }
        int currentCount = ((Number) row.get("c")).intValue();
        int maxScore = ((Number) row.get("m")).intValue();
        // 每题最多调整 2 次
        if (currentCount >= 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "该题已调整满 2 次，无法继续调整"));
        }
        if (finalScore < 0 || finalScore > maxScore) {
            return ResponseEntity.badRequest().body(Map.of("error", "分数必须在 0 ~ " + maxScore + " 之间"));
        }

        jdbc.update("UPDATE exam_answer SET final_score = ?, teacher_comment = ?, adjust_count = adjust_count + 1 WHERE id = ?",
                finalScore, teacherComment, answerId);
        int newCount = currentCount + 1;

        // 同步更新 submission 的最终总分 = 该份提交所有 answer 的 final_score/auto_score 之和
        Long submissionId = ((Number) row.get("id")).longValue();
        Map<String, Object> sub = jdbc.queryForMap(
            "SELECT id, submission_id AS sid FROM exam_answer WHERE id = ?", answerId);
        Number sid = (Number) sub.get("sid");
        if (sid != null) {
            Object total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(final_score, auto_score, 0)),0) FROM exam_answer WHERE submission_id = ?",
                Object.class, sid.longValue());
            jdbc.update("UPDATE exam_submission SET total_score = ? WHERE id = ?",
                    ((Number) total).intValue(), sid.longValue());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newScore", finalScore);
        result.put("remainingAdjust", Math.max(0, 2 - newCount));
        return ResponseEntity.ok(result);
    }

    // ==================== 辅助方法 ====================

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** 校验当前用户是否有权管理该考试（管理员可管所有，教师只管自己创建的） */
    private boolean canManage(Long examId, Long userId, Authentication auth) {
        if (isAdmin(auth)) return true;
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM exam_homework WHERE id = ? AND created_by = ?", Integer.class, examId, userId);
        return cnt != null && cnt > 0;
    }

    private static final DateTimeFormatter DTF_M = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DTF_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Timestamp parseTs(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        // 兼容前端 datetime-local "yyyy-MM-ddTHH:mm" 或 "yyyy-MM-ddTHH:mm:ss"
        String t = s.trim().replace('T', ' ');
        try {
            return Timestamp.valueOf(t.length() > 16 ? LocalDateTime.parse(t, DTF_MS) : LocalDateTime.parse(t, DTF_M));
        } catch (Exception e) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(t, DTF_M));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private List<String> strList(Map<String, Object> map, String key) {
        List<String> result = new ArrayList<>();
        Object v = map.get(key);
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) result.add(o.toString());
            }
        }
        return result;
    }

    private String describeTypes(List<String> types) {
        Map<String, String> m = Map.of(
            "single_choice", "单选题",
            "multiple_choice", "多选题",
            "true_false", "判断题",
            "fill_blank", "填空题",
            "short_answer", "简答题");
        if (types.isEmpty()) return "单选题";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(m.getOrDefault(types.get(i), types.get(i)));
        }
        return sb.toString();
    }

    private String difficultyLabel(String d) {
        return switch (d == null ? "" : d) {
            case "easy" -> "简单";
            case "hard" -> "困难";
            case "mixed" -> "简单中等困难混合";
            default -> "中等";
        };
    }

    /** 从 LLM 返回文本中稳健地解析出 JSON 数组 */
    private List<Map<String, Object>> parseQuestions(String raw, List<String> allowedTypes, int wantCount) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            String text = raw.trim();
            // 去掉 markdown 代码块包裹
            text = text.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
            JsonNode node;
            int arrStart = text.indexOf('[');
            if (arrStart >= 0) {
                String jsonPart = text.substring(arrStart);
                node = mapper.readTree(jsonPart);
            } else {
                node = mapper.readTree(text);
            }
            if (node == null) return out;
            if (node.isArray()) {
                for (JsonNode q : node) {
                    if (out.size() >= wantCount) break;
                    Map<String, Object> item = normalizeQuestion(q);
                    if (item != null) out.add(item);
                }
            } else if (node.has("questions") && node.get("questions").isArray()) {
                for (JsonNode q : node.get("questions")) {
                    if (out.size() >= wantCount) break;
                    Map<String, Object> item = normalizeQuestion(q);
                    if (item != null) out.add(item);
                }
            }
        } catch (Exception e) {
            // 解析失败时尝试正则提取（容错）
            out.addAll(regexFallback(raw, allowedTypes, wantCount));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeQuestion(JsonNode q) {
        String type = q.path("type").asText("");
        if (type.isEmpty()) return null;
        String content = q.path("content").asText("");
        int score = q.path("score").asInt(5);
        String difficulty = q.path("difficulty").asText("medium");
        String answer = q.path("answer").asText("");
        List<String> options = new ArrayList<>();
        JsonNode optArr = q.path("options");
        if (optArr.isArray()) {
            for (JsonNode o : optArr) options.add(o.asText());
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("content", content);
        item.put("score", score);
        item.put("difficulty", difficulty);
        item.put("answer", answer);
        if (options.isEmpty() && (type.equals("single_choice") || type.equals("multiple_choice"))) {
            // 选择题缺少选项时补齐
            options = List.of("A.选项一", "B.选项二", "C.选项三", "D.选项四");
        }
        item.put("options", options);
        return item;
    }

    private List<Map<String, Object>> regexFallback(String raw, List<String> allowedTypes, int wantCount) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Matcher m = Pattern.compile("\\{[^{}]*?\"type\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\\}").matcher(raw);
            while (m.find() && out.size() < wantCount) {
                try {
                    JsonNode q = mapper.readTree(m.group());
                    Map<String, Object> item = normalizeQuestion(q);
                    if (item != null) out.add(item);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v == null || v.toString().isEmpty() ? def : v.toString();
    }

    private Long longVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try {
            return Long.valueOf(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}