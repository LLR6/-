# LR-Sentinel

> 一个用于学习 Detection Engineering 的轻量级、可解释防御检测器。

LR-Sentinel 输入 JSONL 格式的认证、网络连接和 DNS 事件，通过一组透明的启发式规则发现值得进一步调查的行为，并输出 JSON / HTML 报告。

它不是“黑盒 AI IDS”，也不把启发式命中当作攻击结论。项目重点是展示一个完整的防御分析链路：

```text
event ingestion
    ↓
normalization
    ↓
detection rules
    ↓
risk scoring
    ↓
evidence + explanation
    ↓
JSON / HTML report
```

## Detection Rules

| Rule | Detection | Severity |
| --- | --- | --- |
| AUTH-001 | 2 分钟内同一来源/账号多次登录失败 | High |
| AUTH-002 | 多次失败后短时间内成功登录 | Critical |
| NET-001 | 短时间访问同一目标大量不同端口 | High |
| NET-002 | 高度周期性的出站连接 | Medium |
| DNS-001 | 超长 / 高熵 / 可疑 TXT DNS label | Medium |

## Why Explainable Detection?

真实 SOC / DFIR 工作里，告警不能只有一个“恶意概率”。

LR-Sentinel 每条告警都会同时给出：

- `rule_id`
- 严重级别与风险分数
- 观察对象
- 首次/最后时间
- 可验证 evidence
- 命中原因 explanation

这样可以区分：

```text
Detection ≠ Conclusion
```

规则负责找到值得调查的行为，分析人员再结合上下文判断是否真正恶意。

## Quick Start

Python 3.11+，核心检测器只使用标准库。

```bash
cd security-lab/lr-sentinel
python -m pip install -e .
lr-sentinel samples/demo.jsonl --json alerts.json --html report.html
```

也可以直接：

```bash
PYTHONPATH=src python -m lr_sentinel.cli samples/demo.jsonl
```

运行测试：

```bash
PYTHONPATH=src python -m unittest discover -s tests -v
```

## Event Schema

认证事件：

```json
{"ts":"2026-09-24T10:00:00Z","type":"auth","src_ip":"10.0.0.8","user":"admin","status":"fail"}
```

网络事件：

```json
{"ts":"2026-09-24T10:01:00Z","type":"net","src_ip":"10.0.0.8","dst_ip":"10.0.0.9","dst_port":22}
```

DNS 事件：

```json
{"ts":"2026-09-24T10:20:00Z","type":"dns","src_ip":"10.0.0.8","query":"abc.example.test","qtype":"A"}
```

## Project Structure

```text
lr-sentinel/
├── samples/
│   └── demo.jsonl
├── src/lr_sentinel/
│   ├── __init__.py
│   ├── cli.py
│   ├── detector.py
│   └── report.py
├── tests/
│   └── test_detector.py
├── pyproject.toml
└── README.md
```

## Engineering Notes

### Sliding windows

暴力登录和端口扫描不是统计整个日志文件，而是在固定时间窗口里聚合事件，避免“十小时内碰巧出现十个端口”也被误判。

### Beacon heuristic

周期性出站连接使用时间间隔的变异系数（coefficient of variation）作为简单稳定性指标。它只能提供 triage signal，因为正常更新服务、心跳与监控也可能高度周期。

### DNS heuristic

DNS 规则关注 label 长度、字符熵和 TXT 查询。高熵域名并不等于隧道，因此告警解释里明确保留误报边界。

## Limitations

- 当前是单机离线分析，不是生产级流式 IDS。
- 规则阈值需要根据真实环境调整。
- 没有资产画像、用户基线和威胁情报上下文。
- 不尝试自动给出“攻击已确认”的结论。
- 示例数据全部是人工构造的安全测试数据。

## Roadmap

- [ ] CSV / Zeek TSV adapter
- [ ] PCAP → normalized event adapter
- [ ] Sigma-like YAML rule format
- [ ] MITRE ATT&CK mapping
- [ ] baseline-aware anomaly scoring
- [ ] dashboard / timeline
- [ ] detection precision/recall evaluation dataset

## Safe Scope

这是一个**防御检测与日志分析学习项目**。示例和测试只处理本地构造数据，不包含未授权扫描、利用或真实目标攻击功能。
