# 小林毕业设计 ← aiStudy 功能搬迁方案

> 本文档用于在新对话中执行搬迁，内容自包含，可直接作为任务说明使用。
> 生成时间：2026-08-18
> 源项目：`C:\Users\jay\Desktop\myBishe\aiStudy`
> 目标项目：`C:\Users\jay\Desktop\作业\小林毕业设计`

---

## 〇、背景与目标

把 aiStudy（教务系统）的功能搬到小林毕业设计（课程笔记 App）中，实现"班级制教务"化改造。

**要搬的 11 个功能**：班级制教务、学生管理、教师管理、学期管理、教室管理、课时管理（章节学习不用）、教师课表、一键排课、调课、课表导入、AI 出题/评估。

**核心原则**：能照抄的就不要重构。已核实两边后端技术栈**完全相同**，大量代码可 1:1 照抄。

**明确不搬**：课程章节/课时学习（CourseChapterController）、聊天/消息、考试/作业发布、小程序原生功能（课段/拍照/笔记/知识库保留原样）。

---

## 一、硬性结论（已核实）

1. **后端技术栈完全同源**：
   - 两边都是 **Spring Boot 3.2.0 + MyBatis-Plus 3.5.5 + MySQL + jjwt 0.12.3**（jakarta 命名空间一致）
   - 把 aiStudy 的 `com.znxsgl` 包整个拷进小林后端，改 `@SpringBootApplication(scanBasePackages)` 和 `@MapperScan` 即可共存，无需改包名/命名空间
2. **只有 3 处必须"适配"**（其余照抄）：
   - `user` 表语义冲突（aiStudy 账号密码+角色 vs 小林 openid）
   - `course` 表命名冲突（同库不能有两张 `course`）
   - **AI 出题强依赖"章节学习"**（已确认 [QuizController.java](C:\Users\jay\Desktop\myBishe\aiStudy\backend\src\main\java\com\znxsgl\controller\QuizController.java) 中：没学章节直接拒绝出题、按章节 RAG 检索出题），而章节明确不搬

---

## 二、功能 → 照抄清单

### 2.1 后端功能模块

| 功能 | aiStudy 源文件 | 搬入方式 | 依赖的表 |
|---|---|---|---|
| 班级制教务 | `AdminUserController.java`、`ClassInfo.java` | 照抄 | `class_info` |
| 学生管理 | `AdminUserController.java`（列表/增删改/导入学生） | 照抄 | `user`（需合并） |
| 教师管理 | `AdminUserController.java`、`Teacher.java` | 照抄 | `teacher`、`department` |
| 学期管理 | `SemesterController.java` | 照抄 | `semester` |
| 教室管理 | `AdminScheduleController.java` | 照抄 | `classroom` |
| 课时管理 | `TeachingTask.java`（周课时/连堂/优先级） | 照抄 | `teaching_task`、`course_class` |
| 教师课表 | `ScheduleService.java`、`ScheduleController.java` | 照抄 | `schedule` |
| 一键排课 | `AutoScheduleService.java`、`ScheduleConflictChecker.java` | 照抄 | `schedule`、`schedule_lock`、`schedule_rule` |
| 调课 | `TeacherScheduleAdjustController.java` | 照抄（通知部分可选，见第四节开关1） | `schedule_lock` |
| 课表导入 | `ScheduleImportController.java`、`ScheduleImportTeacherMatcher.java` | 照抄 | `course_import_record` |
| AI 出题/评估 | `QuizController.java`、`LlmService.java` | 必须适配（见 3.3） | `quiz_session`、`quiz_answer`、`question_bookmark`、`user_course_difficulty`、`wrong_analysis_cache` |

### 2.2 鉴权与支撑件（必须一起搬）

- 鉴权：`SecurityConfig`、`JwtAuthFilter`、`JwtUtil`、`AuthController`、`RbacPermissionEvaluator` + RBAC 表（`sys_role`/`sys_permission`/`sys_user_role`/`sys_role_permission`）
- 基础：`GlobalExceptionHandler`、`MyBatisPlusConfig`、`MyBatisPlusMetaObjectHandler`、`WebConfig`
- 小林 pom 需要补的依赖：`spring-boot-starter-security`、`okhttp`、`poi`/`poi-ooxml`（导入用）、`hutool`、`guava`

### 2.3 前端

- aiStudy 管理端 `web-admin-react`（React）**整套照抄**进小林，已覆盖 11 个功能的全部管理界面（班级/学生/教师/学期/教室/一键排课/调课/课表导入/AI出题）。只改 API 地址配置（见第六节风险2）。
- 小林小程序（原生微信）保留，但**登录页改账号密码登录、课表页数据源改接新接口**（参考 aiStudy 自己的 uni-app 小程序）。
- 小林 Vue3 教师端：去留见第四节开关2。

---

## 三、必须适配的 3 处（避坑重点）

### 3.1 `user` 表合并

| | aiStudy `user` | 小林 `user` |
|---|---|---|
| 登录方式 | 用户名+密码（BCrypt）+角色 | 微信 openid |
| 关键字段 | `username`/`password_hash`/`role(1学生/2教师/3管理员)`/`class_id`/`student_no` | `openid`/`nickname`/`school`/`grade`/`semester` |

**方案**：以 aiStudy 的 `user` 表为**主体**（支撑全部 11 个功能），把小林的 `openid`/`school`/`grade`/`semester` 等字段**追加合并**进来。登录统一走 aiStudy 的账号密码 + JWT + RBAC。小林小程序登录页改成账号密码登录。

### 3.2 `course` 表命名冲突（同库不能有两张 course）

| | aiStudy `course` | 小林 `course` |
|---|---|---|
| 语义 | 课程主数据（一门课→多班，`teacher_id`/`semester`/`credit`） | 个人课表条目（`user_id`/`day_of_week`/`start_time`/`weeks`） |
| 引用面 | 极大（排课/导入/教师/出题全用） | 集中（CourseController/CourseServiceImpl） |

**方案**：aiStudy 的表**全部原名照抄不动**（引用面大）；把**小林自己的 `course` 表改名为 `personal_course`**（实体 `@TableName("personal_course")` + 小林 CourseServiceImpl 里的原生 SQL 同步）。两套体系平行共存：
- 教务体系 = aiStudy 模型（学生课表来自班级排课的 `schedule`）
- 个人学习体系 = 小林原有模型（课段/笔记/知识库挂在 `personal_course` 上，语义不变）

### 3.3 AI 出题去"章节依赖"

`QuizController.java` 现在强制「先学章节才能出题 + 按章节 RAG 检索 + 复习推荐章节」。不搬章节，因此：

- `generate`：删掉 `chapter_read_progress` 强校验；出题上下文从"章节 RAG 检索"改为**「课程描述 + 小林知识库文档」**（小林已有 `knowledge_base`/`knowledge_document`，天然适合当出题素材）
- `review-plan`：删掉 `recommendedChapters` 章节推荐（返回薄弱点/学习计划即可）
- 其余（多线程出题、15分钟缓存、MD5指纹、评分评估、错题解析、难度自适应）**全部照抄**

---

## 四、需要拍板的 2 个开关

1. **调课通知要不要**：aiStudy 调课会推 WebSocket 通知给学生（依赖 `chat_message`/`notification` 表）。你没列聊天功能。
   - A. 连通知一起搬（照抄 `ScheduleNotifyService` + 相关表）——行为与 aiStudy 完全一致
   - B. 只搬调课+锁定，不推通知（更轻，但学生端少一个提醒）
2. **小林 Vue3 教师端去留**：搬入 React 管理端后，它已覆盖"教师课表"等全部功能。小林原有 Vue3 教师端是**保留并行**还是**弃用**（建议：先保留不删，跑通后再定）。

---

## 五、执行顺序（新对话照此推进）

| 阶段 | 内容 | 验证 |
|---|---|---|
| 0 | 备份小林库和代码；给小林 pom 补依赖 | `mvn compile` 通过 |
| 1 | 建库：照抄 aiStudy 全部相关表 + 合并 `user` + `course`→`personal_course` | SQL 可重复执行 |
| 2 | 搬鉴权：Security/JwtUtil/Auth/RBAC + 实体/mapper | 账号密码能登录、接口被拦截 |
| 3 | 搬教务模块：班级/学生/教师/学期/教室/课时 CRUD | 管理端能增删改查 |
| 4 | 搬排课：AutoScheduleService + 冲突检查 + 调课 + 课表查询 | 一键排课、调课成功 |
| 5 | 搬课表导入：Excel 解析+预览+确认+上架 | 用现成测试表导入成功 |
| 6 | 适配 AI 出题（改数据源为课程描述/知识库） | 出题/评估/错题解析可用 |
| 7 | 搬 React 管理端（改 API 地址）；小程序登录+课表页适配 | 前端全流程可点通 |
| 8 | 联调 + 边界测试（排课冲突/重复导入/周次边界） | 按边界用例跑一遍 |

**顺序理由**：先建库和鉴权（地基），再 CRUD，再排课核心（最复杂），再导入和 AI，最后前端。每阶段独立可验证，避免"全搬完才发现跑不起来"。

---

## 六、主要风险提示

1. **Spring Security 会拦截小林原有接口**：小林现在没有 Security 框架，引入 aiStudy 的 `SecurityConfig` 后，小林现有的课段/资料/知识库接口也要纳入鉴权或白名单——**搬入时最容易被忽略的坑**，务必在阶段 2 一起处理。
2. **前端 API 地址**：aiStudy 前端 baseURL 需确认并改为小林后端地址；两边后端路径是否带 `/api` 前缀需统一（aiStudy 是 `/api/**`，小林 request.js 也是 `/api` 前缀，但后端 context-path 需核对）。
3. **数据库合并**：aiStudy 数据在 `znxsgltest` 库、小林在 `course_note` 库。建议统一到 `course_note`，并导入 aiStudy 的种子数据（班级/教师/学期/教室）供测试。
4. **AI key 复用**：小林 `application.yml` 已有 DeepSeek key；aiStudy 的 `LlmService` 需要 `ai.llm.*` 配置，搬入时把两边的 AI 配置统一到小林配置里。
5. **删除课程/清空数据的清理脚本**（aiStudy 的 `test/delete_computer_class_courses.py` 等）仅测试用，不要带入生产逻辑。

---

## 七、关键文件路径速查

### aiStudy（源）

- 后端代码：`C:\Users\jay\Desktop\myBishe\aiStudy\backend\src\main\java\com\znxsgl\`
- 建表 SQL：`C:\Users\jay\Desktop\myBishe\aiStudy\init-db\znxsgltest.sql`
- 管理端前端：`C:\Users\jay\Desktop\myBishe\aiStudy\web-admin-react\`
- 课表导入模板：`C:\Users\jay\Desktop\myBishe\aiStudy\web-admin-react\public\template\schedule-import-template.xlsx`
- 测试数据：`C:\Users\jay\Desktop\myBishe\aiStudy\test\测试数据\计算机应用班课程表.xlsx`

### 小林（目标）

- 后端：`C:\Users\jay\Desktop\作业\小林毕业设计\course-note-backend\`
- 建表 SQL：`C:\Users\jay\Desktop\作业\小林毕业设计\course-note-backend\sql\init.sql`
- 小程序：`C:\Users\jay\Desktop\作业\小林毕业设计\course-note-miniapp\`
- 教师端 Vue3：`C:\Users\jay\Desktop\作业\小林毕业设计\course-note-teacher\`

### 需要重点阅读的"冲突相关"文件（开始前先读）

- 小林 `CourseServiceImpl.java`：决定 `personal_course` 改名的改动面
- aiStudy `SecurityConfig.java` / `JwtAuthFilter.java`：决定 Security 引入后小林现有接口的白名单
- aiStudy `QuizController.java`：决定 AI 出题适配范围
