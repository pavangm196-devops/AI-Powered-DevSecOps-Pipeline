#!/usr/bin/env python3
"""
AI Pipeline Chat Server
Serves a web chat interface where developers can ask questions about pipeline reports.
Uses the LLM provider chain from ai_utils.py (Ollama → HuggingFace → OpenAI).

Usage:
  python3 scripts/chat_server.py              # port 5001 (default)
  PORT=8080 python3 scripts/chat_server.py    # custom port
  REPORTS_DIR=/path/to/reports python3 scripts/chat_server.py

Open http://localhost:5001 in your browser.
POST /chat  {"question": "..."} → {"answer": "..."}
GET  /reload                    → reloads reports from disk
"""

import os
import sys
import json
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ai_utils import ask_llm

PORT        = int(os.environ.get('CHAT_PORT',   5001))
REPORTS_DIR = os.environ.get('REPORTS_DIR', '.')


# ── Report loading ────────────────────────────────────────────────────────────

def load_reports():
    files = {
        'security':     'ai-security-report.md',
        'code_review':  'ai-code-review-report.md',
        'release_notes':'ai-release-notes.md',
    }
    reports = {}
    for key, name in files.items():
        path = os.path.join(REPORTS_DIR, name)
        if os.path.exists(path):
            with open(path) as f:
                reports[key] = f.read()
            print(f"[Chat] Loaded {name}")
        else:
            reports[key] = f'[{name} not found — run the pipeline first]'
            print(f"[Chat] Warning: {name} not found")
    return reports


def build_context(reports):
    return (
        "You are an AI DevSecOps assistant with access to the latest pipeline reports below.\n"
        "Answer questions concisely and accurately based only on the provided reports.\n"
        "If asked about something not in the reports, say so clearly.\n\n"
        "=== SECURITY REPORT ===\n"
        + reports['security'][:2500]
        + "\n\n=== CODE REVIEW REPORT ===\n"
        + reports['code_review'][:1800]
        + "\n\n=== RELEASE NOTES ===\n"
        + reports['release_notes'][:1200]
    )


# ── Embedded chat UI ──────────────────────────────────────────────────────────

CHAT_HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AI Pipeline Assistant</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
       background:#0f0f1a;color:#e0e0e0;height:100vh;display:flex;flex-direction:column}

  .topbar{background:#1a1a2e;padding:14px 24px;display:flex;align-items:center;
          gap:12px;border-bottom:1px solid #2a2a40;flex-shrink:0}
  .logo{width:38px;height:38px;border-radius:9px;display:flex;align-items:center;
        justify-content:center;font-size:20px;
        background:linear-gradient(135deg,#667eea,#764ba2)}
  .topbar h1{font-size:17px;font-weight:600}
  .topbar small{font-size:11px;color:#777;display:block;margin-top:2px}
  .online{margin-left:auto;display:flex;align-items:center;gap:6px;font-size:12px;color:#4caf50}
  .dot{width:7px;height:7px;border-radius:50%;background:#4caf50;
       animation:pulse 2s ease-in-out infinite}
  @keyframes pulse{0%,100%{opacity:1}50%{opacity:.3}}

  .chips{background:#16213e;padding:9px 24px;display:flex;gap:8px;
         border-bottom:1px solid #2a2a40;overflow-x:auto;flex-shrink:0}
  .chips span{font-size:11px;color:#666;line-height:26px;white-space:nowrap}
  .chip{padding:4px 13px;border-radius:20px;font-size:12px;border:1px solid #2a3a4a;
        cursor:pointer;white-space:nowrap;transition:all .18s;color:#aaa}
  .chip:hover{border-color:#667eea;color:#667eea;background:#1e2a4a}

  .chat{flex:1;overflow-y:auto;padding:20px 24px;display:flex;flex-direction:column;gap:14px}
  .msg{display:flex;gap:10px;max-width:820px}
  .msg.user{flex-direction:row-reverse;align-self:flex-end}
  .av{width:30px;height:30px;border-radius:50%;display:flex;align-items:center;
      justify-content:center;font-size:13px;flex-shrink:0}
  .av.ai{background:linear-gradient(135deg,#667eea,#764ba2)}
  .av.usr{background:#2a2a3a}
  .bbl{padding:11px 15px;border-radius:12px;font-size:13px;line-height:1.65;max-width:680px}
  .msg.ai  .bbl{background:#1e2a3a;border:1px solid #2a3a4a;border-top-left-radius:3px}
  .msg.user .bbl{background:#667eea;color:#fff;border-top-right-radius:3px}
  .bbl pre{background:#0f0f1a;border:1px solid #333;padding:10px;border-radius:6px;
           overflow-x:auto;font-size:12px;margin:8px 0}
  .bbl code{background:#0f0f1a;padding:1px 5px;border-radius:3px;font-size:12px}
  .bbl strong{color:#fff}
  .bbl ul{margin:6px 0;padding-left:18px}
  .bbl li{margin:3px 0}

  .typing{display:flex;gap:5px;align-items:center;padding:11px 15px}
  .tdot{width:6px;height:6px;border-radius:50%;background:#667eea;
        animation:bounce 1.2s ease-in-out infinite}
  .tdot:nth-child(2){animation-delay:.2s}
  .tdot:nth-child(3){animation-delay:.4s}
  @keyframes bounce{0%,80%,100%{transform:translateY(0)}40%{transform:translateY(-7px)}}

  .bottom{padding:14px 24px;background:#1a1a2e;border-top:1px solid #2a2a40;flex-shrink:0}
  .sugg{display:flex;gap:7px;flex-wrap:wrap;max-width:820px;margin:0 auto 10px}
  .sugg button{padding:5px 12px;background:#1e2a3a;border:1px solid #2a3a4a;
               border-radius:20px;font-size:11px;cursor:pointer;color:#aaa;
               transition:all .18s}
  .sugg button:hover{border-color:#667eea;color:#667eea}
  .row{display:flex;gap:9px;max-width:820px;margin:0 auto}
  .row input{flex:1;background:#0f0f1a;border:1px solid #2a3a4a;color:#e0e0e0;
             padding:11px 15px;border-radius:8px;font-size:13px;outline:none;
             transition:border-color .18s}
  .row input:focus{border-color:#667eea}
  .row input::placeholder{color:#444}
  .send{background:linear-gradient(135deg,#667eea,#764ba2);border:none;color:#fff;
        padding:11px 18px;border-radius:8px;cursor:pointer;font-size:15px;
        transition:opacity .18s}
  .send:hover{opacity:.85}
  .send:disabled{opacity:.4;cursor:not-allowed}
</style>
</head>
<body>

<div class="topbar">
  <div class="logo">&#129302;</div>
  <div>
    <h1>AI Pipeline Assistant</h1>
    <small>Ask questions about your latest build reports</small>
  </div>
  <div class="online"><div class="dot"></div> Online</div>
</div>

<div class="chips">
  <span>Jump to:</span>
  <div class="chip" onclick="ask('Summarise the security findings in this build.')">&#128737; Security</div>
  <div class="chip" onclick="ask('What did the code review find?')">&#128104;&#8205;&#128187; Code Review</div>
  <div class="chip" onclick="ask('What changed in this release?')">&#128640; Release Notes</div>
  <div class="chip" onclick="ask('What is the overall risk level and should we deploy?')">&#9888; Risk Assessment</div>
  <div class="chip" onclick="ask('List all critical and high vulnerabilities with CVE IDs.')">&#128272; CVEs</div>
</div>

<div class="chat" id="chat">
  <div class="msg ai">
    <div class="av ai">&#129302;</div>
    <div class="bbl">
      Hello! I have access to your latest pipeline reports:<br><br>
      &bull; &#128737; <strong>Security Report</strong> — CVE findings, risk level, remediation roadmap<br>
      &bull; &#128104;&#8205;&#128187; <strong>Code Review</strong> — Code quality and security issues<br>
      &bull; &#128640; <strong>Release Notes</strong> — What changed in this build<br><br>
      Ask me anything about your pipeline results.
    </div>
  </div>
</div>

<div class="bottom">
  <div class="sugg">
    <button onclick="ask(this.textContent)">Is it safe to deploy?</button>
    <button onclick="ask(this.textContent)">What are the critical vulnerabilities?</button>
    <button onclick="ask(this.textContent)">What should I fix first?</button>
    <button onclick="ask(this.textContent)">Give me the remediation roadmap.</button>
    <button onclick="ask(this.textContent)">What new features were added?</button>
  </div>
  <div class="row">
    <input id="inp" placeholder="Ask about your pipeline reports…"
           onkeydown="if(event.key==='Enter')send()">
    <button class="send" id="btn" onclick="send()">&#9658;</button>
  </div>
</div>

<script>
function esc(t){return t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}

function fmt(t){
  t=t.replace(/```([\s\S]*?)```/g,'<pre>$1</pre>');
  t=t.replace(/`([^`]+)`/g,'<code>$1</code>');
  t=t.replace(/\*\*(.*?)\*\*/g,'<strong>$1</strong>');
  t=t.replace(/^[-*] (.+)$/gm,'<li>$1</li>');
  t=t.replace(/(<li>.*<\/li>)/gs,'<ul>$1</ul>');
  t=t.replace(/\n/g,'<br>');
  return t;
}

function ask(q){document.getElementById('inp').value=q;send();}

async function send(){
  const inp=document.getElementById('inp');
  const btn=document.getElementById('btn');
  const chat=document.getElementById('chat');
  const q=inp.value.trim();
  if(!q)return;

  chat.innerHTML+=`<div class="msg user"><div class="av usr">&#128100;</div>
    <div class="bbl">${esc(q)}</div></div>`;
  inp.value='';btn.disabled=true;

  const tid='t'+Date.now();
  chat.innerHTML+=`<div class="msg ai" id="${tid}">
    <div class="av ai">&#129302;</div>
    <div class="bbl typing">
      <div class="tdot"></div><div class="tdot"></div><div class="tdot"></div>
    </div></div>`;
  chat.scrollTop=chat.scrollHeight;

  try{
    const r=await fetch('/chat',{method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({question:q})});
    const d=await r.json();
    document.getElementById(tid).remove();
    chat.innerHTML+=`<div class="msg ai"><div class="av ai">&#129302;</div>
      <div class="bbl">${fmt(d.answer||d.error)}</div></div>`;
  }catch(e){
    document.getElementById(tid).remove();
    chat.innerHTML+=`<div class="msg ai"><div class="av ai">&#129302;</div>
      <div class="bbl" style="color:#ff6b6b">
        Connection error — is the LLM provider running?<br>
        Start Ollama: <code>ollama serve</code>
      </div></div>`;
  }
  btn.disabled=false;
  chat.scrollTop=chat.scrollHeight;
}
</script>
</body>
</html>"""


# ── HTTP request handler ──────────────────────────────────────────────────────

class Handler(BaseHTTPRequestHandler):
    _ctx   = None
    _lock  = threading.Lock()

    def log_message(self, fmt, *args):
        print(f"[Chat] {self.address_string()} {fmt % args}")

    def _json(self, data, status=200):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path in ('/', '/chat'):
            body = CHAT_HTML.encode()
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif self.path == '/health':
            self._json({'status': 'ok', 'time': datetime.now().isoformat()})
        elif self.path == '/reload':
            with Handler._lock:
                Handler._ctx = None
            self._json({'status': 'reports reloaded from disk'})
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path != '/chat':
            self.send_response(404)
            self.end_headers()
            return

        length = int(self.headers.get('Content-Length', 0))
        try:
            payload  = json.loads(self.rfile.read(length).decode())
            question = payload.get('question', '').strip()
            if not question:
                self._json({'error': 'question is required'}, 400)
                return

            with Handler._lock:
                if Handler._ctx is None:
                    Handler._ctx = build_context(load_reports())
            ctx = Handler._ctx

            prompt = f"{ctx}\n\nQuestion: {question}\n\nAnswer (be concise):"
            answer = ask_llm(prompt)
            self._json({'answer': answer})

        except json.JSONDecodeError:
            self._json({'error': 'invalid JSON body'}, 400)
        except Exception as e:
            self._json({'error': str(e)}, 500)


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    print(f"[Chat] Starting AI Pipeline Chat Server")
    print(f"[Chat] Reports directory : {os.path.abspath(REPORTS_DIR)}")
    print(f"[Chat] Open in browser   : http://localhost:{PORT}")
    print(f"[Chat] Chat API endpoint : POST http://localhost:{PORT}/chat")
    print(f"[Chat] Reload reports    : GET  http://localhost:{PORT}/reload")
    print(f"[Chat] Press Ctrl+C to stop\n")

    server = HTTPServer(('0.0.0.0', PORT), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print('\n[Chat] Server stopped.')


if __name__ == '__main__':
    main()
