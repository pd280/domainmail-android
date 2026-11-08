#!/usr/bin/env python3
"""
用 GitHub REST API 完成「推代码 → 触发 Actions → 追日志 → 取 APK」全流程。

不执行任何本地编译 / 测试 / Gradle / cargo 命令，纯标准库 HTTP 调用。

用法：
  export GITHUB_TOKEN=ghp_xxx
  export GITHUB_REPO=owner/domainmail-android      # 可选，不填自动建 <用户名>/domainmail-android
  export GITHUB_API=https://api.github.com          # 可选，自建/代理端点
  export GITHUB_BRANCH=main                         # 可选

  python tools/github_build.py all                 # 一把梭：push + dispatch + 等待 + 报告
  python tools/github_build.py push                # 只推代码
  python tools/github_build.py dispatch            # 只触发 workflow_dispatch
  python tools/github_build.py status --run-id N   # 查某次 run 的状态和每个 step
  python tools/github_build.py logs --run-id N     # 下载完整日志 zip
  python tools/github_build.py artifacts --run-id N

Token 需要的权限：repo（含 workflow）、actions:read。
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = os.environ.get("GITHUB_API", "https://api.github.com").rstrip("/")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
REPO = os.environ.get("GITHUB_REPO", "")
REPO_NAME = os.environ.get("GITHUB_REPO_NAME", "domainmail-android")
BRANCH = os.environ.get("GITHUB_BRANCH", "main")
WORKFLOW = "build-apk.yml"

SKIP_DIRS = {
    ".git", "target", "dist", ".gradle", "build",
    "node_modules", "__pycache__", ".idea", "captures",
}

# 只跳过仓库根目录里 clone 的参考后端。千万不能按任意层级匹配 "domainmail"，
# 否则会把包名 com/ifwqu/domainmail 下的 Kotlin 源码一起跳过，
# 构建出没有 MainActivity 的空壳 APK（装上直接闪退）。
SKIP_ROOT_DIRS = {"domainmail"}
SKIP_SUFFIXES = {
    ".so", ".apk", ".aab", ".jar", ".class", ".exe", ".dll", ".pdb",
    ".zst", ".crate", ".zip", ".bak", ".log", ".keystore", ".jks",
}
SKIP_FILES = {".DS_Store", "Thumbs.db", "local.properties", "config.json"}


# ---------------------------------------------------------------- HTTP

def req(method, path, body=None, raw=False, ok=(200, 201, 202, 204)):
    url = path if path.startswith("http") else API + path
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "domainmail-gh-build/1.0",
    }
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    if data is not None:
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=90) as resp:
            payload = resp.read()
            parsed = payload if raw else (json.loads(payload) if payload else {})
            return resp.status, dict(resp.headers), parsed
    except urllib.error.HTTPError as exc:
        payload = exc.read()
        if exc.code in ok:
            try:
                parsed = payload if raw else (json.loads(payload) if payload else {})
            except Exception:
                parsed = payload if raw else {}
            return exc.code, dict(exc.headers), parsed
        try:
            detail = json.loads(payload)
        except Exception:
            detail = payload.decode("utf-8", "replace")
        print(f"[HTTP {exc.code}] {method} {url}\n{detail}", file=sys.stderr)
        raise


# ---------------------------------------------------------------- 收集文件

def collect_files():
    files = {}
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT).as_posix()
        parts = rel.split("/")
        if parts[0] in SKIP_ROOT_DIRS:
            continue
        if any(part in SKIP_DIRS for part in parts[:-1]):
            continue
        if path.name in SKIP_FILES or path.suffix.lower() in SKIP_SUFFIXES:
            continue
        try:
            files[rel] = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            print(f"  skip binary: {rel}")
    return files


# ---------------------------------------------------------------- 仓库

def resolve_repo():
    global REPO
    if not REPO:
        _, _, me = req("GET", "/user")
        REPO = f"{me['login']}/{REPO_NAME}"
        print(f"[repo] 未指定，使用 {REPO}")
    code, _, _ = req("GET", f"/repos/{REPO}", ok=(200, 404))
    if code == 404:
        _, _, info = req(
            "POST", "/user/repos",
            {
                "name": REPO.split("/", 1)[1],
                "private": False,
                "auto_init": True,
                "description": "DomainMail Android client - Rust core + Jetpack Compose, built by GitHub Actions",
            },
            ok=(201,),
        )
        print(f"[repo] 已创建 {info['full_name']}")
        time.sleep(2)
    else:
        print(f"[repo] 已存在 {REPO}")


def head_sha():
    code, _, info = req(
        "GET", f"/repos/{REPO}/git/ref/heads/{BRANCH}", ok=(200, 404)
    )
    if code == 404:
        return None
    return info["object"]["sha"]


def push(files):
    """用 Git Data API 一次 create-tree 提交全部文本文件，不需要本地 git。"""
    resolve_repo()

    # 防呆：没有 Kotlin 源码时 Gradle 照样能"构建成功"，但 APK 里没有
    # MainActivity，装上必闪退。这里直接拒绝推送。
    kotlin = [
        p for p in files
        if p.startswith("android/app/src/main/java/") and p.endswith(".kt")
    ]
    if len(kotlin) < 5:
        sys.exit(
            f"[push] 拒绝推送：只找到 {len(kotlin)} 个 Kotlin 源文件，"
            "构建出的会是空壳 APK（缺少 Activity）"
        )
    workflow = ".github/workflows/build-apk.yml"
    if workflow not in files:
        sys.exit("[push] 拒绝推送：缺少 build-apk.yml")

    print(f"[push] {len(files)} 个文件，其中 Kotlin 源文件 {len(kotlin)} 个")

    entries = [
        {"path": name, "mode": "100644", "type": "blob", "content": content}
        for name, content in files.items()
    ]
    _, _, tree = req("POST", f"/repos/{REPO}/git/trees", {"tree": entries})
    base = head_sha()
    parents = [base] if base else []

    _, _, commit = req(
        "POST", f"/repos/{REPO}/git/commits",
        {
            "message": "DomainMail Android: Rust core + Compose UI + GitHub Actions build",
            "tree": tree["sha"],
            "parents": parents,
        },
    )
    if base:
        req(
            "PATCH", f"/repos/{REPO}/git/refs/heads/{BRANCH}",
            {"sha": commit["sha"], "force": True},
        )
    else:
        req(
            "POST", f"/repos/{REPO}/git/refs",
            {"ref": f"refs/heads/{BRANCH}", "sha": commit["sha"]},
        )
    print(f"[push] commit {commit['sha'][:12]} -> {REPO}@{BRANCH}")
    return commit["sha"]


# ---------------------------------------------------------------- Actions

def dispatch():
    req(
        "POST", f"/repos/{REPO}/actions/workflows/{WORKFLOW}/dispatches",
        {"ref": BRANCH}, ok=(204,),
    )
    print("[dispatch] workflow_dispatch 已发出")
    # Actions 需要一点时间创建 run
    for _ in range(10):
        time.sleep(3)
        _, _, runs = req(
            "GET", f"/repos/{REPO}/actions/workflows/{WORKFLOW}/runs?per_page=5"
        )
        items = runs.get("workflow_runs", [])
        if items:
            return items[0]["id"]
    raise RuntimeError("没有查到新建的 workflow run")


def status(run_id, wait_seconds=0):
    deadline = time.time() + wait_seconds
    while True:
        _, _, run = req("GET", f"/repos/{REPO}/actions/runs/{run_id}")
        state, conclusion = run["status"], run.get("conclusion")
        print(f"[status] run {run_id}: status={state} conclusion={conclusion}")
        if state == "completed" or wait_seconds == 0:
            report(run_id)
            return conclusion
        if time.time() > deadline:
            print("[status] 超时，可用 status --run-id 继续查")
            return "timeout"
        time.sleep(20)


def report(run_id):
    _, _, jobs = req("GET", f"/repos/{REPO}/actions/runs/{run_id}/jobs?per_page=50")
    for job in jobs.get("jobs", []):
        print(f"\nJOB  {job['name']}  -> {job.get('conclusion')}")
        for step in job.get("steps", []):
            print(f"  {step['number']:>2}. {step['name']:<45} {step.get('conclusion')}")
    _, _, arts = req("GET", f"/repos/{REPO}/actions/runs/{run_id}/artifacts")
    for art in arts.get("artifacts", []):
        print(
            f"\nARTIFACT {art['name']}  {art['size_in_bytes']} bytes  "
            f"expired={art['expired']}\n  {art['archive_download_url']}"
        )


def logs(run_id, out="actions-logs.zip"):
    code, _, data = req(
        "GET", f"/repos/{REPO}/actions/runs/{run_id}/logs",
        raw=True, ok=(200, 302, 404),
    )
    if code == 404:
        print("[logs] 日志还未生成或已过期")
        return
    if isinstance(data, bytes) and data:
        Path(out).write_bytes(data)
        print(f"[logs] 已保存 {out} ({len(data)} bytes)")
    else:
        print("[logs] 空响应")


def artifacts(run_id):
    _, _, arts = req("GET", f"/repos/{REPO}/actions/runs/{run_id}/artifacts")
    for art in arts.get("artifacts", []):
        print(f"{art['id']}  {art['name']}  {art['size_in_bytes']} bytes  {art['archive_download_url']}")


# ---------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "cmd",
        choices=["all", "push", "dispatch", "status", "logs", "artifacts"],
    )
    parser.add_argument("--run-id", type=int)
    parser.add_argument("--wait", type=int, default=3600, help="all/status 等待秒数")
    args = parser.parse_args()

    if not TOKEN:
        sys.exit("缺少 GITHUB_TOKEN")

    if args.cmd == "push":
        push(collect_files())
        return

    if REPO:
        pass
    else:
        resolve_repo()

    if args.cmd == "dispatch":
        print(f"[dispatch] run_id={dispatch()}")
        return

    run_id = args.run_id
    if run_id is None and args.cmd != "all":
        sys.exit("需要 --run-id")

    if args.cmd == "all":
        push(collect_files())
        run_id = dispatch()
        print(f"[all] run_id={run_id}  {API.replace('api.', '')}/{REPO}/actions/runs/{run_id}")
        conclusion = status(run_id, args.wait)
        if conclusion != "success":
            logs(run_id)
        return

    if args.cmd == "status":
        status(run_id, args.wait)
    elif args.cmd == "logs":
        logs(run_id)
    elif args.cmd == "artifacts":
        artifacts(run_id)


if __name__ == "__main__":
    main()
