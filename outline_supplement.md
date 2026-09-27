# 大纲_补充｜印苑百事通当前待办

更新日期：2026-09-25  
目标：先把本机开发环境和必需配置准备好，再让 Codex 在 D:\YinYuanBaiShiTong\_Project\Project\_File 完成构建、联调和验收。本文件补充已有的“大纲”，以当前实际进度为准。

## 一、当前状态与执行顺序

- [x] 项目代码与基础目录已建立在 D:\YinYuanBaiShiTong\_Project\Project\_File。
- [x] 已有环境配置样例、数据库编排文件、前后端启动脚本和知识库 PDF 副本。
- [ ] 尚未完成构建与实际运行验证；本机目前缺少符合要求的 JDK 和可用的 Docker 环境。
- [ ] 尚未创建项目根目录下的本地 .env 配置。
- [ ] 尚未导入知识库 PDF，也未进行完整功能验收。

建议顺序：安装 JDK → 安装并启动 Docker Desktop／WSL 2 → 填写本地 .env → 启动数据库和项目 → 交由 Codex 联调、导入资料、验收 → 再决定是否接入付费模型与邮件服务。

## 二、你现在需要完成的必需事项

### 2.1 安装完整 JDK，并让系统使用新版本

- [ ] 安装完整的 JDK 21；JDK 17 也可满足本项目的最低要求。安装的是 JDK，不是只有运行功能的 JRE。
- [ ] 将 JAVA_HOME 指向新 JDK 的安装目录，并把新 JDK 的 bin 放在 PATH 中有效的位置。
- [ ] 处理旧 Java 8 的优先级：当前 java 指向 Oracle 的 Java 8 转发路径，javac 不可用，Maven 也仍使用 Java 8。安装后重新打开 PowerShell 核对以下结果。

~~~powershell
java -version
javac -version
mvn -v
~~~

完成标准：三个命令均可运行；java、javac 和 Maven 使用同一个 JDK 17 或 21。现有 Maven 3.6.3 可继续使用，无须仅为此项目重装 Maven。

### 2.2 安装 Docker Desktop，确认 WSL 2 可用

- [ ] 按 Docker 官方 Windows 安装说明完成 Docker Desktop 所需的 WSL 2 设置。
- [ ] 启动 Docker Desktop，确认使用 Linux 容器。
- [ ] 重新打开 PowerShell，检查：

~~~powershell
wsl --version
docker compose version
docker info
~~~

完成标准：Docker Desktop 正在运行；Docker Compose 可用；docker info 可以连接到 Docker 引擎。项目的数据库由 Compose 启动，无须另外安装独立的 psql 命令行工具。

### 2.3 创建本地配置文件

- [ ] 在项目根目录，将 .env.example 复制为 .env。
- [ ] 填写数据库密码 DB_PASSWORD；数据库容器与后端会使用同一份配置。
- [ ] 填写足够长且随机的 JWT_SECRET，以及后台管理员用户名和密码。
- [ ] 保存 .env 在本机，不将真实密码或 API key 写入聊天、大纲或版本库。

~~~powershell
Set-Location 'D:\YinYuanBaiShiTong\_Project\Project\_File'
Copy-Item -LiteralPath '.env.example' -Destination '.env'
~~~

初次启动可先留空 DEEPSEEK_API_KEY、DASHSCOPE_API_KEY 和邮件配置。这样可以先验证数据库、界面、知识库导入与本地检索的基础流程；模型回答、语义向量质量和邮件发送仍需后续配置与验证。

## 三、安装后交给 Codex 完成的联调工作

以下属于项目开发与验证工作，你完成第二节后再让 Codex继续执行：

- [ ] 在项目目录启动数据库：docker compose up -d db。
- [ ] 分别运行 scripts\Start-Backend.ps1 和 scripts\Start-Frontend.ps1；首次前端启动脚本会安装 npm 依赖。
- [ ] 处理构建或启动错误，确认数据库建表、后端服务和前端页面均正常。
- [ ] 打开 http://localhost:5173，使用本地管理员账号登录。
- [ ] 导入 data/knowledge/knowledge_base.pdf，检查文本提取、切分、索引、检索结果和引用来源。
- [ ] 根据项目要求文档逐项验证普通问答、权限、后台管理、资料更新或爬取、异常提示等功能；将未实现项与缺陷记录为具体待办。
- [ ] 在接入真实模型后，再核对模型名称、接口地址、请求格式、响应处理和费用控制。现有配置只是待验证的配置项，不能把文件中写有模型名视为接口已经跑通。
- [ ] 完成一次从启动到问答的端到端验收，并给出运行步骤和仍存在的问题。

当前已装 Node.js 24.19.0 和 npm 11.17.0，一般无需重复安装。此前因为 JDK 和 Docker 环境不足，项目尚未完成构建或运行验收。

## 四、DeepSeek 与 Embedding 的账号决策

### 4.1 先确认密钥来源，再接入

- [ ] 只确认 DeepSeek V4 Pro API key 由哪家平台签发、对应的接口基础地址和计费账号；不要在聊天中发送密钥正文。
- [ ] 到真正进行模型接口联调时，再让 Codex 查看“API key”文档，在本地填写 .env 并测试一次最小请求。
- [ ] 核实该平台是否提供 Embedding 接口，以及向量维度、模型名、价格和使用限制。

### 4.2 当前为什么有单独的 DashScope 配置

- 当前项目的语义检索方案使用 text-embedding-v2，输出 1536 维向量；数据库中的向量列也按 1536 维设计。
- DeepSeek V4 Pro 配置用于生成回答。仅有生成模型的 API key，不能自动调用另一家平台的 Embedding 接口。密钥能否复用取决于实际签发平台及其支持的服务，不能仅凭“DeepSeek V4 Pro”这个名称判断。
- [ ] 如果希望使用当前方案的真实语义向量，准备支持 text-embedding-v2 的阿里云百炼／DashScope 凭据，并核对相应地域和接口地址；填写 DASHSCOPE_API_KEY 后重新导入 PDF，使入库向量与查询向量使用同一种模型。
- [ ] 如果希望只用一个供应商，先让 Codex核对该供应商是否真的提供兼容的 Embedding。若没有，选择本地 Embedding 模型或调整检索方案；涉及向量维度变化时，同步修改数据库与索引并重建知识库。
- [ ] 预算尚未确定时，可先使用当前的本地回退检索跑通流程，再决定是否开通付费 Embedding。回退检索的语义效果可能弱于专用 Embedding，需要用实际知识库问答评估。

## 五、可在基础流程跑通后再决定

- [ ] 邮件：若需要订阅、验证码或定期发送，再准备 SMTP 账号并填写 MAIL_HOST、MAIL_PORT、MAIL_USERNAME、MAIL_PASSWORD、MAIL_FROM。
- [ ] 资料维护：确认知识库 PDF 的更新负责人、版本与更新时间；确定网页资料的允许抓取范围和更新频率。
- [ ] 上线方式：确认是仅在本机使用、校园内网部署，还是公网部署；确定域名、HTTPS、备份和管理员交接方式。
- [ ] 费用与限制：确定模型和 Embedding 的月预算、单次提问限制、日志保留范围。

## 六、资料查看边界

- 知识库 PDF 和项目要求文档已经属于项目实现与验收依据，后续联调可继续按需要查看。
- 展示视频文件体积较大；在你明确要求观看之前，不打开、复制或分析展示视频。
- DeepSeek V4 Pro 的 API key 文档留到真实接口接入时再查看；任何密钥仅用于本地配置，不写入本大纲。
- 若资料中包含账号或个人信息，输出验收报告时仅记录是否配置成功，不展示原文。

## 七、你可以用这份清单判断是否准备完毕

- [ ] java -version、javac -version、mvn -v 均显示可用的 JDK 17／21 环境。
- [ ] Docker Desktop 已运行，docker compose version 和 docker info 正常。
- [ ] 项目根目录已有填写好的本地 .env，管理员和数据库密码已设置。
- [ ] 将上述三个条件完成后通知 Codex 继续构建和联调。
- [ ] 只有在进行真实 AI 接口测试时才处理 API key 文档；展示视频等待你的明确指令。

## 参考资料

- Docker Desktop Windows 安装说明：https://docs.docker.com/desktop/setup/install/windows-install/
- Spring Boot 系统要求：https://docs.spring.io/spring-boot/3.5/system-requirements.html
- 阿里云文本向量接口：https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api/
- 阿里云百炼 API key 说明：https://help.aliyun.com/zh/model-studio/get-api-key
