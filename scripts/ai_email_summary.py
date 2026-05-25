#!/usr/bin/env python3
"""
Generates an HTML email summary from all AI-generated pipeline reports.
Output: ai-email-summary.html  (consumed by Jenkins emailext)

Required env vars (set by Jenkins):
  BUILD_NUMBER, BUILD_URL, GIT_COMMIT, GIT_BRANCH, IMAGE_NAME, BUILD_STATUS
"""

import os
import re
from datetime import datetime


def read_report(path, fallback="Report not generated for this build."):
    if os.path.exists(path):
        with open(path) as f:
            return f.read()
    return fallback


def extract_section(text, header):
    """Pull a single ## section from markdown."""
    pattern = rf"## {re.escape(header)}\n(.*?)(?=\n## |\Z)"
    m = re.search(pattern, text, re.DOTALL)
    return m.group(1).strip() if m else ""


def md_to_html(text):
    """Minimal markdown → HTML: bold, bullet lists, line breaks."""
    text = re.sub(r'\*\*(.*?)\*\*', r'<strong>\1</strong>', text)
    lines = text.split('\n')
    out, in_list = [], False
    for line in lines:
        stripped = line.lstrip()
        if stripped.startswith('- ') or stripped.startswith('* '):
            if not in_list:
                out.append('<ul>')
                in_list = True
            out.append(f'<li>{stripped[2:]}</li>')
        else:
            if in_list:
                out.append('</ul>')
                in_list = False
            if line.strip():
                out.append(f'<p>{line}</p>')
    if in_list:
        out.append('</ul>')
    return '\n'.join(out)


def risk_color(text):
    t = text.upper()
    if 'CRITICAL' in t: return '#dc3545'
    if 'HIGH'     in t: return '#fd7e14'
    if 'MEDIUM'   in t: return '#ffc107'
    if 'LOW'      in t: return '#28a745'
    return '#6c757d'


def main():
    build_number = os.environ.get('BUILD_NUMBER', 'local')
    build_url    = os.environ.get('BUILD_URL',    '#')
    git_commit   = os.environ.get('GIT_COMMIT',   'unknown')[:12]
    branch       = os.environ.get('GIT_BRANCH',   'unknown')
    image_name   = os.environ.get('IMAGE_NAME',   'N/A')
    status       = os.environ.get('BUILD_STATUS', 'UNKNOWN').upper()

    security_md   = read_report('ai-security-report.md')
    code_review_md = read_report('ai-code-review-report.md')
    release_md    = read_report('ai-release-notes.md')

    exec_summary   = extract_section(security_md,   'Executive Summary') or \
                     'Security analysis report was not generated.'
    risk_text      = extract_section(security_md,   'Overall Risk Level') or 'UNKNOWN'
    code_summary   = (extract_section(code_review_md, 'Summary') or
                      extract_section(code_review_md, 'Code Quality Issues') or
                      'Code review report was not generated.')
    whats_new      = extract_section(release_md, "What's New") or \
                     'Release notes were not generated.'
    sec_updates    = extract_section(release_md, 'Security & Compliance Updates') or ''

    rcolor = risk_color(risk_text)
    scolor = {'SUCCESS': '#28a745', 'FAILURE': '#dc3545',
              'UNSTABLE': '#fd7e14'}.get(status, '#6c757d')

    image_tag = image_name.split(':')[-1][:24] if ':' in image_name else image_name[:24]
    ts = datetime.utcnow().strftime('%Y-%m-%d %H:%M UTC')

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<style>
  body{{margin:0;padding:20px;background:#f4f4f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}}
  .wrap{{max-width:680px;margin:0 auto;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.08)}}
  .hdr{{background:#1a1a2e;color:#fff;padding:28px 32px}}
  .hdr h1{{margin:0 0 6px;font-size:20px}}
  .hdr p{{margin:0;opacity:.7;font-size:13px}}
  .badge{{display:inline-block;margin-top:14px;padding:4px 14px;border-radius:20px;font-size:12px;font-weight:700;background:{scolor};color:#fff}}
  .sec{{padding:20px 32px;border-bottom:1px solid #eef0f4}}
  .sec h2{{font-size:14px;font-weight:700;color:#333;margin:0 0 12px;display:flex;align-items:center;gap:7px}}
  .sec p,.sec li{{font-size:13px;color:#555;line-height:1.65;margin:4px 0}}
  .sec ul{{margin:6px 0;padding-left:18px}}
  .meta{{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:4px}}
  .card{{background:#f8f9fc;border-radius:6px;padding:10px 14px}}
  .card .lbl{{font-size:10px;text-transform:uppercase;letter-spacing:.6px;color:#999;margin-bottom:3px}}
  .card .val{{font-size:12px;font-weight:600;color:#333;font-family:monospace;word-break:break-all}}
  .risk{{display:inline-block;padding:5px 14px;border-radius:4px;font-weight:700;font-size:13px;color:#fff;background:{rcolor}}}
  .btn{{display:inline-block;padding:8px 18px;background:#1a1a2e;color:#fff;text-decoration:none;border-radius:6px;font-size:12px;margin:4px 4px 4px 0}}
  .ftr{{background:#f8f9fc;padding:14px 32px;text-align:center;font-size:11px;color:#aaa}}
</style>
</head>
<body>
<div class="wrap">

  <div class="hdr">
    <h1>AI DevSecOps Pipeline Report</h1>
    <p>Build #{build_number} &nbsp;•&nbsp; {ts}</p>
    <div class="badge">{status}</div>
  </div>

  <div class="sec">
    <h2>&#128269; Build Information</h2>
    <div class="meta">
      <div class="card"><div class="lbl">Branch</div><div class="val">{branch}</div></div>
      <div class="card"><div class="lbl">Commit</div><div class="val">{git_commit}</div></div>
      <div class="card"><div class="lbl">Build #</div><div class="val">{build_number}</div></div>
      <div class="card"><div class="lbl">Image Tag</div><div class="val">{image_tag}</div></div>
    </div>
  </div>

  <div class="sec">
    <h2>&#128737;&#65039; Security Overview</h2>
    {md_to_html(exec_summary[:800])}
    <p style="margin-top:10px"><strong>Overall Risk Level: </strong>
      <span class="risk">{risk_text[:60]}</span></p>
  </div>

  <div class="sec">
    <h2>&#128104;&#8205;&#128187; Code Review Highlights</h2>
    {md_to_html(code_summary[:600])}
  </div>

  <div class="sec">
    <h2>&#128640; What's New in This Release</h2>
    {md_to_html(whats_new[:600])}
    {('<h2 style="font-size:14px;margin-top:14px">&#128274; Security &amp; Compliance Updates</h2>' + md_to_html(sec_updates[:400])) if sec_updates else ''}
  </div>

  <div class="sec">
    <h2>&#128206; Full AI Reports (attached)</h2>
    <p>Complete reports are attached to this email and available in Jenkins:</p>
    <a class="btn" href="{build_url}artifact/ai-security-report.md">Security Report</a>
    <a class="btn" href="{build_url}artifact/ai-code-review-report.md">Code Review</a>
    <a class="btn" href="{build_url}artifact/ai-release-notes.md">Release Notes</a>
    <a class="btn" href="{build_url}">Jenkins Build</a>
  </div>

  <div class="ftr">
    Generated by AI DevSecOps Pipeline &nbsp;•&nbsp; Powered by Mistral / Ollama
  </div>

</div>
</body>
</html>"""

    out = 'ai-email-summary.html'
    with open(out, 'w') as f:
        f.write(html)
    print(f"[AI Email] HTML summary saved → {out}")


if __name__ == '__main__':
    main()
