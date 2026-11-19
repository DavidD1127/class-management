# 快速开始与常见问题

本文补 README 没展开的部分：两条启动路径的细节、配置项含义、以及跑不起来时先查什么。

## 两条路径怎么选

| | 便携版 | 开发模式 |
|---|---|---|
| 需要装数据库 | 不需要（内置 H2 文件库） | 需要（MySQL 8.4，或用 docker compose） |
| 需要装 Maven | 不需要（jlink 打包后只用内置 JRE） | 不需要（仓库自带 Maven Wrapper） |
| 适合 | 演示、给不装环境的人试用 | 开发、调试、跑数据库测试 |
| 数据位置 | `data/classroom.mv.db`（包内） | MySQL 数据目录 + `DEMO_DB_DATADIR` |
| 端口 | 18765 | 后端 8092 / 前端 15174 |

## 便携版细节

```powershell
pwsh -File scripts/Build-Portable.ps1
```

做三件事：构建前端 → 用 `-Pportable` 把 H2 打进 jar → 用 jlink 裁一个最小 JRE，最后输出到 `output/portable-<时间戳>/ClassManagement/`（附一个同名 zip）。

首次运行会自动执行 `npm ci` 装前端依赖，之后再跑会跳过。

> 注意：H2 驱动只在 `-Pportable` 下打包。用 `方式二` 的 `-Pisolated-build` 单独构建出来的 jar 不含 H2，拿去连 H2 会报 `Failed to load driver class org.h2.Driver`。想在开发模式下不装 MySQL 跑，加 `-Pisolated-build,portable`。

首次运行 `Start.cmd` 会：

1. 让你设置管理员密码（至少 12 位，只用于新建账号，不覆盖已有账号）
2. 可选输入 DeepSeek API Key，直接回车跳过
3. 在包内 `data/` 建 H2 文件库并跑 Flyway 迁移

如果解压到了中文路径，启动脚本会用 `subst` 临时映射一个盘符再启动——随包 JRE 的 Windows 启动器在部分代码页下读不了中文路径。这是已知限制，不是 bug。

## 开发模式细节

### 数据库

`docker compose up -d --wait` 启动 MySQL 8.4 并等待健康检查。端口仅对本机开放（127.0.0.1:3310）。数据保存在已忽略的 `./.data/mysql-docker`。

Compose 显式设置项目名，因此仓库可以放在中文目录。

不用 Docker 也可以自己装 MySQL 8.4，但必须满足：8.0.16 以上（迁移脚本用了 CHECK 约束）、`utf8mb4`。

### 建库与账号

docker compose 会按 `.env` 里的 `MYSQL_ROOT_PASSWORD` 初始化，并自动创建 `class_management_demo`。手工建库：

```sql
CREATE DATABASE class_management_demo CHARACTER SET utf8mb4;
CREATE USER 'class_management_app'@'localhost' IDENTIFIED BY '<本地独立密码>';
GRANT ALL ON class_management_demo.* TO 'class_management_app'@'localhost';
```

### 关于 `Start-AgentLocal.ps1`

它做四件事：

1. 读 `DB_URL` 等配置。仓库里没有本机私有配置时，直接读同名环境变量
2. **校验连接串指向 `class_management_demo`**，否则直接报错——防止演示数据写进别的库
3. 3310 没监听时，按 `DEMO_DB_DATADIR` 拉起 `mysqld`
4. 起后端并等 8092 就绪，前端没起就顺手起前端

因为第 3 步，这个脚本要求本机装了 MySQL 的二进制（`MYSQL_BIN` 可覆盖）。如果你用 Docker 提供数据库，3310 已经在监听，第 3 步会跳过。

数据目录的两条硬性要求：

- **不能含非 ASCII 字符**：`mysqld` 从命令行取 `--datadir` 时，Windows 会把中文路径转坏
- **不能放 OneDrive 或 `%TEMP%`**：OneDrive 会同步正在写入的表空间；临时目录可能被清理工具递归删掉

脚本对这两条都做检查，不满足时直接报错并提示改用 `DEMO_DB_DATADIR`。

### 前端

先安装依赖，再运行启动脚本。启动脚本会自动启动 Vite。以下是单独启动前端的命令。

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1 --port 15174 --strictPort
```

Vite 通过 `API_PROXY_TARGET` 转发 `/api` 到后端，默认 `http://127.0.0.1:8092`。

## 演示数据

```powershell
pwsh -File scripts/Initialize-Demo.ps1
```

需要先设 `DEMO_DB_PASSWORD`。脚本只操作 `class_management_demo`，**任何业务表非空即停止**，重复执行不会覆盖，也不提供默认清库。造出的数据：3 班 36 人、考试与成绩、6 项作业 72 条名单、考勤与表扬。

## 常见问题

| 现象 | 排查方向 |
|---|---|
| 端口被占用 | 8092 / 15174 / 3310 分别对应后端、前端、数据库；脚本不会结束别人的进程 |
| MySQL 1045 / 2003 | 账号授权、密码、端口三项；Docker 场景注意 `.env` 里的密码与 `DB_PASSWORD` 是否一致 |
| 缺表 / Flyway 失败 | 包版本、目标库、迁移历史是否被改过；不要删 `flyway_schema_history` 或改旧迁移 |
| JWT 配置失败 | `JWT_SECRET` 至少 32 字节 |
| 登录失败 | 初始化管理员**不覆盖**已有密码；删表重建不是解决办法 |
| 前端 404 / 502 | `API_PROXY_TARGET` 是否指向当前后端端口 |
| 启动报「路径含非 ASCII 字符」 | 用 `DEMO_DB_DATADIR` 指到纯英文路径 |
| PowerShell 中文脚本报语法错 | Windows PowerShell 5.1 需要 UTF-8 BOM；建议改用 PowerShell 7 |
| AI 报未配置 | `DEEPSEEK_API_KEY` 没设；其余功能不受影响 |
