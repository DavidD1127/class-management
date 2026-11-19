# 变更记录

本项目从 0.1.0 起按 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 组织，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [0.1.0] - 2026-11-18

首个公开版本。

### 新增

- `LICENSE`（MIT）、`CHANGELOG`、`CONTRIBUTING`、Issue 模板
- `docker-compose.yml`：一条命令起 MySQL 8.4，映射到 3310，不占用宿主机已有 3306
- `backend/mvnw` Maven Wrapper：构建不再要求预装 Maven
- `.github/workflows/ci.yml`：后端测试（H2）、前端构建、密钥扫描三条流水线
- `.github/dependabot.yml`：Maven / npm / GitHub Actions 依赖更新
- `.editorconfig`、`.gitattributes`：统一缩进与换行策略
- `.env.example`：全部后端环境变量的模板
- `docs/QUICKSTART.md`：两条启动路径的细节与常见问题
- `scripts/Scan-Secrets.mjs` 新增 `--no-history` 与 `--fail` 参数，便于在 CI 中阻断

### 变更

- 启动与运维脚本不再包含任何本机绝对路径，改为环境变量 + 通用默认值
  - MySQL 二进制目录：`MYSQL_BIN`
  - 演示库数据目录：`DEMO_DB_DATADIR`
  - Java 与 Node：`JAVA_HOME` / `NODE_EXE`
- `Start-AgentLocal.ps1` 支持在没有本机私有配置文件的干净克隆上运行（改读环境变量），并对数据库名做强校验
- 脚本按通配匹配后端产物，不再把版本号写死
- README 重写为面向使用者的结构；原技术细节迁入 `docs/`
- 前端 `package.json` 补齐 `name`、`description`、`license`
- 版本号由 `0.0.1-SNAPSHOT` 升为 `0.1.0`
- `ScoreController`、`PraiseController` 方法签名与参数分行，与新增的 `@PreAuthorize` 分列

### 修复

干净副本实测（`D:\repo-test\毕业设计`）跑出来的问题，全部只在「新克隆的仓库」里出现：

- `Build-Portable.ps1` 直接调用 `npm run build` 却从未装过依赖，新克隆没有 `node_modules`，报 `'vite' 不是内部或外部命令`。现在检测到缺失时先自动 `npm ci`
- `Start-AgentLocal.ps1` 假设 `.secrets` 已存在，写 JWT 密钥时抛 `Could not find a part of the path ...\.secrets\agent-jwt.dpapi`。现在自动创建该目录
- `Start-AgentLocal.ps1` 同样假设 `.test-data` 已存在，日志重定向失败导致后端起不来。现在自动创建
- `Start-AgentLocal.ps1` 在没有 `deepseek-key.dpapi` 时直接崩溃，与 README「缺 Key 时普通业务可用」矛盾。现在降级为提示语 + 空 Key，AI 报未配置、其余功能照常
- `docker-compose.yml` 在中文目录下报 `project name must not be empty`（Compose 无法从非 ASCII 目录名推导项目名）。现在显式声明 `name: class-management`

### 移除

- 仓库根目录两个写死本机路径的一次性运维脚本
- 演示库数据目录不再放在 `%TEMP%` 或 OneDrive 同步目录下

### 安全

- 确认全部 git 历史中未提交过真实凭据（仅有 `.example` 模板与扫描脚本）
- 测试夹具中的固定密码加 `test-fixture` 标记，避免被密钥扫描误报
