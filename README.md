# 课程笔记拍照识别与整理小程序（CourseNoteAI）

> English Name: **CourseNoteAI** — Photo-OCR Course Notes with AI Knowledge Base RAG Q&A

## 项目概述

基于 Spring Boot + 微信小程序的课程笔记智能管理系统，支持拍照OCR识别、AI整理归纳，并以**课程**为维度建立 **AI知识库**。

## 核心创新点

### 以课程为入口的AI整理归纳
- **课表入口**：以课程表为核心入口，进入每个课段查看所有资料
- **多维归类**：拍照、笔记、导入材料自动归类到课段内
- **AI知识库**：每门课程拥有独立AI知识库，课段资料自动纳入
- **RAG问答**：基于课程知识库进行智能问答

## 技术栈

### 后端
- Java 17 + Spring Boot 3.2
- MyBatis-Plus + MySQL
- JWT 身份认证
- MinIO 文件存储
- WebSocket 实时通信
- LLM (DeepSeek / 腾讯混元)
- 腾讯云 OCR (文字识别)

### 前端
- 微信小程序原生框架
- WXSS + Flexbox 布局

## 项目结构

```
course-note-backend/          # Spring Boot 后端
├── src/main/java/com/coursenote/
│   ├── common/               # 公共类(Result,异常处理)
│   ├── config/               # 配置类(JWT,WebSocket,跨域)
│   ├── controller/           # 控制器
│   ├── dto/                  # 数据传输对象
│   ├── entity/               # 实体类
│   ├── mapper/               # MyBatis Mapper
│   ├── service/              # 服务接口
│   │   └── impl/             # 服务实现
│   └── ws/                   # WebSocket
├── src/main/resources/
│   ├── application.yml       # 主配置
│   └── mapper/               # XML Mapper(可选)
└── sql/init.sql              # 数据库初始化脚本

course-note-miniapp/          # 微信小程序前端
├── pages/
│   ├── index/                # 课程表首页
│   ├── course/               # 课程管理
│   ├── session/              # 课段详情
│   ├── photo/                # 拍照OCR
│   ├── note/                 # 笔记编辑
│   ├── knowledge/            # AI知识库
│   ├── profile/              # 个人中心
│   └── material-detail/      # 资料详情
└── utils/
    ├── api.js                # API封装
    └── util.js               # 工具函数
```

## 数据库表

| 表名 | 说明 |
|------|------|
| user | 用户表 |
| course | 课程表（含星期、时间、周次等） |
| course_session | 课段表（每堂课实例） |
| material | 资料表（拍照/笔记/导入统一存储） |
| knowledge_base | AI知识库表 |
| knowledge_document | 知识库文档表 |
| ai_chat_record | AI对话记录表 |

## API接口

### 用户
- `POST /api/user/login` - 微信登录
- `GET /api/user/info` - 获取用户信息

### 课程
- `GET /api/courses/schedule` - 获取课表(按星期分组)
- `POST /api/courses` - 添加课程
- `PUT /api/courses/{id}` - 更新课程
- `DELETE /api/courses/{id}` - 删除课程

### 课段
- `GET /api/sessions/date?date=xxx` - 某天的课段
- `POST /api/sessions/start/{courseId}` - 开始上课
- `POST /api/sessions/end/{sessionId}` - 下课
- `GET /api/sessions/{id}/detail` - 课段详情

### 资料
- `POST /api/materials/photo` - 上传照片
- `POST /api/materials/note` - 创建笔记
- `POST /api/materials/import` - 导入资料

### AI
- `POST /api/ai/summarize` - AI整理归纳课段
- `POST /api/ai/chat` - 知识库问答
- `GET /api/ai/knowledge/{courseId}` - 课程知识库详情

### WebSocket
- `ws://localhost:8080/api/ws/transcribe/{token}/{sessionId}` - 实时转写

## 部署说明

### 1. 数据库
```sql
source sql/init.sql
```

### 2. 后端配置
编辑 `application.yml`，配置以下关键项：
- 微信小程序 app-id / app-secret
- MySQL 数据库连接
- MinIO 或文件存储路径
- LLM API Key（DeepSeek / 腾讯混元）
- 腾讯云 ASR / OCR 密钥（可选）

> **API Key 等敏感信息**建议写在 `course-note-backend/application-local.yml`（已被 `.gitignore` 排除），
> 或通过环境变量 `DEEPSEEK_API_KEY`、`ARK_API_KEY` 注入，不要提交到仓库。

### 3. 启动后端
```bash
cd course-note-backend
mvn spring-boot:run
```

### 4. 小程序配置
在微信开发者工具中打开 `course-note-miniapp` 目录
- 修改 `utils/api.js` 中的 `BASE_URL` 为后端地址
- 在 `app.json` 中配置小程序 AppID

## 业务流程

```
用户添加课程 → 课程表展示
    ↓
上课签到(开始课段) → 课段详情页
    ↓
拍照📷  |  笔记📝  |  导入📎
    ↓              ↓
    OCR识别
    ↓
所有资料自动归入课段
    ↓
下课后AI自动整理归纳
    ↓
资料自动导入"课程AI知识库"
    ↓
支持基于知识库的智能问答
```
