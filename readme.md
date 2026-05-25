# AI-Powered DevSecOps Pipeline

> A full AI-augmented DevSecOps CI/CD pipeline built on Jenkins, deployed to AWS EKS — integrating 3 layers of LLM-based automation (code review, security analysis, release notes) with OPA policy-as-code, Trivy, SonarQube, OWASP scanning, and HTTPS via Let's Encrypt.

---

> **Note:** The application layer (Spring Boot GameApp) is adapted from [ManojKRISHNAPPA/SnakeGame](https://github.com/ManojKRISHNAPPA/SnakeGame). My contributions cover the entire DevSecOps and infrastructure layer: the AI pipeline scripts, OPA security policies, Kubernetes deployment manifests, Jenkins pipeline configuration, and the AWS EKS + HTTPS setup documented below.

---

## Tech Stack

![Jenkins](https://img.shields.io/badge/CI%2FCD-Jenkins-red?logo=jenkins)
![AWS](https://img.shields.io/badge/Cloud-AWS%20EKS-orange?logo=amazon-aws)
![Kubernetes](https://img.shields.io/badge/Orchestration-Kubernetes-blue?logo=kubernetes)
![Docker](https://img.shields.io/badge/Container-Docker-blue?logo=docker)
![SonarQube](https://img.shields.io/badge/SAST-SonarQube-4E9BCD?logo=sonarqube)
![Trivy](https://img.shields.io/badge/Scan-Trivy-lightgrey)
![OPA](https://img.shields.io/badge/Policy-OPA%2FConftest-blueviolet)
![Python](https://img.shields.io/badge/AI%20Scripts-Python-3776AB?logo=python)
![Ollama](https://img.shields.io/badge/LLM-Ollama%20Mistral-black)

---

## What Problem This Solves

Traditional DevSecOps pipelines produce raw CVE reports and scan logs that engineers have to manually read and interpret — this takes time and critical findings get missed. This pipeline solves that by adding an AI layer that automatically translates raw security scan output into plain-English executive reports, reviews code diffs for security issues before tests run, and generates professional release notes from commit history — all without blocking the pipeline if the LLM is unavailable.

---

## Pipeline Architecture

```
Git Push
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│                        JENKINS PIPELINE                         │
│                                                                 │
│  Git Checkout → Compile                                         │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────┐                            │
│  │  AI LAYER 1 — Code Review       │  ← reads git diff          │
│  │  Catches security bugs, bad     │     asks LLM to review     │
│  │  code patterns before tests run │     saves report.md        │
│  └─────────────────────────────────┘                            │
│       │                                                         │
│       ▼                                                         │
│  Test & Coverage (JaCoCo) → SonarQube → Quality Gate           │
│       │                                                         │
│       ▼                                                         │
│  OWASP Dependency Scan (CVE check on all Maven deps)            │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────────────┐  ┌──────────────┐  ┌────────────────┐    │
│  │  Trivy Image Scan│  │ OPA Conftest │  │  Docker Build  │    │
│  │  (base image CVE)│  │ (policy lint)│  │                │    │
│  └──────────────────┘  └──────────────┘  └────────────────┘    │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────┐                            │
│  │  AI LAYER 2 — Security Analysis │  ← reads OWASP JSON        │
│  │  Translates raw CVEs to plain   │     + trivy-report.txt     │
│  │  English executive report       │     asks LLM to summarise  │
│  └─────────────────────────────────┘                            │
│       │                                                         │
│       ▼                                                         │
│  Docker Push → Update Kubeconfig → OPA K8s Manifest Check      │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────┐                            │
│  │  AI LAYER 3 — Release Notes     │  ← reads git log           │
│  │  Generates deployment docs from │     asks LLM to write      │
│  │  commit history automatically   │     professional notes     │
│  └─────────────────────────────────┘                            │
│       │                                                         │
│       ▼                                                         │
│  Deploy to EKS (kubectl apply)                                  │
└─────────────────────────────────────────────────────────────────┘
```

> All AI stages use `catchError(UNSTABLE)` — if the LLM is unreachable the pipeline continues and deploys. AI failures never block CI/CD.

---

## My Contributions — What I Built

### 1. AI Layer Scripts (`scripts/`)

**`scripts/ai_utils.py`** — Shared LLM client with 3-provider fallback chain:
```
Ollama (Mistral local)  →  HuggingFace free API  →  OpenAI GPT-3.5
```
Checks Ollama's `/api/tags` endpoint before each call — auto-pulls the model if not cached. Subsequent runs skip the download entirely.

**`scripts/ai_code_review.py`** — AI Layer 1
- Runs `git diff HEAD~1 HEAD` to extract changed files
- Sends diff to LLM with a security-focused review prompt
- Saves `ai-code-review-report.md` as a Jenkins artifact
- Catches: hardcoded secrets, SQL injection patterns, insecure session handling

**`scripts/ai_security_analysis.py`** — AI Layer 2
- Parses `target/dependency-check-report.json` — extracts all CVEs with CVSS scores
- Reads `trivy-report.txt` — container image vulnerabilities
- Sorts by severity, takes top 15 critical findings
- Sends to LLM: produces an executive security report with remediation roadmap

**`scripts/ai_release_notes.py`** — AI Layer 3
- Gets commits since last git tag (or last 20 commits)
- Gets list of files changed in last 5 commits
- Sends to LLM: produces professional release notes per build automatically

---

### 2. OPA Policy-as-Code

**`dockerfile-security.rego`** — Dockerfile security policy:
- Enforces non-root user
- Blocks hardcoded secrets
- Validates base image requirements

**`opa-k8s-security.rego`** — Kubernetes manifest policy:
- Enforces resource limits on all containers
- Validates security contexts
- Blocks privileged containers

Both policies run via `openpolicyagent/conftest` Docker container — no host installation needed.

---

### 3. Kubernetes Deployment (`deployment.yaml`)

Full deployment stack for AWS EKS:

```
Internet (HTTPS :443)
    │
    ▼
AWS Load Balancer
    │
    ▼
NGINX Ingress Controller ←── cert-manager (Let's Encrypt TLS)
    │
    ▼
ClusterIP Service
    │
    ▼
Deployment (3 replicas) → Spring Boot Pod :8080
```

Resources configured:
- `Deployment` — 3 replicas with resource limits and security context
- `Service` — ClusterIP for internal routing
- `Ingress` — HTTPS termination + HTTP→HTTPS redirect
- `ClusterIssuer` — ACME http01 challenge for Let's Encrypt certificate

---

### 4. Jenkins Pipeline (`Jenkinsfile-devsecops`)

Full pipeline stage configuration including:

| Stage | Tool | Fails Build? |
|---|---|---|
| Compile | Maven | Yes |
| AI Code Review | Ollama / HF / OpenAI | No (UNSTABLE) |
| Test & Coverage | JUnit + JaCoCo | Yes |
| SonarQube Analysis | SonarQube | Yes (Quality Gate) |
| OWASP Dependency Scan | dependency-check 12.x | UNSTABLE (CVSS ≥ 7.0) |
| Trivy Image Scan | Trivy | UNSTABLE (CRITICAL) |
| OPA Dockerfile Policy | Conftest | Yes |
| Docker Build & Push | Docker | Yes |
| AI Security Analysis | Ollama / HF / OpenAI | No (UNSTABLE) |
| OPA K8s Manifest Policy | Conftest | Yes |
| AI Release Notes | Ollama / HF / OpenAI | No (UNSTABLE) |
| Deploy to EKS | kubectl | Yes |

---

## Key Design Decisions

**Why Ollama as primary LLM?** Running Mistral locally on the Jenkins server means zero cost per pipeline run and no internet dependency. Cloud APIs are fallbacks only.

**Why `catchError(UNSTABLE)` on AI stages?** AI reports add value but should never block a deploy. If the LLM is down, the pipeline continues and engineers get standard scan output.

**Why OPA/Conftest via Docker?** No host installation required — the policy runner is containerized, so any Jenkins server can run policies without setup.

**Why OWASP suppression file?** NVD CPE matching over-reports false positives — for example, `spring-boot-starter-thymeleaf` getting tagged with Thymeleaf CVEs even though it contains no Thymeleaf code. Suppressions keep the signal-to-noise ratio high.

---

## Project Structure

```
AI-Powered-DevSecOps-Pipeline/
├── scripts/
│   ├── ai_utils.py                  # LLM client: Ollama → HF → OpenAI
│   ├── ai_code_review.py            # AI Layer 1: git diff → code review
│   ├── ai_security_analysis.py      # AI Layer 2: OWASP+Trivy → exec report
│   └── ai_release_notes.py          # AI Layer 3: git log → release notes
├── src/                             # Spring Boot application (adapted)
├── data/                            # H2 database files
├── Jenkinsfile-devsecops            # AI-augmented DevSecOps pipeline
├── jenkinsfile                      # Standard DevSecOps pipeline
├── deployment.yaml                  # K8s: Deployment + Service + Ingress
├── dockerfile-security.rego         # OPA policy for Dockerfile
├── opa-k8s-security.rego            # OPA policy for K8s manifests
├── Dockerfile                       # eclipse-temurin:17-jdk, non-root user
├── pom.xml                          # Maven deps + OWASP plugin config
├── ai-code-review-report.md         # Sample AI Layer 1 output
├── ARCHITECTURE.md                  # Deep-dive architecture documentation
└── README.md
```

---

## How to Run Locally

### Prerequisites
- Java 17, Maven, Docker
- Ollama installed with Mistral pulled: `ollama pull mistral`
- Python 3 (stdlib only — no pip install needed)

### Run the Application
```bash
git clone https://github.com/pavangm196-devops/AI-Powered-DevSecOps-Pipeline.git
cd AI-Powered-DevSecOps-Pipeline

# Run all tests
mvn test

# Start the app (H2 auto-created)
mvn spring-boot:run
# App → http://localhost:8080
# H2 console → http://localhost:8080/h2-console
```

### Run AI Scripts Locally
```bash
# Requires Ollama running: ollama serve
python3 scripts/ai_code_review.py
python3 scripts/ai_security_analysis.py
python3 scripts/ai_release_notes.py
```

### Run OPA Policies
```bash
# Validate Dockerfile
docker run --rm -v $(pwd):/project openpolicyagent/conftest test --policy dockerfile-security.rego Dockerfile

# Validate K8s manifests
docker run --rm -v $(pwd):/project openpolicyagent/conftest test --policy opa-k8s-security.rego deployment.yaml
```

---

## Jenkins Setup

### Required Credentials

| Credential ID | Type | Used For |
|---|---|---|
| `docker-hub-creds` | Username/Password | Docker Hub push |
| `hf-api-token` | Secret Text | HuggingFace API fallback |
| `openai-api-key` | Secret Text | OpenAI API fallback |
| `kube` | Secret File | kubectl kubeconfig for EKS |

### Environment Variables
```groovy
OLLAMA_HOST         = "http://127.0.0.1:11434"
OLLAMA_MODEL        = "mistral"
AWS_REGION          = "ap-south-1"
AI_FAIL_ON_CRITICAL = "false"   // set "true" to block deploy on CRITICAL CVEs
```

---

## What This Demonstrates

| Skill | How it's shown |
|---|---|
| AI/LLM Integration | 3-layer AI pipeline with Ollama + HF + OpenAI fallback chain |
| DevSecOps | OWASP + Trivy + SonarQube + JaCoCo all integrated in one pipeline |
| Policy-as-Code | OPA/Conftest policies for Dockerfile and K8s manifests |
| Kubernetes | EKS deployment with HTTPS, cert-manager, NGINX Ingress |
| Jenkins | Multi-stage pipeline with parallel security scans |
| Python | AI utility scripts for LLM prompt engineering and report generation |
| Security | Non-root containers, resource limits, CVE suppression management |

---

## Original Project Credit

The Spring Boot application (GameApp — Snake game + Calculator + Auth) is adapted from [ManojKRISHNAPPA/SnakeGame]. Full credit to the original author for the application code. All DevSecOps infrastructure, AI pipeline scripts, OPA policies, Kubernetes manifests, and Jenkins configuration in this repository are my own work.

---

## Author

**Pavan G M** — DevOps Engineer | AWS Certified Solutions Architect (SAA-C03) | CKA Certified

[![GitHub](https://img.shields.io/badge/GitHub-pavangm196--devops-black?logo=github)](https://github.com/pavangm196-devops)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-pavan--gm96-blue?logo=linkedin)](https://linkedin.com/in/pavan-gm96)
