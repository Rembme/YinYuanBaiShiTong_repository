# 印苑百事通知识库

面向北京印刷学院师生的校园通知检索与智能问答项目。当前优先补齐核心功能并进行网页验收，视觉优化安排在功能稳定之后。

## 目录

- `Project/_File/`：应用源码、数据库配置和运行说明，详见其中的 `README.md`。
- `Project/_File/data/knowledge/knowledge_base.pdf`：应用使用的校内制度资料。
- `outlines/` 与 `outline_supplement.md`：项目范围和工作计划。

## 本地运行

先阅读 `Project/_File/README.md`，按说明准备 JDK、Maven、Node.js 和 Docker。将 `Project/_File/.env.example` 复制为同目录 `.env`，填入自己的数据库、JWT 和模型凭据，再按步骤启动数据库、后端和前端。

本仓库不会保存本地 `.env`。Docker Compose 要求显式提供 `DB_PASSWORD`，应用启动要求显式提供 `JWT_SECRET`。