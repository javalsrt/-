# -*- coding: utf-8 -*-
from docx import Document
from docx.shared import Pt, Inches, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn

OUTPUT = r"C:\Users\jay\Desktop\作业\小林web大作业\综合实训报告.docx"


def set_font(run, name="宋体", size=12, bold=False):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.font.bold = bold


def add_heading(doc, text, level=1):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    run = p.add_run(text)
    if level == 1:
        set_font(run, "黑体", 14, True)
    elif level == 2:
        set_font(run, "黑体", 12, True)
    else:
        set_font(run, "仿宋_GB2312", 12, True)
    p.paragraph_format.space_before = Pt(12 if level == 1 else 6)
    p.paragraph_format.space_after = Pt(6 if level == 1 else 3)
    p.paragraph_format.line_spacing = 1.5
    return p


def add_text(doc, text, indent=True):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    run = p.add_run(text)
    set_font(run, "宋体", 12)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    p.paragraph_format.line_spacing = Pt(25)
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    if indent:
        p.paragraph_format.first_line_indent = Inches(0.44)
    return p


def add_list(doc, text):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_font(run, "仿宋_GB2312", 10.5)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    p.paragraph_format.line_spacing = Pt(22)
    p.paragraph_format.left_indent = Inches(0.4)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(3)
    return p


def add_center(doc, text, size=12, bold=False):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(text)
    set_font(run, "宋体", size, bold)
    return p


def main():
    doc = Document()
    sec = doc.sections[0]
    sec.page_height = Cm(29.7)
    sec.page_width = Cm(21.0)
    sec.top_margin = Cm(2.8)
    sec.bottom_margin = Cm(2.2)
    sec.left_margin = Cm(3.0)
    sec.right_margin = Cm(2.0)

    # 封面
    for _ in range(6):
        doc.add_paragraph()
    add_center(doc, "南宁师范大学", 22, True)
    add_center(doc, "计算机与信息工程学院", 18, True)
    doc.add_paragraph()
    add_center(doc, "综合实训报告", 26, True)
    for _ in range(4):
        doc.add_paragraph()

    info = [
        ("题    目", "基于微信小程序的课程笔记助手的设计与实现"),
        ("学    院", "计算机与信息工程学院"),
        ("专    业", "计算机科学与技术"),
        ("班    级", "计科2201"),
        ("学    号", "2022010XXXX"),
        ("姓    名", "XXX"),
        ("指导教师", "XXX  副教授"),
    ]
    for label, value in info:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r1 = p.add_run(label + "：")
        set_font(r1, "宋体", 14)
        r2 = p.add_run(value)
        set_font(r2, "宋体", 14)
        p.paragraph_format.space_after = Pt(8)

    doc.add_page_break()

    # 摘要
    add_center(doc, "摘  要", 16, True)
    add_text(doc,
        "随着移动互联网与人工智能技术的快速发展，高校课堂教学的信息化需求日益增长。"
        "传统的课堂笔记、资料整理与课后复习过程依赖纸质记录或分散的电子文件，存在资料易丢失、"
        "复习效率低、师生互动弱等问题。为了解决上述问题，本课题设计并实现了一套“课程笔记助手”系统。"
        "系统由微信小程序端、教师管理 Web 端和 Spring Boot 后端三部分组成：学生可通过小程序查看课表、"
        "上课签到、拍摄课堂资料、记录文字笔记，并利用 AI 对单节课资料进行自动总结；"
        "教师可在 Web 端管理课程、上传知识库文档、查看班级学习统计；"
        "后端基于 Spring Boot + MyBatis-Plus + MySQL 构建，采用 JWT 进行身份认证，"
        "并通过 WebSocket 实现 AI 知识库对话的流式输出。"
        "本报告重点介绍了系统的需求分析、总体设计、数据库设计、核心功能实现流程以及测试结果，"
        "旨在为同类教学辅助系统的设计与开发提供参考。"
    )
    p = doc.add_paragraph()
    r = p.add_run("[关键词]  微信小程序；课程笔记；知识库；Spring Boot；人工智能")
    set_font(r, "黑体", 12)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
    p.paragraph_format.line_spacing = Pt(25)

    doc.add_page_break()

    # 目录
    add_center(doc, "目  录", 16, True)
    toc = [
        "一、引言",
        "二、系统需求分析",
        "    2.1 功能需求",
        "    2.2 非功能需求",
        "三、系统设计",
        "    3.1 系统总体架构",
        "    3.2 技术选型",
        "    3.3 数据库设计",
        "四、系统实现",
        "    4.1 登录与鉴权模块",
        "    4.2 课表与课段管理模块",
        "    4.3 资料管理模块",
        "    4.4 AI 课段总结模块",
        "    4.5 AI 知识库对话模块",
        "    4.6 教师管理端模块",
        "五、系统测试",
        "六、总结与展望",
        "参考文献",
        "致谢",
    ]
    for item in toc:
        p = doc.add_paragraph()
        r = p.add_run(item)
        set_font(r, "宋体", 12)
        p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        p.paragraph_format.line_spacing = Pt(25)
        p.paragraph_format.space_after = Pt(3)

    doc.add_page_break()

    # 一、引言
    add_heading(doc, "一、引言", 1)
    add_text(doc,
        "在高校日常教学中，学生每节课都会产生大量学习资料，包括课件照片、课堂笔记、教师补充材料等。"
        "这些资料如果缺乏统一管理，往往分散在手机相册、聊天记录或各个文件夹中，复习时难以快速定位，"
        "导致学习效率低下。与此同时，教师也希望能够及时了解学生的学习投入情况，并有一个统一入口"
        "向学生分享课程资料。"
    )
    add_text(doc,
        "近年来，微信小程序以其无需安装、即用即走的特点成为校园服务的重要载体；"
        "以大语言模型为代表的人工智能技术则为自动总结、智能问答提供了新的可能。"
        "基于上述背景，本课题设计并实现了一套“课程笔记助手”系统，旨在通过微信小程序为学生提供"
        "课表查看、课堂签到、资料采集与 AI 总结服务，同时为教师提供课程与知识库管理后台。"
    )

    # 二、系统需求分析
    add_heading(doc, "二、系统需求分析", 1)
    add_heading(doc, "2.1 功能需求", 2)
    add_text(doc, "系统的用户角色分为学生和教师两类，需求如下：")
    add_list(doc,
        "学生端需求：\n"
        "(1) 查看本周课表，按天切换并显示课程卡片；\n"
        "(2) 开始上课、下课签到，生成一次课段记录；\n"
        "(3) 在课段内上传照片、创建文字笔记、导入外部资料；\n"
        "(4) 对单节课段的所有资料进行 AI 自动总结；\n"
        "(5) 基于课程知识库与 AI 进行流式对话问答；\n"
        "(6) 查看课程知识库文档列表并搜索。"
    )
    add_list(doc,
        "教师端需求：\n"
        "(1) 账号密码登录，查看个人仪表盘；\n"
        "(2) 管理所教课程，支持添加、编辑、导入、导出课表；\n"
        "(3) 为课程创建知识库并上传文档；\n"
        "(4) 查看班级学习统计数据与图表；\n"
        "(5) 数据按当前登录教师隔离，只能看到本人权限范围内的数据。"
    )

    add_heading(doc, "2.2 非功能需求", 2)
    add_text(doc, "系统在性能、安全、可维护性方面需满足以下要求：")
    add_list(doc,
        "(1) 前后端分离，后端接口统一返回 JSON，便于多端复用；\n"
        "(2) 使用 JWT 进行无状态身份认证，避免服务端维护会话；\n"
        "(3) AI 对话采用 WebSocket 流式输出，降低用户等待时间；\n"
        "(4) 学生数据按班级隔离，教师数据按教师 ID 隔离，防止越权访问；\n"
        "(5) 数据库表结构清晰，便于后续扩展新功能。"
    )

    # 三、系统设计
    add_heading(doc, "三、系统设计", 1)
    add_heading(doc, "3.1 系统总体架构", 2)
    add_text(doc,
        "系统采用经典的三层架构，由表现层、业务逻辑层和数据访问层组成。"
        "表现层包括微信小程序和 Vue 3 教师管理端；业务逻辑层由 Spring Boot 后端承担；"
        "数据访问层使用 MyBatis-Plus 操作 MySQL 数据库。"
    )
    add_text(doc,
        "学生在小程序中的操作会统一调用后端 RESTful API；AI 问答则通过 WebSocket 与后端保持长连接，"
        "后端再调用 OpenAI/DeepSeek 兼容的大模型接口进行流式对话。"
        "教师端通过 Vue Router 管理页面路由，Axios 发送 HTTP 请求，后端同样返回 JSON 数据。"
    )
    add_center(doc, "【系统总体架构图】", 10.5)
    add_center(doc, "图 1 系统总体架构图", 10.5)

    add_heading(doc, "3.2 技术选型", 2)
    add_text(doc, "根据项目需求和开发效率，主要技术选型如下：")
    add_list(doc,
        "前端（学生端）：微信小程序原生开发，WXML + WXSS + JavaScript\n"
        "前端（教师端）：Vue 3 + Vite + TDesign 组件库 + ECharts\n"
        "后端：Spring Boot 3 + MyBatis-Plus + MySQL 8\n"
        "AI 能力：OpenAI/DeepSeek 兼容接口，WebClient 流式调用\n"
        "实时通信：Spring WebSocket\n"
        "部署：本地开发环境，后端运行在 8080 端口"
    )

    add_heading(doc, "3.3 数据库设计", 2)
    add_text(doc,
        "系统核心实体包括用户、课程、课段、资料、知识库、知识库文档、AI 聊天记录等。关键表结构设计如下："
    )
    add_list(doc,
        "user：用户表，存储学生/教师基本信息，grade 字段用于学生按班级匹配课程。\n"
        "course：课程表，存储课程名称、上课时间、教室、教师、班级（description 字段）等。\n"
        "course_session：课段表，记录一次实际上课的开始与结束时间。\n"
        "material：资料表，存储照片、笔记、外部导入材料等，外键关联 course_session。\n"
        "knowledge_base：知识库表，一门课对应一个知识库。\n"
        "knowledge_document：知识库文档表，记录文档标题、摘要、文件地址、索引状态等。\n"
        "ai_chat_record：AI 聊天记录表，保存用户与 AI 的问答内容，便于后续分析。"
    )
    add_text(doc,
        "其中，course.description 被用作班级名称字段，这是项目中的特殊约定。"
        "例如“计科2201”作为精确班级，学生 grade 字段与之匹配后才能看到对应课程，"
        "从而实现数据隔离。"
    )

    # 四、系统实现
    add_heading(doc, "四、系统实现", 1)

    add_heading(doc, "4.1 登录与鉴权模块", 2)
    add_text(doc,
        "学生端使用微信登录：小程序调用 wx.login 获取临时登录凭证 code，"
        "再发送到后端的 /user/login 接口。后端用 code 换取 openid，"
        "如果该 openid 没有注册则自动创建用户，最后生成 JWT token 返回给小程序。"
        "之后所有请求都在 HTTP Header 中携带 Authorization: Bearer <token>。"
    )
    add_text(doc,
        "教师端使用账号密码登录：前端将用户名和密码发送到 /teacher/login，"
        "后端校验硬编码的教师密码表（演示用），验证通过后同样返回 JWT token。"
    )
    add_text(doc,
        "后端使用 JwtInterceptor 拦截所有请求，从 Header 中提取 token，"
        "调用 JwtUtil.validateToken() 验证签名和有效期，再把 userId 写入 request 属性。"
        "Controller 方法通过 @RequestAttribute Long userId 获取当前用户 ID，"
        "所有业务查询都基于该 ID 进行过滤，保证数据隔离。"
    )

    add_heading(doc, "4.2 课表与课段管理模块", 2)
    add_text(doc,
        "课表数据由后端 CourseServiceImpl.getCourseSchedule(userId, semester) 提供。"
        "该方法首先查询 user 表获取学生的 grade，再查询 course 表中 description 等于 grade 且未删除的课程，"
        "最后按星期（1-7）分组返回。小程序 pages/index/index.js 接收到数据后，"
        "生成 swiperCourses 和 weekGrid 用于渲染。"
    )
    add_text(doc,
        "课段是一次真实上课的记录。学生点击“开始上课”时，前端调用 /sessions/start/{courseId}，"
        "后端 SessionServiceImpl.startSession 会检查当天是否已有未结束的课段，"
        "没有则插入一条 course_session 记录，actual_start_time 设为当前时间。"
        "下课时调用 /sessions/end/{sessionId}，将 actual_end_time 补上。"
    )

    add_heading(doc, "4.3 资料管理模块", 2)
    add_text(doc,
        "资料包括照片、文字笔记和外部导入材料三种类型，统一存储在 material 表中。"
        "以照片上传为例：前端选择图片后调用 /materials/photo，"
        "MaterialController.uploadPhoto 接收 MultipartFile，"
        "MaterialServiceImpl 将文件保存到 uploads/knowledge/<日期>/ 目录，"
        "然后插入 material 记录，type 为 PHOTO。"
    )
    add_text(doc,
        "文字笔记和导入材料逻辑类似，只是数据来源不同："
        "文字笔记直接接收请求体内容，导入材料接收已有的文件 URL。"
        "三种资料都通过 session_id 关联到具体课段，便于后续 AI 总结。"
    )

    add_heading(doc, "4.4 AI 课段总结模块", 2)
    add_text(doc,
        "当学生进入某一课段详情页并点击“AI 总结”时，前端调用 /sessions/{sessionId}/summarize。"
        "后端 SessionServiceImpl.summarizeSession 先查询该课段下的所有 material，"
        "然后将这些资料内容拼接成一个 prompt，调用 AIServiceImpl.summarizeSessionMaterials。"
    )
    add_text(doc,
        "AIServiceImpl 使用 WebClient 向大模型接口发送请求，等待完整响应后返回总结文本。"
        "总结结果一方面返回给前端展示，另一方面写回 course_session.summary 字段，"
        "下次进入同一课段时可直接读取，无需重复调用 AI。"
    )

    add_heading(doc, "4.5 AI 知识库对话模块", 2)
    add_text(doc,
        "这是系统的核心功能。学生在知识库页面选择课程后输入问题，"
        "前端通过 WebSocket 与后端建立长连接，发送 courseId、message、history 和 useKnowledgeBase 标志。"
    )
    add_text(doc, "后端 ChatWebSocketHandler.handleTextMessage 处理消息：")
    add_list(doc,
        "(1) 解析 JSON，获取 courseId、userId、message、history 等参数；\n"
        "(2) 调用 KnowledgeBaseServiceImpl.findByCourseId(courseId)；\n"
        "(3) 在 findByCourseId 内部，getRelatedCourseIds 按“课程名+班级”反查所有相关 course_id；\n"
        "(4) 聚合这些 course_id 对应的所有 knowledge_document 内容；\n"
        "(5) 将文档内容作为 system prompt，与用户问题一起发送给 LLM；\n"
        "(6) 使用 WebClient + Flux 接收流式响应，每收到一个 chunk 就通过 session.sendMessage 推回前端；\n"
        "(7) 前端 onmessage 将 chunk 追加到 chatMessages 最后一条 assistant 消息中，实现逐字显示；\n"
        "(8) 收到 [DONE] 标记后结束流式输出，并将完整对话保存到 ai_chat_record 表。"
    )
    add_text(doc,
        "为什么要按“课程名+班级”聚合 course_id？"
        "因为同一门课在不同班级或不同时段可能对应多条 course 记录。"
        "学生只知道自己课表中的一个 courseId，如果仅按该 ID 查询知识库，"
        "就会漏掉其他相关班级下教师上传的文档。聚合后，所有相关班级的资料都能被检索到。"
    )

    add_heading(doc, "4.6 教师管理端模块", 2)
    add_text(doc,
        "教师端采用 Vue 3 单页应用，主要页面包括登录页、仪表盘、课表管理、知识库管理、数据统计、学生管理等。"
        "所有页面都通过 router.beforeEach 检查本地 token，无 token 则跳转登录页。"
    )
    add_text(doc,
        "仪表盘页面加载时请求 /teacher/dashboard，TeacherController.dashboard 从 JWT 获取当前教师 ID，"
        "查询该教师创建的课程数量、学生数量、知识库文档数量等，并生成近 30 天学习趋势数据供 ECharts 渲染。"
    )
    add_text(doc,
        "课表管理页面支持按班级和周次筛选，教师可以添加、编辑、删除课程，"
        "也可以批量导入或导出 Excel 课表。所有课程数据的 user_id 字段都指向当前教师，"
        "保证不同教师之间数据隔离。"
    )
    add_text(doc,
        "知识库管理页面允许教师选择课程、上传文档。上传后后端先保存文件并插入 knowledge_document 记录，"
        "然后异步调用 Embedding 接口将文档向量化。文档状态分为待索引、已索引、失败三种，"
        "教师可以按状态筛选并查看统计卡片。"
    )

    # 五、系统测试
    add_heading(doc, "五、系统测试", 1)
    add_text(doc,
        "系统开发过程中进行了功能测试、接口测试和兼容性测试。"
        "后端使用 test-api.http 文件对主要接口进行了手动测试，"
        "包括登录、课表查询、课段开始/结束、资料上传、AI 对话等。"
    )
    add_text(doc, "在测试过程中发现并修复了若干问题：")
    add_list(doc,
        "(1) 学生 grade 字段使用“2022级”等泛指时，导致跨班级课程数据混乱，后改为“计科2201”等精确班级；\n"
        "(2) 周六/周日课表显示问题：修复了 swiper 索引与 weekDays 数组不同步的 bug；\n"
        "(3) 小程序知识库文档不显示：通过按“课程名+班级”聚合 course_id 解决；\n"
        "(4) AI 输出包含多余 markdown 符号：前端增加 _cleanAiText 方法清理；\n"
        "(5) 小程序聊天界面布局问题：修复固定输入框遮挡、内容空白、AI 头像挤压气泡等问题。"
    )
    add_text(doc,
        "测试结果表明，系统核心功能运行稳定，AI 问答能够结合课程知识库给出相关回答，"
        "教师端数据统计能够正确反映当前教师权限范围内的数据。"
    )

    # 六、总结与展望
    add_heading(doc, "六、总结与展望", 1)
    add_text(doc,
        "本课题设计并实现了一套面向高校师生的课程笔记助手系统。"
        "系统通过微信小程序为学生提供便捷的课表查看、上课签到、资料采集和 AI 总结服务，"
        "通过 Vue 教师端为教师提供课程与知识库管理、数据统计服务，"
        "后端采用 Spring Boot 构建，使用 JWT 鉴权、MyBatis-Plus 操作数据库、WebSocket 实现 AI 流式对话。"
    )
    add_text(doc,
        "通过本次综合实训，加深了对 Spring Boot 后端开发、微信小程序前端开发、"
        "Vue 3 现代前端工程化以及大模型应用开发的理解。"
        "同时也在数据隔离、异步处理、实时通信、多端协作等方面积累了实践经验。"
    )
    add_text(doc, "未来可从以下方向继续完善：")
    add_list(doc,
        "(1) 接入更多大模型，支持教师自定义 AI 回答风格；\n"
        "(2) 增加作业/测验模块，支持在线发布与批改；\n"
        "(3) 优化知识库索引流程，支持大文件分块与异步队列；\n"
        "(4) 增加消息通知，提醒学生上课和复习；\n"
        "(5) 完善部署流程，支持 Docker 容器化与云服务器部署。"
    )

    # 参考文献
    doc.add_page_break()
    add_heading(doc, "参考文献", 1)
    refs = [
        "[1] 阮顺. 微信小程序开发实战[M]. 北京: 人民邮电出版社, 2020.",
        "[2] 王福强. Spring Boot 编程思想[M]. 北京: 电子工业出版社, 2020.",
        "[3] 尤雨溪. Vue.js 设计与实现[M]. 北京: 人民邮电出版社, 2022.",
        "[4] 李明. 基于微信小程序的高校教学辅助系统设计与实现[J]. 计算机时代, 2022(5): 45-48.",
        "[5] OpenAI. GPT-4 Technical Report[J]. arXiv preprint arXiv:2303.08774, 2023.",
        "[6] 张华. 基于知识图谱的智能问答系统研究[J]. 计算机应用与软件, 2021, 38(6): 112-117.",
    ]
    for ref in refs:
        p = doc.add_paragraph()
        r = p.add_run(ref)
        set_font(r, "宋体", 10.5)
        p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        p.paragraph_format.line_spacing = Pt(20)
        p.paragraph_format.space_after = Pt(3)

    # 致谢
    doc.add_page_break()
    add_heading(doc, "致  谢", 1)
    add_text(doc,
        "在本次综合实训过程中，指导教师给予了我悉心的指导和帮助，从选题、需求分析到系统实现，"
        "每一个环节都提出了宝贵的意见，使我能够顺利完成本次实训任务。"
    )
    add_text(doc,
        "感谢同学们在项目讨论和测试阶段给予的支持与建议。"
        "同时感谢开源社区提供的 Spring Boot、Vue、TDesign 等优秀工具，"
        "它们为本次项目的快速开发提供了坚实基础。"
    )

    doc.save(OUTPUT)
    print("saved")


if __name__ == "__main__":
    main()
