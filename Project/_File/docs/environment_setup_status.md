# 环境配置状态（2026-09-26）

项目实际目录：`D:\YinYuanBaiShiTong\_Project\Project\_File`。同名的 `D:\YinYuanBaiShiTong_Project\Project_File` 目前为空，不要在那里启动项目。

## 已完成

- 已从电脑现有 IntelliJ JBR 复制完整 JDK 25 到 `D:\JAVA\jdk-25`；`java`、`javac` 均为 25.0.3，Maven 3.6.3 使用同一 JDK。项目 `.env` 与当前用户 `JAVA_HOME` 指向该目录；已将 JDK 的 `bin` 放到系统 PATH 首位，核对原有 PATH 条目均保留。重新打开终端后生效，项目启动脚本也会优先使用 `.env` 指定的 JDK。
- 已在项目根目录创建本地 `.env`。DeepSeek key 用于 `deepseek-flash`（目前对应 V4.1 Flash）；阿里云 key 用于 `text-embedding-v2`。两个接口均已使用极小请求验证，Embedding 返回 1536 维。不要将 `.env` 内容输出到聊天或提交到版本库。
- 《项目密码.docx》中的数据库密码和管理员密码已用于 `.env`。该文档给出的 JWT_SECRET 只有 12 字节，不满足当前 JWT 库的最小长度；`.env` 保留 64 字节随机 JWT_SECRET。管理员用户名为 `admin`，密码可在本地 `.env` 的 APP_ADMIN_PASSWORD 中查看。
- Windows 已重启，Windows Subsystem for Linux 和 VirtualMachinePlatform 两项功能均为 Enabled。已安装 WSL 2.7.14.0，内核版本 6.18.33.2-2，默认 WSL 版本为 2；系统 HypervisorPresent=True。
- Docker 官方安装包的 BITS 下载已完成（625204656 字节），已完成下载交付并核对 Authenticode 签名为 Valid，签名发布者为 Docker Inc。已按当前用户安装 Docker Desktop 4.92.0；Docker CLI 29.8.0、Compose v5.5.1 可用，`docker info` 已成功连接 Linux 引擎。当前安装目录为 `C:\Users\lenovo\AppData\Local\Programs\DockerDesktop`。
- 前端依赖已安装，已生成 `web/package-lock.json`。修复了 `AssistantView.vue` 中带连字符的对象键缺少引号的问题，`npm run build` 已成功完成。Node.js 24.19.0 和 npm 11.17.0 可用。
- 数据库已通过 `docker compose up -d db` 启动，容器 `yinyuan-postgres` 健康状态为 healthy。实际 PostgreSQL 版本为 16.15，pgvector 版本为 0.8.6。
- 后端依赖已下载，修复了 `AuthController.java` 登录查询中 `Map.of` 的泛型类型推断错误。使用 JDK 25、Maven 3.6.3 执行 `mvn -B -DskipTests package` 已成功，生成 `server/target/yinyuan-api-0.1.0.jar`。本次检查包含编译和打包，没有执行 JUnit 测试套件。
- 后端已通过 `spring-boot:run` 启动，Spring Boot 3.5.8 运行于 8080 端口；Hikari 数据库连接成功，Flyway V1 初始化迁移成功。
- 前端 Vite 开发服务已启动于 5173 端口。页面 HTTP 200；直接和通过前端代理访问 `/api/health` 均返回 ok。使用本地配置中的管理员账号，已验证登录成功、令牌签发成功、`/api/auth/me` 返回 ADMIN、`/api/admin/status` 可访问。登录口令和访问令牌未写入本文件。
- 已将项目默认对话模型由 `deepseek-v4-pro` 改为 `deepseek-flash`。未查看展示视频。

## 当前访问地址

- 前端：`http://localhost:5173`
- 后端健康接口：`http://localhost:8080/api/health`
- 管理员用户名：`admin`；口令沿用《项目密码.docx》中的配置，存放在本地 `.env`。

## 后续联调事项

环境已经具备构建、运行和管理员登录条件。知识库 PDF 尚未导入，完整问答、来源引用、采集、订阅和权限等功能尚未逐项验收；这些属于下一阶段的项目联调工作。

## 重启后启动

先启动 Docker Desktop，再进入项目实际目录执行 `docker compose up -d db`。随后分别在两个 PowerShell 终端运行 `./scripts/Start-Backend.ps1` 和 `./scripts/Start-Frontend.ps1`，打开 `http://localhost:5173`。

首次后端依赖下载的 Maven Central 直连速度较慢，本次构建使用临时 Maven settings 连接电脑既有代理 `127.0.0.1:7897`；未修改 Maven 全局 settings，已经下载的依赖保存在现有本地缓存中。临时文件为 `C:\Users\lenovo\AppData\Local\Temp\YinYuan-Environment\maven-existing-proxy.settings.xml`，仅在该本地代理正在运行时可用。

Java PATH 调整前的原始快照保存在 `C:\Users\lenovo\AppData\Local\Temp\YinYuan-Environment\Machine-Path.before-Java.txt`。原有 PATH 条目均已保留，仅提高了现有 JDK 25 的优先级。

参考：

- https://learn.microsoft.com/en-us/windows/wsl/install
- https://docs.docker.com/desktop/setup/install/windows-install/
- https://api-docs.deepseek.com/zh-cn/news/news260910/
- https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api/
