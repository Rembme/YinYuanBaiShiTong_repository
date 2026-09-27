# 印苑百事通

面向北京印刷学院师生的校园通知检索与智能问答应用。首版包含官方网页采集、制度 PDF 导入、带来源回答、多轮会话、专区订阅、每周简报和管理员工作台。

## 项目结构

- `web/`：Vue 3、Element Plus、Vite 前端。
- `server/`：Spring Boot API、权限、采集、RAG 和定时任务。
- `data/knowledge/knowledge_base.pdf`：原始 377 页制度 PDF 的项目副本。
- `docs/`：产品需求、架构说明、实施大纲及原始要求文档。
- `docker-compose.yml`：PostgreSQL 16 + pgvector 本地数据库。
- `scripts/`：读取本地 `.env` 并启动前后端的 PowerShell 脚本。

## 环境要求

- JDK 17 或更高版本。当前电脑已配置完整 JDK 25：`JAVA_HOME=D:\JAVA\jdk-25`，系统 PATH 优先使用其 `bin`，项目启动脚本也会读取 `.env` 中的 `JAVA_HOME`。安装前已打开的终端需要重新打开，才能读取更新后的环境变量。
- Maven 3.6.3 或更高版本。
- Node.js 20.19 或更高版本（电脑当前 Node.js 24 可用）。
- Docker Desktop，或自行安装带 pgvector 的 PostgreSQL 16。

Spring Boot 3.4 与 Java 25 的组合超出 Spring Boot 3.4 官方兼容范围。本项目选用 Spring Boot 3.5.8、Spring AI Alibaba 1.1.2.2 和 Java 17 编译基线；Java 25 可作为运行 JDK。该组合与 Spring AI Alibaba 1.1.2.x 的 Boot 3.5 系列基线相符。

## 本地启动

1. 将 `.env.example` 复制为项目根目录的 `.env`，设置数据库密码、至少 32 字节的随机 `JWT_SECRET`，并设置自己的首个管理员账号和密码。
2. 启动数据库：`docker compose up -d db`。
3. 新开一个 PowerShell，在项目根目录运行：`./scripts/Start-Backend.ps1`。
4. 再新开一个 PowerShell，在项目根目录运行：`./scripts/Start-Frontend.ps1`。
5. 打开 `http://localhost:5173`。浏览器开发请求会由 Vite 转发到本地后端。

`.env` 不会提交到仓库。不要将 API key 放进前端配置或日志。

## 模型配置

- DeepSeek 对话：设置 `DEEPSEEK_API_KEY`，模型标识 `deepseek-flash`，当前对应 DeepSeek V4.1 Flash。
- 知识库向量：设置独立的 `DASHSCOPE_API_KEY`，模型 `text-embedding-v2`，1536 维。DeepSeek key 不可代替 DashScope 凭据。
- 不配置模型凭据时，系统使用本地哈希向量和本地检索回答。该模式用于开发降级，不等同于模型语义 Embedding 或生成式回答。
- API key 仅保存在本地 `.env`，不放入前端、文档或版本库。

## 初次使用

- 本地注册创建的账号是学生角色。
- 管理员首次启动时通过 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD` 创建。
- 进入管理工作台，运行采集任务；也可上传 `data/knowledge/knowledge_base.pdf` 建立检索索引。
- 四个专区的名称与来源映射仍需项目方确认。第三个专区默认指校园新闻网；第四个专区可由管理员添加更多经确认的 `bigc.edu.cn` 页面。
- 定时采集默认关闭；需要时在 .env 设置 CRAWLER_ENABLED=true。周报每周一 08:00（北京时间）生成；只有用户主动开启邮件周报并填写邮箱后，系统才会发送，且还需配置 MAIL_HOST、MAIL_USERNAME、MAIL_PASSWORD 和 MAIL_FROM。

## 已知限制和下一步

- 本地账号系统适合开发预览；正式校园部署前要接入学校统一身份认证。
- 网站 HTML 结构可能变化，需通过管理工作台查看采集结果并维护规则。
- 制度 PDF 的现行有效性尚未逐条核验；应用必须展示页码和版本线索，不能将旧文件自动视为现行政策。
- 首版采用时效关键词和资料类型规则分流，后续可以在稳定检索工具基础上升级到 Agent 自主编排。
- 本阶段未查看展示视频；两份 API key 文本已用于本地 `.env`，没有写入前端或版本库。
