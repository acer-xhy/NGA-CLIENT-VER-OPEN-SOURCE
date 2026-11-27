#!/usr/bin/env python3
"""把 git diff 渲染成单文件 HTML 对照报告，无第三方依赖。

用法：
  python tools/git-diff-html.py                          # 工作区 + 暂存 + 未跟踪（对比 HEAD）
  python tools/git-diff-html.py --staged                 # 只看已暂存
  python tools/git-diff-html.py --range HEAD~1..HEAD     # 指定提交范围
  python tools/git-diff-html.py --out 变更对照_20261127_1430.html

设计目标：给不愿常开 IDE 的人用。生成一个独立的 .html，浏览器打开即可
看文件列表、逐文件左右行号、增删着色，还能一键隐藏上下文行。
"""

import argparse
import datetime as _dt
import html
import re
import subprocess
import sys
from pathlib import Path

MAX_FILE_BYTES = 1_500_000
MAX_FILE_LINES = 3000
CONTEXT_COLLAPSE_LINES = 400

HUNK_RE = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$")
DIFF_GIT_RE = re.compile(r"^diff --git a/(.+?) b/(.+)$")


def run_git(repo: str, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", repo, "-c", "core.quotepath=false", *args],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    return result.stdout


def repo_root(path: str) -> str:
    return run_git(path, "rev-parse", "--show-toplevel").strip()


def build_diff(repo: str, range_spec: str, staged: bool) -> str:
    if range_spec:
        return run_git(repo, "diff", "--no-color", "--no-ext-diff", "-M", range_spec)
    if staged:
        return run_git(repo, "diff", "--no-color", "--no-ext-diff", "-M", "--cached", "HEAD")
    return run_git(repo, "diff", "--no-color", "--no-ext-diff", "-M", "HEAD")


def untracked_patch(repo: str) -> str:
    """手工构造未跟踪文件的 diff，避免 git diff --no-index 在 Windows 上的兼容问题。"""
    listing = run_git(repo, "ls-files", "--others", "--exclude-standard")
    chunks = []
    for rel in listing.splitlines():
        rel = rel.strip()
        if not rel:
            continue
        path = Path(repo) / rel
        try:
            size = path.stat().st_size
        except OSError:
            continue
        head = [f"diff --git a/{rel} b/{rel}", "new file mode 100644", "--- /dev/null", f"+++ b/{rel}"]
        if size > MAX_FILE_BYTES:
            chunks.append("\n".join(head + [f"Binary files /dev/null and b/{rel} differ（文件 {size} 字节，超出展示上限）"]))
            continue
        try:
            data = path.read_bytes()
        except OSError:
            continue
        if b"\x00" in data[:1024]:
            chunks.append("\n".join(head + [f"Binary files /dev/null and b/{rel} differ"]))
            continue
        text = data.decode("utf-8", errors="replace")
        lines = text.splitlines()
        truncated = len(lines) > MAX_FILE_LINES
        if truncated:
            lines = lines[:MAX_FILE_LINES]
        chunks.append("\n".join(head + [f"@@ -0,0 +1,{max(len(lines), 0)} @@"] + [f"+{ln}" for ln in lines]))
        if truncated:
            chunks.append("\\ 文件超过 %d 行，仅显示前 %d 行" % (MAX_FILE_LINES, MAX_FILE_LINES))
    return "\n".join(chunks)


def parse_diff(diff_text: str):
    files = []
    current = None
    hunk = None
    old_no = new_no = 0

    for line in diff_text.splitlines():
        if line.startswith("diff --git "):
            m = DIFF_GIT_RE.match(line)
            current = {
                "old": m.group(1) if m else "?",
                "new": m.group(2) if m else "?",
                "hunks": [],
                "binary": None,
                "added": 0,
                "removed": 0,
            }
            files.append(current)
            hunk = None
            continue
        if current is None:
            continue
        if line.startswith("--- "):
            p = line[4:].strip()
            if p != "/dev/null" and p.startswith("a/"):
                p = p[2:]
            current["old"] = p
            continue
        if line.startswith("+++ "):
            p = line[4:].strip()
            if p != "/dev/null" and p.startswith("b/"):
                p = p[2:]
            current["new"] = p
            continue
        if line.startswith("Binary files "):
            current["binary"] = line
            continue
        m = HUNK_RE.match(line)
        if m:
            old_no = int(m.group(1))
            new_no = int(m.group(3))
            hunk = {"header": line, "lines": []}
            current["hunks"].append(hunk)
            continue
        if hunk is None:
            continue
        if line.startswith("\\"):
            hunk["lines"].append(("note", None, None, line[1:].strip()))
            continue
        if line[:1] in (" ", "+", "-"):
            kind = {" ": "ctx", "+": "add", "-": "del"}[line[:1]]
            content = line[1:]
            if kind == "add":
                current["added"] += 1
                hunk["lines"].append((kind, None, new_no, content))
                new_no += 1
            elif kind == "del":
                current["removed"] += 1
                hunk["lines"].append((kind, old_no, None, content))
                old_no += 1
            else:
                hunk["lines"].append((kind, old_no, new_no, content))
                old_no += 1
                new_no += 1
    return files


CSS = """
:root{color-scheme:dark}
*{box-sizing:border-box}
body{margin:0;background:#1b1b1f;color:#d6d6d6;font:13px/1.55 "Cascadia Mono","Consolas","Menlo",monospace}
header{position:sticky;top:0;z-index:20;background:#232329;border-bottom:1px solid #333;padding:14px 20px}
h1{margin:0 0 4px;font-size:16px;font-weight:600;letter-spacing:.3px}
.meta{color:#8b8b93;font-size:12px}
.meta b{color:#c8c8cf;font-weight:500}
.summary{margin-top:8px;display:flex;gap:16px;align-items:center;flex-wrap:wrap;font-size:12px}
.tot-add{color:#7ddc7d}.tot-del{color:#ff8b8b}.tot-files{color:#b9b9c2}
.switch{display:flex;align-items:center;gap:6px;color:#b9b9c2;cursor:pointer;user-select:none}
main{display:grid;grid-template-columns:330px minmax(0,1fr);gap:0;min-height:calc(100vh - 96px)}
nav{border-right:1px solid #2e2e35;padding:10px 8px;position:sticky;top:96px;align-self:start;max-height:calc(100vh - 96px);overflow:auto}
nav a{display:flex;justify-content:space-between;gap:8px;padding:5px 8px;border-radius:5px;color:#c2c2cb;text-decoration:none;font-size:12px}
nav a:hover{background:#2b2b33;color:#fff}
nav .n-add{color:#7ddc7d}nav .n-del{color:#ff8b8b}
section{padding:18px 20px 30px;min-width:0}
details.file{border:1px solid #2e2e35;border-radius:8px;margin-bottom:16px;background:#1f1f25;overflow:hidden}
details.file>summary{cursor:pointer;padding:9px 12px;background:#26262e;display:flex;justify-content:space-between;gap:12px;align-items:center;font-size:12.5px;list-style:none}
details.file>summary::-webkit-details-marker{display:none}
details.file>summary .path{color:#e2e2ea;word-break:break-all}
details.file>summary .cnt{white-space:nowrap;font-size:11.5px}
.tag{display:inline-block;margin-left:8px;padding:0 6px;border-radius:4px;font-size:10.5px;background:#3a2c1f;color:#e0b47c}
table.diff{width:100%;border-collapse:collapse;font-size:12.5px;table-layout:fixed}
table.diff td{padding:0 8px;vertical-align:top}
td.ln{width:52px;text-align:right;color:#66666e;user-select:none;background:#1c1c22}
td.sign{width:16px;user-select:none;text-align:center}
td.code{white-space:pre-wrap;word-break:break-all}
tr.add td.code{background:#17301a}tr.add td.sign{color:#7ddc7d;background:#17301a}
tr.del td.code{background:#341b1b}tr.del td.sign{color:#ff8b8b;background:#341b1b}
tr.add td.ln,tr.del td.ln{background:#20202a}
tr.hunk td{background:#1d2733;color:#7fb4e8;padding:5px 8px;border-top:1px solid #2b3846;border-bottom:1px solid #2b3846}
tr.note td{color:#8b8b93;font-style:italic}
tr.binary td{padding:10px 12px;color:#e0b47c;background:#241f18}
body.hide-ctx tr.ctx{display:none}
.empty{padding:40px;color:#8b8b93;text-align:center}
@media (max-width:900px){main{grid-template-columns:1fr}nav{position:static;max-height:none;border-right:0;border-bottom:1px solid #2e2e35}}
"""

JS = """
document.getElementById('hideCtx').addEventListener('change', e => {
  document.body.classList.toggle('hide-ctx', e.target.checked);
});
"""


def short_path(old: str, new: str) -> str:
    if old == "/dev/null" or not old:
        return new
    if new == "/dev/null" or not new:
        return old
    return new if old == new else f"{old} → {new}"


def signature(f: dict) -> str:
    return f"{f['old']}|{f['new']}"


def render(files, meta) -> str:
    total_add = sum(f["added"] for f in files)
    total_del = sum(f["removed"] for f in files)

    nav = []
    body = []
    for idx, f in enumerate(files):
        label = html.escape(short_path(f["old"], f["new"]))
        counts = []
        if f["added"]:
            counts.append(f'<span class="n-add">+{f["added"]}</span>')
        if f["removed"]:
            counts.append(f'<span class="n-del">−{f["removed"]}</span>')
        nav.append(f'<a href="#f{idx}"><span>{label}</span><span>{" ".join(counts)}</span></a>')

        tags = []
        if f["old"] == "/dev/null":
            tags.append('<span class="tag">新增</span>')
        elif f["new"] == "/dev/null":
            tags.append('<span class="tag">删除</span>')
        elif f["old"] != f["new"]:
            tags.append('<span class="tag">重命名</span>')

        rows = []
        if f["binary"]:
            note = html.escape(f["binary"])
            rows.append(f'<tr class="binary"><td colspan="3">{note}</td></tr>')
        for hunk in f["hunks"]:
            rows.append(f'<tr class="hunk"><td colspan="3">{html.escape(hunk["header"])}</td></tr>')
            for kind, old_no, new_no, content in hunk["lines"]:
                cls = {"ctx": "ctx", "add": "add", "del": "del", "note": "note"}[kind]
                sign = {"ctx": " ", "add": "+", "del": "-", "note": ""}[kind]
                rows.append(
                    '<tr class="%s"><td class="ln">%s</td><td class="ln">%s</td>'
                    '<td class="sign">%s</td><td class="code">%s</td></tr>'
                    % (cls,
                       "" if old_no is None else old_no,
                       "" if new_no is None else new_no,
                       sign,
                       html.escape(content))
                )
        if not rows:
            rows.append('<tr class="binary"><td colspan="3">（没有可显示的行）</td></tr>')

        body.append(
            f'<details class="file" id="f{idx}" open><summary>'
            f'<span class="path">{label}{"".join(tags)}</span>'
            f'<span class="cnt"><span class="n-add">+{f["added"]}</span> '
            f'<span class="n-del">−{f["removed"]}</span></span>'
            f'</summary><table class="diff">{"".join(rows)}</table></details>'
        )

    if not files:
        body.append('<div class="empty">没有检测到任何改动。</div>')

    return f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>变更对照 · {html.escape(meta['name'])}</title>
<style>{CSS}</style></head>
<body>
<header>
  <h1>变更对照 · {html.escape(meta['name'])}</h1>
  <div class="meta">{html.escape(meta['desc'])}　生成于 {html.escape(meta['time'])}</div>
  <div class="summary">
    <span class="tot-files">{len(files)} 个文件</span>
    <span class="tot-add">+{total_add}</span>
    <span class="tot-del">−{total_del}</span>
    <label class="switch"><input type="checkbox" id="hideCtx"> 只看增删行</label>
  </div>
</header>
<main>
  <nav>{''.join(nav) or '<div class="meta">（无）</div>'}</nav>
  <section>{''.join(body)}</section>
</main>
<script>{JS}</script>
</body></html>
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="git diff → HTML 对照报告")
    parser.add_argument("--repo", default=".", help="仓库路径，默认当前目录")
    parser.add_argument("--range", default="", help="提交范围，如 HEAD~1..HEAD")
    parser.add_argument("--staged", action="store_true", help="只看已暂存")
    parser.add_argument("--out", default="", help="输出文件，默认仓库根目录 变更对照_YYYYMMDD_HHMM.html")
    args = parser.parse_args()

    root = repo_root(args.repo)
    if not root:
        print("不在 git 仓库内", file=sys.stderr)
        return 2

    diff_text = build_diff(root, args.range, args.staged)
    if not args.range and not args.staged:
        diff_text += "\n" + untracked_patch(root)

    files = parse_diff(diff_text)
    now = _dt.datetime.now()
    if args.range:
        desc = f"提交范围 {args.range}"
    elif args.staged:
        desc = "已暂存的改动"
    else:
        desc = "工作区相对 HEAD（含暂存与未跟踪）"

    out = args.out or f"变更对照_{now:%Y%m%d_%H%M}.html"
    out_path = Path(out)
    if not out_path.is_absolute():
        out_path = Path(root) / out_path
    out_path.write_text(render(files, {"name": Path(root).name, "desc": desc, "time": f"{now:%Y-%m-%d %H:%M}"}),
                        encoding="utf-8")

    add = sum(f["added"] for f in files)
    rem = sum(f["removed"] for f in files)
    print(f"报告: {out_path}")
    print(f"文件 {len(files)} 个，+{add} / −{rem}")
    for f in files:
        print(f"  {short_path(f['old'], f['new'])}  +{f['added']} −{f['removed']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
