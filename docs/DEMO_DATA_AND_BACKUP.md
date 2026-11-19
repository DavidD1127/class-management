# 演示数据与备份恢复

## 2026-11-17 演示数据补齐

体检发现三处空缺，全部为模拟数据，不涉及真实学生信息：

| 模块 | 补齐前 | 补齐后 | 说明 |
|---|---:|---:|---|
| 表扬 | 0 | 20 | 分散到三班与 10-25 至 11-16 |
| 作业 | 6 项（分母 0） | +6 项（含完整记录） | 新作业按当前 36 人名单快照 |
| 作业记录 | 0 | 72（完成 45 / 迟交 6 / 缺交 12 / 待交 9） | 状态混合，覆盖四种口径 |
| 考试 | 13 | 16 | 新增 11 月阶段检测每班一场 |
| 成绩 | 216 | 324 | 新增 108 条，三科，含每班 1 人缺考 |

补齐前的空状态会直接影响演示：表扬页与作业页全空，仪表盘的考试均分、作业状态、表扬三块都没有数据。

新增考试的分数由学生 id 确定性推导（`scripts/Supplement-DemoData.py`），不是随机数，两次运行结果一致；三班设了不同的班级水平（1 班偏强、3 班偏弱），避免三班均分完全雷同。

已知情况：

- 原有 6 项「演示数学作业」布置于旧名单，分母为 0、完成率不适用。作业模块原先**没有删除接口**，这 6 项无法通过界面或 API 移除；2026-11-17 已补 `DELETE /api/assignments/{id}`（带完成记录一起进回收站），这 6 项已清理，回收站可恢复。
- 「演示AI录入专场」日期为 10-21，原先是最新考试，导致 1 班仪表盘均分空白。新增 11-10 的考试后它不再是最新，问题自然消解，未改动该考试本身。

执行方式（后端已启动时）：

```powershell
python scripts/Supplement-DemoData.py            # 补齐
python scripts/Supplement-DemoData.py --dry-run  # 只打印计划
```

脚本幂等：同名作业与考试已存在即跳过，可重复运行。默认连接 `127.0.0.1:8092`、账号 `12345`，可用 `HJ_API_BASE` / `HJ_DEMO_USER` / `HJ_DEMO_PASSWORD` 覆盖。

## 2026-10-23 新名单成绩及考勤补充

已为新36人名单补充两轮三科模拟成绩，每班两条考试目录，共新增6条考试、216条成绩：207条实考、9条缺考。科目为语文、数学、英语，每科150分；每班每次考试每科12人，10月月考每班1名学生因病缺考三科，并有当天点名缺勤记录对应。分数为生成的模拟样例，不是真实考试结果。

| 考试名称 | 日期 | 语文 | 数学 | 英语 |
| --- | --- | --- | --- | --- |
| 2026-2027学年高一9月阶段检测（模拟） | 2026-09-29 | 08:00–10:30 | 13:30–15:30 | 16:00–18:00 |
| 2026-2027学年高一10月月考（模拟） | 2026-10-20 | 08:00–10:30 | 13:30–15:30 | 16:00–18:00 |

考试目录保留考试日期，具体科目的考试日期与时段写入成绩备注。新增18条考勤：迟到12、早退3、缺勤3，分布于2026-10-12、15、16、19、20、23日，每班6条。每条填写具体时刻和原因；迟到样例按07:30早读计算，缺勤时刻为点名确认时间而非到校时间。所有日期和时刻均为模拟安排，按北京时间表达。

使用现有成绩导入预览与确认接口分18批写入，无覆盖；考勤通过现有新增接口写入。已校验每名学生两次考试三科各一条、缺考分数为空、分数不超满分、考勤无重复且时间为过去时刻、缺考与缺勤一致。原学生名单、旧考试目录、回收站208条数据保持原状。

操作前备份：`.test-data/simulation-before-20261023-133855.sql`，SHA256：`7B32B08FEDF6F4A0B672C687AE8AD08D16D93A649C1D4915B92B6214193B61FE`。报告：`.test-data/academic-simulation-report.json`。明细导出：`output/academic-samples/语数英成绩-2026-模拟.csv`、`output/academic-samples/考勤记录-2026-模拟.csv`。

## 2026-10-23 当前名单替换记录

按用户要求，当前3310演示库已替换全部36名旧DEMO学生。三个班级仍各12人，使用自然中文姓名和8位数字学号，规则为入学年份2026＋两位班号＋两位序号，例如20260101。全部为生成的模拟身份，不是真实学生个人信息。新名单文件：`output/rosters/高一学生名单-2026-模拟.csv`。

旧名单36人及其62条成绩、19条考勤、19条表扬、72条作业完成记录，共208条数据进入分类回收站，可按学生姓名或旧学号查找并恢复整组。名单替换时班级、7项考试及6项作业目录保持原状；新学生没有继承旧学生记录，替换完成时成绩、考勤、表扬及作业完成记录为空，之后成绩与考勤已按上节另行补入新模拟样例。导入前36行预览均有效，导入后逐人校验姓名、性别、学号和班级，三个班级人数各12，旧学号及旧学生ID不在活动名单中。

替换前完整备份：`.test-data/roster-before-replace-20261023-132951.sql`，SHA256：`A39F4674D877EBFC55AA1FB55FE99C7585BB532F57461B975891F2842CACACFB`。操作报告：`.test-data/roster-replace-report.json`。以下初始化数据及验收过程为历史记录，不代表替换后的活动名单。

只使用虚构数据。原3306/class_management在本轮保持V1/V2、1个账号、0班级/0学生，未升级、未清空；本轮专用3310实例与原实例不同。

## 初始化与重演

1. 管理员创建独立空class_management_demo库及仅该库权限账号，配置见README。
2. 用最终JAR连接该库，Flyway创建V1–V4表；系统管理员由本地环境变量安全创建，旧账号不覆盖。
3. 在本地设置DEMO_DB_PASSWORD，执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Initialize-Demo.ps1 -Port <演示MySQL端口>`。默认账号class_management_demo_app，可用-User指定。
4. 该脚本固定数据库名，不接收正式库名称；检查9张业务表为空，生成SQL在单事务内插入。任意表非空立即拒绝，包括AI预览表，重复运行不覆盖。

脚本预检适用于停止人工写入的首次初始化窗口，不保证与并发管理员写入互斥；初始化期间不要使用管理界面。生成器含数量和禁止删除语句断言，不创建账号或硬编码密码。

| 数据 | 初始数量 | 特殊情况 |
|---|---:|---|
| 班级 | 3 | 演示高一（1）至（3）班 |
| 学生 | 36 | DEMO001–036；DEMO001/002同名“同名演示生” |
| 考试 | 7 | 每班2次，另有1班“演示AI录入专场” |
| 成绩 | 60 | 实考零分、缺考NULL、每班2人未录入 |
| 作业/名单 | 6/72 | 已完成、迟交、未完成、待完成/逾期待确认；每项1人排除 |
| 考勤 | 18 | 迟到/早退/缺勤各6，多个日期 |
| 表扬 | 18 | 虚构原因，多名学生 |

演示日期固定在2026-10，演示日期改变时应明确筛选日期；不要为保持画面而伪造“今天”的历史。AI写入后数据量会增加，恢复初始备份到新库可重演，禁止直接在正式库运行DELETE/DROP/TRUNCATE。

## 可执行备份

先安装MySQL客户端。用 `mysql_config_editor set --login-path=class-demo --host=127.0.0.1 --port=<端口> --user=<演示库账号> --password` 本地交互输入密码，不把密码写进命令行、脚本或报告。以下命令在不含空格的备份目录执行；备份含账号哈希和业务数据，也必须保密。

```powershell
mysqldump --login-path=class-demo --default-character-set=utf8mb4 --single-transaction --no-tablespaces --set-gtid-purged=OFF --result-file=class-demo-backup.sql class_management_demo
if($LASTEXITCODE -ne 0){throw '备份失败，不可继续恢复'}
Get-FileHash -Algorithm SHA256 class-demo-backup.sql
```

使用--result-file避免Windows PowerShell5.1的重定向编码改变SQL。备份不含CREATE DATABASE、DROP DATABASE或其他库，不使用--databases，恢复前人工核对文件来源/范围。备份窗口不要执行DDL；本项目表为InnoDB。

## 恢复演练

由管理员创建**全新的空恢复库**并给专用恢复账号授权，例如class_management_demo_restore；不要连接正式库恢复。使用单独的class-restore登录路径：

```powershell
mysql --login-path=class-restore --default-character-set=utf8mb4 class_management_demo_restore
```

进入mysql后运行：

```sql
source class-demo-backup.sql
SHOW TABLES;
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM students;
SELECT COUNT(*) FROM scores;
```

导入SQL可能包含DROP TABLE IF EXISTS，**只有确认是新空恢复库后才能执行**。恢复前核对 `SELECT DATABASE()`；源库继续保留。比较全部表行数、主外键/唯一约束、按主键排序的数据内容，再用应用连接恢复库验收；仅SQL导入退出码0不等于恢复业务正确。恢复成功后若需切换演示，先明确新DB_URL和端口；恢复账号与应用账号分别按目标库授权，不复制全局root权限。

## 本轮实际证据

已用mysqldump将3310/class_management_demo初始数据备份，并恢复到全新phase7_restore，包含users、8张传统业务表、ai_operations、flyway_schema_history共11张表。每表行数和按主键排序的内容SHA256一致。证据 `.test-data/phase7-backup-verification.json` 与备份文件 `.test-data/phase7-demo-backup.sql` 均被Git忽略。本轮未对原3306执行备份恢复或升级。

另已将同名样本调整及AI写入后的最终快照备份并恢复到全新phase7_restore_final；11张表的排序数据哈希、行数及完整SHOW CREATE TABLE（索引/外键/CHECK）均一致。最终证据为`.test-data/phase7-final-backup-verification.json`、`phase7-restored-structure.json`，备份为`phase7-final-demo-backup.sql`。源演示库最终36人、62条成绩、19条表扬、18条考勤，AI专场均分82。

本轮3310是临时隔离验收实例，账号/密码不可用于正式部署；验收结束关闭临时服务，数据目录保留。未提供自动重置和自动恢复正式库的脚本。
