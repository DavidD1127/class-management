#!/usr/bin/env python3
"""演示数据补充脚本（可重复运行，只追加不覆盖）。

补齐三处缺口，让答辩演示的每个页面都有内容：
  1. 作业：6 项布置给当前 36 人名单，附带完成/迟交/缺交/待交混合记录
  2. 表扬：20 条，分散到三班与 10 月下旬至 11 月中旬
  3. 成绩：11 月阶段检测三场（每班一场），三科共 108 条，含个别缺考

三条原则：
  - 幂等：同名作业/考试已存在即跳过，重复运行不会翻倍
  - 不覆盖：只新增与状态更新，不删除任何记录
  - 可复现：分数由学生 id 确定性推导，不用随机数，两次运行结果一致

用法（后端需已启动，默认 8092）：
    python scripts/Supplement-DemoData.py
    HJ_API_BASE=http://127.0.0.1:8092 HJ_DEMO_USER=12345 HJ_DEMO_PASSWORD=12345 \
        python scripts/Supplement-DemoData.py --dry-run

注意：作业模块当前没有删除接口，脚本不会也不能清理历史作业。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta

API = os.environ.get("HJ_API_BASE", "http://127.0.0.1:8092").rstrip("/")
USER = os.environ.get("HJ_DEMO_USER", "12345")
PASSWORD = os.environ.get("HJ_DEMO_PASSWORD", "12345")

EXAM_DATE = "2026-11-10"
EXAM_NAME = "2026-2027学年高一11月阶段检测（模拟）"
SUBJECTS = [("语文", 150, 105.0), ("数学", 150, 97.0), ("英语", 150, 108.0)]
CLASS_BONUS = {1: 5.0, 2: -1.5, 3: -6.0}
SUBJECT_ADJ = {"语文": 2.0, "数学": -3.0, "英语": 5.0}
ABSENT_PLAN = {1: "数学", 2: "英语", 3: "语文"}
ABSENT_INDEX = 3

# (班级, 科目, 名称, 布置日, 截止时刻)
ASSIGNMENT_PLAN = [
    (1, "语文", "高一期中复习·文言文默写", "2026-10-28", "2026-11-04T20:00"),
    (1, "数学", "函数与导数专项练习", "2026-11-12", "2026-11-20T22:00"),
    (2, "英语", "Unit 3 阅读理解训练", "2026-11-01", "2026-11-08T21:00"),
    (2, "物理", "牛顿运动定律习题", "2026-11-13", "2026-11-21T22:00"),
    (3, "数学", "数列单元测试卷", "2026-10-30", "2026-11-06T20:00"),
    (3, "英语", "作文仿写两篇", "2026-11-10", "2026-11-18T22:00"),
]

PRAISE_REASONS = [
    "期中考试年级前十",
    "主动帮同学讲解函数题",
    "值日认真，教室整洁",
    "校级作文比赛二等奖",
    "运动会 800 米第一名",
    "带动小组完成分组实验",
    "连续四周作业全勤",
    "主动整理班级图书角",
    "数学竞赛校内选拔入围",
    "课堂发言积极，思路清晰",
]

DRY = False


def call(path, token=None, method="GET", body=None):
    """调用后端，返回解析后的 JSON；HTTP 错误折叠成 dict。"""
    if DRY and method in ("POST", "PUT", "DELETE"):
        return {"__dry__": True, "data": {"id": -1}}
    req = urllib.request.Request(
        API + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        return {"error": exc.code, "detail": exc.read().decode()[:200]}


def fetch_all(path, token, size=100):
    """按分页取完全部记录。"""
    items, page = [], 0
    while True:
        data = call(f"{path}?page={page}&size={size}", token).get("data") or {}
        items.extend(data.get("content", []))
        if page + 1 >= data.get("pages", 1):
            return items
        page += 1


def login():
    # 登录必须真实执行，不受 --dry-run 拦截
    req = urllib.request.Request(
        API + "/api/auth/login",
        data=json.dumps({"username": USER, "password": PASSWORD}).encode(),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            res = json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        sys.exit(f"登录失败：HTTP {exc.code}")
    token = (res.get("data") or {}).get("token")
    if not token:
        sys.exit(f"登录失败：{res}")
    return token


def roster(token):
    students = fetch_all("/api/students", token)
    by_class = {}
    for s in sorted(students, key=lambda x: (x["classId"], x["id"])):
        by_class.setdefault(s["classId"], []).append(s)
    return students, by_class


def step_assignments(token, by_class):
    """新建作业并补齐状态记录。"""
    existing = {a["name"] for a in fetch_all("/api/assignments", token)}
    created = []
    for class_id, subject, name, assigned, deadline in ASSIGNMENT_PLAN:
        if name in existing:
            print(f"  跳过已存在作业：{name}")
            continue
        res = call("/api/assignments", token, "POST", {
            "name": name, "classId": class_id, "subject": subject,
            "description": "演示用模拟作业，仅供毕业答辩展示",
            "assignedDate": assigned, "deadline": deadline,
        })
        if "error" in res:
            print(f"  ! 建作业失败 {name}: {res.get('detail')}")
            continue
        created.append((res["data"]["id"], class_id, name, deadline))
        print(f"  新建作业 #{res['data']['id']} {name}")

    # 已新建的才写状态；已存在的不动，避免覆盖手工调整
    now = datetime.now()
    tally = {"COMPLETED": 0, "LATE": 0, "PENDING": 0, "INCOMPLETE": 0}
    for aid, class_id, name, deadline_str in created:
        assigned = datetime.fromisoformat(next(p[3] for p in ASSIGNMENT_PLAN if p[2] == name) + "T08:00")
        deadline = datetime.fromisoformat(deadline_str)
        expired = deadline < now
        for idx, stu in enumerate(by_class.get(class_id, [])):
            if expired:
                state = "COMPLETED" if idx < 8 else ("LATE" if idx < 10 else "INCOMPLETE")
            else:
                state = "COMPLETED" if idx < 7 else ("PENDING" if idx < 10 else "INCOMPLETE")

            payload = {"status": state}
            if state == "COMPLETED":
                spot = assigned + (deadline - assigned) * 0.6 if idx % 3 else deadline - timedelta(hours=2)
                spot = max(assigned, min(spot, min(deadline, now) - timedelta(minutes=5)))
                payload["submittedAt"] = spot.isoformat(timespec="minutes")
            elif state == "LATE":
                spot = deadline + timedelta(days=1, hours=3)
                if spot > now:
                    spot = now - timedelta(hours=3)
                payload["submittedAt"] = spot.isoformat(timespec="minutes")

            res = call(f"/api/assignments/{aid}/students/{stu['id']}", token, "PUT", payload)
            if "error" in res:
                print(f"  ! {name}/{stu['name']}/{state}: {res.get('detail')}")
            else:
                tally[state] += 1
    print(f"  作业记录：{tally}")


def step_praises(token, by_class):
    """补 20 条表扬。"""
    total = (call("/api/praises?page=0&size=1", token).get("data") or {}).get("total", 0)
    if total:
        print(f"  已有表扬 {total} 条，跳过")
        return
    added = 0
    for i in range(20):
        class_id = [1, 2, 3][i % 3]
        members = by_class.get(class_id) or []
        if not members:
            continue
        stu = members[(i * 5) % len(members)]
        day = (datetime(2026, 10, 25) + timedelta(days=(i * 2) % 23)).date().isoformat()
        res = call("/api/praises", token, "POST", {
            "studentId": stu["id"], "praiseDate": day,
            "reason": PRAISE_REASONS[i % len(PRAISE_REASONS)],
            "remark": "演示用模拟记录",
        })
        if "error" in res:
            print(f"  ! 表扬失败 {stu['name']}: {res.get('detail')}")
        else:
            added += 1
    print(f"  表扬新增 {added} 条")


def step_scores(token, by_class):
    """补 11 月阶段检测：每班一场考试，三科成绩。"""
    exams = call("/api/exams", token).get("data") or []
    have = {(e["classId"], e["name"], str(e["examDate"])[:10]) for e in exams}
    added_exam = added_score = 0
    for class_id in sorted(by_class):
        if (class_id, EXAM_NAME, EXAM_DATE) in have:
            print(f"  跳过已存在考试：{class_id} 班")
            continue
        res = call("/api/exams", token, "POST", {"name": EXAM_NAME, "classId": class_id, "examDate": EXAM_DATE})
        if "error" in res:
            print(f"  ! 建考试失败 {class_id} 班: {res.get('detail')}")
            continue
        exam_id = res["data"]["id"]
        added_exam += 1
        members = sorted(by_class[class_id], key=lambda x: x["id"])
        absent_subject = ABSENT_PLAN.get(class_id)
        for idx, stu in enumerate(members):
            for subject, full, base in SUBJECTS:
                if subject == absent_subject and idx == ABSENT_INDEX:
                    payload = {"studentId": stu["id"], "examId": exam_id, "subject": subject,
                               "score": None, "fullScore": full, "status": "ABSENT",
                               "remark": "模拟数据：因病缺考"}
                else:
                    offset = ((stu["id"] * 37 + idx * 13 + len(subject) * 5) % 37) - 18
                    score = base + CLASS_BONUS.get(class_id, 0) + SUBJECT_ADJ.get(subject, 0) + offset
                    if subject == "数学" and stu["id"] % 5 == 0:
                        score += 7
                    if subject == "语文" and stu["id"] % 6 == 0:
                        score += 6
                    payload = {"studentId": stu["id"], "examId": exam_id, "subject": subject,
                               "score": round(max(45, min(full, score)), 1), "fullScore": full,
                               "status": "RECORDED", "remark": "模拟数据：2026年11月阶段检测，成绩为虚构样例"}
                r = call("/api/scores", token, "POST", payload)
                if "error" in r:
                    print(f"  ! {stu['name']} {subject}: {r.get('detail')}")
                else:
                    added_score += 1
    if added_exam:
        print(f"  新建考试 {added_exam} 场，成绩 {added_score} 条")


def report(token):
    print("\n当前数据：")
    for label, path in [("班级", "/api/classes"), ("学生", "/api/students?page=0&size=1"),
                        ("考试", "/api/exams"), ("成绩", "/api/scores?page=0&size=1"),
                        ("考勤", "/api/attendance?page=0&size=1"), ("表扬", "/api/praises?page=0&size=1"),
                        ("作业", "/api/assignments?page=0&size=1")]:
        data = call(path, token).get("data")
        n = len(data) if isinstance(data, list) else (data or {}).get("total")
        print(f"  {label:6s} {n}")

    dash = call("/api/dashboard", token).get("data") or {}
    latest = dash.get("latestExam") or {}
    print(f"  仪表盘：最新考试 {latest.get('name')}（{latest.get('examDate')}）"
          f"，均分 {len(dash.get('examAverages', []))} 项"
          f"，作业状态 {len(dash.get('assignmentStates', []))} 项"
          f"，考勤 {len(dash.get('attendance', []))} 组"
          f"，表扬 {dash.get('praises')}")


def main():
    global DRY
    ap = argparse.ArgumentParser(description="补充演示数据（幂等）")
    ap.add_argument("--dry-run", action="store_true", help="只打印计划，不写库")
    ap.add_argument("--skip-scores", action="store_true", help="跳过成绩补充")
    args = ap.parse_args()
    DRY = args.dry_run

    print(f"目标后端：{API}{'（演习模式）' if DRY else ''}")
    token = login()
    students, by_class = roster(token)
    print(f"在读学生 {len(students)} 人，{len(by_class)} 个班\n")

    print("1/3 作业")
    step_assignments(token, by_class)
    print("2/3 表扬")
    step_praises(token, by_class)
    if not args.skip_scores:
        print("3/3 成绩")
        step_scores(token, by_class)
    report(token)


if __name__ == "__main__":
    main()
