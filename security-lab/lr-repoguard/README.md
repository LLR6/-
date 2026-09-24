# LR-RepoGuard

> 面向个人仓库和 CI/CD 的轻量级 DevSecOps 安全体检工具。

LR-RepoGuard 关注一个很常见、也很容易被忽略的问题：**代码本身没漏洞，不代表仓库和构建流程是安全的。**

它会扫描本地仓库中的高风险工程配置，例如：

- Android / Java 签名材料被提交到 Git；
- 私钥、GitHub token、AWS access key 等敏感模式；
- `curl | bash` / `wget | sh` 等危险 shell 链；
- `chmod 777`；
- GitHub Actions 使用 `permissions: write-all`；
- GitHub Actions 只固定到 tag，而没有固定到完整 commit SHA。

## Example

```bash
cd security-lab/lr-repoguard
python -m pip install -e .
lr-repoguard ../.. --json repoguard.json --markdown repoguard.md --fail-on high
```

输出示例：

```text
critical=0 high=0 medium=17 low=0
```

Medium 并不自动意味着“存在漏洞”。例如 `actions/checkout@v4` 很常见，但从供应链完整性角度看，它仍然不是 immutable reference，因此会被记录为 hardening 建议。

## Rules

| Rule | Severity | Meaning |
| --- | --- | --- |
| SIGNING-MATERIAL | Critical | 跟踪到 .jks / .keystore / .p12 / .pfx 或它们的编码包装文件 |
| SECRET-PRIVATE-KEY | Critical | 文本中出现私钥 PEM 标记 |
| SECRET-GITHUB-TOKEN | Critical | 疑似 GitHub token |
| SECRET-AWS-ACCESS-KEY | Critical | 疑似 AWS access key |
| CI-CURL-PIPE-SHELL | High | 下载内容直接 pipe 到 shell |
| CI-WGET-PIPE-SHELL | High | 下载内容直接 pipe 到 shell |
| GHA-WRITE-ALL | High | GitHub Actions 使用 write-all |
| GHA-UNPINNED-ACTION | Medium | Action 没固定到 40 位 commit SHA |
| FS-CHMOD-777 | Medium | 过宽文件权限 |

## Why This Project

这个项目来自真实的工程问题：发布 APK、配置自动构建、管理 GitHub Actions 时，很容易为了“先跑起来”把签名文件、临时凭据或者过宽权限留在仓库里。

RepoGuard 的目标不是取代专业 secret scanner，而是训练一种安全工程习惯：

```text
feature works
    ↓
can it be reproduced?
    ↓
can it be abused?
    ↓
what sensitive material did the build process introduce?
```

## Design

```text
repository
  ├── tracked files
  ├── GitHub Actions
  ├── scripts
  └── config
        ↓
rule scanners
        ↓
Finding(rule, severity, path, line, evidence)
        ↓
JSON + Markdown report
        ↓
optional CI threshold
```

核心实现只使用 Python 标准库，方便在干净 CI 环境运行。

## Run Tests

```bash
PYTHONPATH=src python -m unittest discover -s tests -v
```

## CI Use

项目工作流会：

1. 运行单元测试；
2. 扫描整个当前仓库；
3. 对 High / Critical finding 失败；
4. Medium finding 作为 hardening 提示保留。

## Limitations

- 正则 secret detection 可能误报，也可能漏报；
- 当前不检查 Git 历史提交，只扫描工作树；
- 还没有接 GitHub Advisory Database / OSV；
- 还没有做 SARIF 输出与 GitHub Code Scanning 集成；
- “未固定 Action SHA”是供应链 hardening 建议，不等同于已被攻击。

## Roadmap

- [ ] SARIF output
- [ ] Git history secret scan
- [ ] package-lock / Gradle dependency inventory
- [ ] GitHub Advisory / OSV adapter
- [ ] Dockerfile hardening rules
- [ ] baseline / allowlist
- [ ] GitHub Code Scanning integration
