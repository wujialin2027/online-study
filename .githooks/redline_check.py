# -*- coding: utf-8 -*-
"""推送红线检查 —— 「允许清单」策略，而不是「黑名单目录」。

为什么改成允许清单：黑名单只能挡住你**想到**的目录。这次 _local/ 和 .workbuddy/
被挡住了，但下次新冒出来一个 notes/、截图/、面试准备/、backup/，黑名单根本不认识它，
东西就跟着上去了。所以反过来定规则：**只有明确列出来的路径能进仓库，其余一律拦下**。

这跟防火墙的默认策略是一个道理：默认拒绝，白名单放行。代价是偶尔要改白名单，
收益是"没想到的东西"永远不会被漏过去。

两种调用方式（分别由 pre-commit / pre-push 触发）：
    redline_check.py staged     检查暂存区（提交前）
    redline_check.py push <ranges...>  检查将要推送的提交（推送前）

输出一律用英文、文件名用 ascii() 转义：Windows 控制台默认 GBK，中文写进去是乱码，
提示看不清等于没有提示。
"""
import os
import re
import subprocess
import sys

# ==================== 1. 允许清单 ====================

# 只允许这些顶层条目进仓库。新增任何顶层目录/文件，都必须先改这里，等于强制过一道人工审核。
ALLOWED_TOP = {
    ".gitattributes",
    ".gitignore",
    ".githooks",           # 本检查脚本自身
    "README.md",
    "init.sql",
    "docs",
    "sql",
    "online_study_backend",
    "online_study_frontend",
}

# 文档类文件的允许范围。Markdown 是简历/笔记最常用的格式，所以一个字都不能松：
# 只有项目 README 和 docs 下的设计文档算正规文档，其余 .md 一律拦。
ALLOWED_MD = (
    re.compile(r"^README\.md$"),
    re.compile(r"^docs/[^/]+\.md$"),
)

# 二进制附件：只允许演示种子数据（前端演示时课件下载、批改面板要能点开）
ALLOWED_BINARY = re.compile(r"^online_study_backend/uploads/demo-[^/]+\.(png|jpg|jpeg|pdf)$")

# 代码/配置类扩展名白名单
ALLOWED_CODE_EXT = {
    ".java", ".kt", ".xml", ".yml", ".yaml", ".properties", ".sql", ".json",
    ".js", ".mjs", ".cjs", ".ts", ".vue", ".css", ".scss", ".html", ".htm",
    ".txt", ".ftl", ".md5", ".sh", ".cmd", ".bat", ".ps1", ".py", ".graphql",
}

# 硬黑名单：不管在哪，出现就拦
HARD_BLOCK_NAME = [
    (re.compile(r"(^|/)application\.yml$"), "live config (holds DB password / JWT secret / API key)"),
    (re.compile(r"(^|/)application-[^/]*\.yml$"), "profile config (may hold real credentials)"),
    (re.compile(r"(^|/)\.env(\..*)?$"), "env file"),
    (re.compile(r"\.(key|pem|p12|pfx|jks|keystore)$"), "certificate or private key"),
    (re.compile(r"(^|/)id_rsa(\.pub)?$"), "SSH key"),
    (re.compile(r"\.(log|class|jar|war)$"), "build output or log"),
]

# ==================== 2. 内容规则 ====================

# 命中即拦：真的密钥
BLOCK_CONTENT = [
    (re.compile(r"(sk-[A-Za-z0-9_.\-]{20,})"), "API key"),
    (re.compile(r"(AKIA[0-9A-Z]{16})"), "AWS access key"),
    (re.compile(r"(gh[pousr]_[A-Za-z0-9]{30,})"), "GitHub token"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "private key body"),
    (re.compile(r"(?i)(password|pwd|secret|passwd)\s*[:=]\s*[\"'][^\"'\s${}]{6,}[\"']"), "hardcoded password"),
    (re.compile(r"(?i)jwt[.\-_]?secret\s*[:=]\s*[\"']?[A-Za-z0-9+/=]{16,}"), "hardcoded JWT secret"),
]

# 只告警不拦：可能误报，但值得看一眼
WARN_CONTENT = [
    (re.compile(r"(?<![0-9])1[3-9][0-9]{9}(?![0-9])"), "phone-like number"),
    (re.compile(r"[A-Za-z0-9._%+-]+@(?!example\.(com|org)|test\.com)[A-Za-z0-9.-]+\.[A-Za-z]{2,}"), "email"),
    (re.compile(r"\b(192\.168\.|10\.[0-9]{1,3}\.|172\.(1[6-9]|2[0-9]|3[01])\.)"), "private network address"),
    (re.compile(r"[A-Za-z]:\\\\Users\\\\|[A-Za-z]:\\\\d-|[A-Za-z]:/Users/"), "absolute local path"),
    (re.compile(r"[\u4e00-\u9fa5]*(简历|面试|投递|秋招|春招|offer|薪资|期望城市|学号|身份证|挂科|绩点)[\u4e00-\u9fa5]*"), "personal / job-hunting wording"),
]

# docs 下的 Markdown 只要出现这些词就升级为拦截 —— 那里只该放设计文档
DOC_FORBIDDEN = re.compile(r"(简历|面试|投递|秋招|春招|期望薪资|学号|身份证|挂科|绩点|offer)")

# docs 下的文件名出现这些词，说明多半是个人笔记混进了设计文档目录
DOC_SUSPECT_NAME = re.compile(r"(?i)(临时|temp|草稿|draft|笔记|note|备忘|todo|个人)")

MAX_BYTES = 5 * 1024 * 1024


def git(*args):
    out = subprocess.run(["git"] + list(args), capture_output=True)
    return out.stdout.decode("utf-8", "replace")


def show(name):
    """文件名转 ascii 显示：中文名在 GBK 控制台会乱码。"""
    return ascii(name) if any(ord(c) > 127 for c in name) else name


def check_paths(paths, problems, warns):
    for f in paths:
        top = f.split("/")[0]

        # 1) 顶层不在允许清单 —— 这是最重要的一条
        if top not in ALLOWED_TOP:
            problems.append(
                "OUT-OF-WHITELIST  %s -- top-level '%s' is not in the allowlist; "
                "if it really belongs in the repo, add it to .githooks/redline_check.py ALLOWED_TOP"
                % (show(f), show(top)))
            continue

        # 2) 硬黑名单
        blocked = False
        for rule, why in HARD_BLOCK_NAME:
            if rule.search(f):
                problems.append("FORBIDDEN-NAME   %s -- %s" % (show(f), why))
                blocked = True
        if blocked:
            continue

        # 3) 文件类型：md / 二进制 / 代码
        if f.lower().endswith(".md"):
            if not any(r.search(f) for r in ALLOWED_MD):
                problems.append(
                    "DOC-OUT-OF-PLACE %s -- markdown is only allowed as README.md or docs/*.md" % show(f))
            continue

        if re.search(r"\.(png|jpg|jpeg|pdf|docx?|xlsx?|pptx?|zip|rar|7z|gif|bmp|webp)$", f, re.I):
            if not ALLOWED_BINARY.search(f):
                problems.append(
                    "BINARY-NOT-SEED  %s -- only uploads/demo-* seed files may be committed" % show(f))
            continue

        ext = os.path.splitext(f)[1].lower()
        if ext and ext not in ALLOWED_CODE_EXT:
            warns.append("odd-extension    %s -- extension '%s' is unusual for this repo" % (show(f), ext))


def check_content(diff_text, problems, warns):
    """只扫新增行（以 + 开头且不是 +++），避免历史遗留数据反复告警。

    跳过 .githooks/ 自身：这个文件里必然写着 "简历|面试|投递" 这些词（它们是规则的一部分），
    也必然带着 shebang 的绝对路径。不跳过的话，每次改检查脚本都会给自己报一堆误报，
    真正的问题就淹没在噪音里了。
    """
    current = ""
    for line in diff_text.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:]
            continue
        if current.startswith(".githooks/"):
            continue
        if not line.startswith("+") or line.startswith("+++"):
            continue
        for rule, why in BLOCK_CONTENT:
            m = rule.search(line)
            if m:
                hit = m.group(0)
                shown = hit[:12] + "..." if len(hit) > 12 else hit
                problems.append("SECRET           looks like a real %s: %s" % (why, shown))
        for rule, why in WARN_CONTENT:
            if rule.search(line):
                warns.append("pii-suspect      %s -> %s" % (why, line.strip()[:110]))


def check_doc_content(paths, problems, warns):
    """docs/*.md 单独查正文。

    为什么不能只靠"文件名允许"：docs/ 放设计文档是对的，但这个目录同样能塞进
    「面试答题稿.md」「个人简历-备份.md」—— 名字一改就绕过了所有路径规则。
    所以对正文再查一遍敏感词，命中直接拦，不给"改个名字就混过去"留口子。
    """
    for f in paths:
        if not f.lower().endswith(".md") or not f.startswith("docs/"):
            continue
        name = os.path.basename(f)
        if DOC_SUSPECT_NAME.search(name):
            warns.append("doc-name-suspect %s -- filename looks like a personal note" % show(f))
        try:
            with open(f, "r", encoding="utf-8", errors="replace") as fh:
                text = fh.read()
        except OSError:
            continue
        m = DOC_FORBIDDEN.search(text)
        if m:
            problems.append(
                "DOC-OFF-TOPIC    %s -- contains job-hunting / personal wording %s; "
                "docs/ is for project design docs only" % (show(f), ascii(m.group(0))))


def check_sizes(paths, problems):
    for f in paths:
        try:
            size = os.path.getsize(f)
        except OSError:
            continue
        if size > MAX_BYTES:
            problems.append("TOO-LARGE        %s -- %.1f MB (limit 5 MB)" % (show(f), size / 1048576.0))


def report(problems, warns, mode, count=0):
    if warns:
        sys.stderr.write("\n[%s] %d item(s) worth a look (not blocking):\n" % (mode, len(warns)))
        for w in warns[:20]:
            sys.stderr.write("  ~ %s\n" % w)
        if len(warns) > 20:
            sys.stderr.write("  ~ ... and %d more\n" % (len(warns) - 20))

    if problems:
        sys.stderr.write("\n[%s] BLOCKED: %d problem(s)\n" % (mode, len(problems)))
        for p in problems[:40]:
            sys.stderr.write("  - %s\n" % p)
        if len(problems) > 40:
            sys.stderr.write("  - ... and %d more\n" % (len(problems) - 40))
        sys.stderr.write(
            "\nThis is the red line: nothing goes to GitHub until every item above is explained\n"
            "and approved by the owner. Never bypass with --no-verify.\n\n")
        return 1

    sys.stderr.write("[%s] red-line check passed (%d file(s))\n" % (mode, count))
    return 0


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "staged"
    problems, warns = [], []
    count = 0

    if mode == "staged":
        paths = [f for f in git("diff", "--cached", "--name-only", "--diff-filter=ACM").splitlines() if f.strip()]
        if not paths:
            return 0
        check_paths(paths, problems, warns)
        check_doc_content(paths, problems, warns)
        check_sizes(paths, problems)
        check_content(git("diff", "--cached", "-U0", "--no-color"), problems, warns)
        return report(problems, warns, mode, len(paths))

    elif mode == "push":
        # git 通过 stdin 传入：<local ref> <local sha> <remote ref> <remote sha>
        lines = [l.split() for l in sys.stdin.read().splitlines() if l.strip()]
        paths = set()
        for parts in lines:
            if len(parts) < 4:
                continue
            local_sha, remote_sha = parts[1], parts[3]
            if set(remote_sha) == {"0"}:
                # 新分支：检查本地独有、远端还没有的全部提交
                out = git("log", "--name-only", "--pretty=format:", local_sha, "--not", "--remotes=origin")
            else:
                out = git("diff", "--name-only", "%s..%s" % (remote_sha, local_sha))
            paths.update(p for p in out.splitlines() if p.strip())

        paths = sorted(paths)
        if not paths:
            return 0
        count = len(paths)
        check_paths(paths, problems, warns)
        check_doc_content(paths, problems, warns)
        check_sizes(paths, problems)

        for parts in lines:
            if len(parts) < 4:
                continue
            local_sha, remote_sha = parts[1], parts[3]
            if set(remote_sha) == {"0"}:
                continue
            check_content(git("diff", "-U0", "--no-color", "%s..%s" % (remote_sha, local_sha)),
                          problems, warns)

    else:
        sys.stderr.write("unknown mode: %s\n" % mode)
        return 2

    return report(problems, warns, mode, count)


if __name__ == "__main__":
    sys.exit(main())
