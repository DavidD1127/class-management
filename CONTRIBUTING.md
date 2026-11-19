# 贡献指南

感谢愿意花时间。这个项目规模不大，改动前请先读完本文，能省掉来回沟通。

## 开发环境

最低要求：

- JDK 17
- Node.js 20.19+ 或 22.12+
- MySQL 8.4（用 `docker compose up -d` 最省事；跑单元测试不需要）

Maven 不需要预装，用仓库自带的 `backend/mvnw`。

```powershell
docker compose up -d
cd backend
./mvnw test          # 默认走内存 H2
cd ../frontend
npm ci
npm run build
```

跑起来见 [docs/QUICKSTART.md](docs/QUICKSTART.md)。

## 提交前请自查

1. `cd backend && ./mvnw test` 全绿
2. `cd frontend && npm run build` 通过
3. `node scripts/Scan-Secrets.mjs --no-history --fail` 无命中
4. 没有把本机绝对路径、真实密码、API Key 写进任何文件

CI 会跑同样的四项，PR 上红了的先修完再请求评审。

## 代码约定

- **不引入新依赖前先说清楚理由**。这个项目刻意保持小：没有 Redis、没有消息队列、没有微服务
- 业务侧数据访问用 JdbcTemplate 写原生 SQL，参数一律绑定，不做字符串拼接
- 迁移脚本只增不改。已经发布过的 `V*.sql` 不要修改，改动请新增一版
- 写操作必须有明确的事务边界；删除走回收站，不直接物理删除
- 注释解释「为什么」，不复述「做了什么」
- 中文注释与中文文案保持现状；代码标识符、命令、日志用英文

## 提交信息

沿用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```text
feat(score): 支持按考试批量导入
fix(ai): 工具参数错误回给模型自纠而不是直接 400
docs: 补充 DEMO_DB_DATADIR 说明
chore(deps): 升级 Spring Boot 到 3.3.6
```

类型用 `feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `ci`，范围可选。

## 分支与 PR

- 从 `main` 切分支：`feat/xxx`、`fix/xxx`
- 一个 PR 只做一件事。顺手重构请单独提
- PR 描述里写清楚：改了什么、为什么改、怎么验证的
- 涉及数据库迁移、权限变更、AI 工具白名单的改动，请额外说明影响面

## 报告问题

用 Issue 模板。请提供：

- 复现步骤（越具体越好）
- 期望行为与实际行为
- 版本信息：`java -version`、`node -v`、数据库版本、操作系统
- 相关日志（**先删掉密钥、密码、真实姓名与学号**）

安全漏洞请不要开公开 Issue，见 [SECURITY.md](.github/SECURITY.md)。

## 行为准则

讨论对事不对人。技术分歧用证据说话：日志、复现、文档。不接受人身攻击、歧视性言论或骚扰。
