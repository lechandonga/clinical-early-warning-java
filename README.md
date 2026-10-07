# 住院患者病情恶化预警服务

基于 Spring Boot 的住院患者病情恶化早期预警服务。使用本地内存数据模拟患者体征与检验结果的持续上报，保证在**迟到、重复、并发上报**下风险判定与告警结论一致且可解释。

## 架构概览

```
POST /api/observations
        |
ObservationValidator   关键字段 / 取值范围 / 时间戳校验，拒绝给出可区分原因
        |
ObservationService     recordId 幂等去重；患者级锁串行化；迟到数据照常入库
        |
RiskEngine + RuleRegistry   按激活规则版本评分定级，结论记录版本号与触发依据
        |
AlertService           告警产生 / 确认 / 升级 / 解除状态机，重复触发去重
```

## 数据上报与校验

上报记录 `Observation`：`recordId`（幂等键）、`patientId`、`type`（VITAL_SIGN/LAB_RESULT）、`code`、`value`、`unit`、`occurredAt`。

校验失败一律拒绝（HTTP 422），原因可区分（`RejectionReason`）：

| 原因 | 含义 |
| --- | --- |
| `MISSING_FIELD` | recordId/patientId/type/code/value/occurredAt 缺失 |
| `UNKNOWN_CODE` | 观测编码未在 `early-warning.ranges` 配置 |
| `VALUE_OUT_OF_RANGE` | 取值超出配置的 [min, max] |
| `TIMESTAMP_IN_FUTURE` | 时间戳超前当前时间超过 `max-future-skew` |
| `TIMESTAMP_TOO_OLD` | 时间戳早于 `max-past-age` 允许的最大历史 |

- 同一 `recordId` 重复上报返回 `DUPLICATE`（HTTP 409），幂等忽略，不影响判定。
- 在 `max-past-age` 之内的乱序 / 迟到数据**正常入库并重新触发判定**，不会被丢弃；判定按 `occurredAt` 取每个 code 的最新值，与到达顺序无关。

## 风险规则与版本模型

- 规则以**不可变版本**（`RuleVersion`）管理：阈值规则列表（code、比较符、阈值、分值、说明）+ 分值到风险等级（LOW/MEDIUM/HIGH/CRITICAL）的分段 + 告警阈值等级。同名版本重复注册会被拒绝，规则只能以新版本演进。
- 每次判定（`RiskEvaluation`）记录：总分、等级、**所用规则版本**、命中规则及当时的观测值与记录 ID（`explain()` 给出完整人类可读依据）。
- 规则切换只影响新判定；历史判定日志与已产生告警保留原版本结论，**绝不回写**。
- `POST /api/patients/{id}/replay?ruleVersion=v1` 用指定版本重放患者全部已存数据：同一版本 + 同一批数据必然得到相同结论。

## 告警状态流转

```
OPEN --> ACKNOWLEDGED --> ESCALATED --> RESOLVED
  |           |
  |           +--> RESOLVED
  +--> ESCALATED / RESOLVED
```

- 同一患者同一时间最多一条未解除告警；重复触发只更新证据，**不产生重复告警**。
- 已确认（ACKNOWLEDGED）后风险继续升高：追加 `RE_NOTIFIED` 事件再次提醒并更新风险等级，不新建告警。
- 风险回落到告警阈值以下：自动解除（`RESOLVED`）。
- `RESOLVED` 为终态；非法流转（如 RESOLVED->ACKNOWLEDGED、重复确认）返回 HTTP 409 并说明当前状态与目标状态。
- 告警全生命周期事件（`AlertEvent`）完整保留，含每次流转的来源/目标状态、规则版本与说明。

## 并发一致性

- 患者级 `ReentrantLock` 串行化同一患者的「入库 -> 判定 -> 告警推进」全程，并发上报不漏报、不重复告警、状态不错乱。
- `recordId` 去重集合与患者锁配合，并发重复上报恰好接受一次。

## 关键配置项（`application.properties`，前缀 `early-warning`）

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `early-warning.ranges.<CODE>.min/max` | 见配置文件 | 各观测编码合法取值范围 |
| `early-warning.max-future-skew` | `PT5M` | 时间戳允许超前上限 |
| `early-warning.max-past-age` | `P30D` | 允许入库的历史数据最大年龄 |

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/observations` | 上报体征/检验数据 |
| GET | `/api/patients/{id}/observations` | 查询患者观测（按发生时间排序） |
| GET | `/api/patients/{id}/evaluations` | 查询患者历史判定（含规则版本与依据） |
| POST | `/api/patients/{id}/replay?ruleVersion=` | 用指定规则版本重放 |
| GET | `/api/alerts`、`/api/alerts/patient/{id}` | 查询告警 |
| POST | `/api/alerts/{id}/acknowledge`、`escalate`、`resolve` | 告警确认/升级/解除 |
| GET/POST | `/api/rules`、`/api/rules/{v}/activate` | 规则版本查询/注册/激活 |

## 本地验证

```bash
# 运行全部单元测试（日志中可见各业务场景与用例结果）
mvn test

# 启动服务
mvn spring-boot:run

# 上报一条异常心率（产生 MEDIUM 告警）
curl -X POST localhost:8080/api/observations -H 'Content-Type: application/json' -d \
  '{"recordId":"demo-1","patientId":"P1","type":"VITAL_SIGN","code":"HR","value":150,"unit":"bpm","occurredAt":"2026-10-07T08:00:00Z"}'

# 查看告警与判定依据
curl localhost:8080/api/alerts
curl localhost:8080/api/patients/P1/evaluations
```

## 测试覆盖（17 个用例，全部通过）

| 测试类 | 业务场景 |
| --- | --- |
| `ObservationValidationTest` | 字段缺失、超范围、未知编码、时间戳超前/过旧五类拒绝 |
| `LateAndDuplicateIngestTest` | 迟到数据纳入判定、重复上报幂等、重复触发不重复告警 |
| `ConcurrentIngestTest` | 64 线程并发上报、32 线程并发重复上报 |
| `AlertLifecycleTest` | 完整闭环、非法流转拒绝、确认后再提醒、风险回落自动解除 |
| `RuleVersionSwitchTest` | 版本切换不改写历史、同版本重放结论一致、规则不可变 |
