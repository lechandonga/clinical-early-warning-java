# 住院患者病情恶化预警服务（clinical-early-warning）

基于 Spring Boot 3.4 / Java 21 的住院患者病情恶化早期预警（EWS）服务。使用内存存储模拟体征 / 检验结果的持续上报，在乱序、迟到、重复与并发上报下给出**一致、可解释、可重放**的风险判定与告警闭环。

## 1. 总体能力

- **持续上报与查询**：体征与检验结果按患者持续上报，支持按患者查询观测、评估历史、告警轨迹与重放。
- **显式拒绝、不静默接受**：字段缺失、未知指标、取值越界、时间戳异常、重复报文分别返回可区分错误码。
- **乱序 / 迟到纳入判定**：观测按“观测时间点”有序归并，迟到数据用**观测发生当时生效的规则版本**补算，绝不丢弃。
- **规则可配置且带版本**：规则版本只增不改，按 `effectiveFrom` 时间点生效；历史判定与历史告警永不被新版本改写。
- **告警闭环**：产生 → 确认 → 升级 → 解除；已确认患者风险继续升高会在原告警上“再提醒升级”；风险不升高不重复告警；非法流转被拒绝并说明原因。
- **并发一致性**：同一患者的上报、判定、告警流转在患者级锁内串行完成，不同患者仍并行。
- **确定性重放**：规则引擎为纯函数，同一规则版本重放同一批数据，评分、严重度、触发依据逐条一致。
- **本地数据模拟**：内置定时模拟器（默认关闭），持续产生虚拟患者上报并偶发触发告警。

## 2. 数据校验与拒绝原因

上报 `POST /api/observations`，HTTP 400 响应体 `{code, message, time}`，错误码：

| code | 触发条件 |
| --- | --- |
| `MISSING_FIELD` | `recordId` / `patientId` / `metricCode` / `value` / `observedAt` 缺失或空白，message 指明具体字段 |
| `UNKNOWN_METRIC` | `metricCode` 未在指标注册表中配置 |
| `VALUE_OUT_OF_RANGE` | 取值超出该指标配置的 `[min, max]`，message 带指标、实际值与范围 |
| `INVALID_TIMESTAMP` | 观测时间领先接收时钟超过 `max-timestamp-future`，或早于 `max-timestamp-age`，或格式不可解析 |
| `DUPLICATE_RECORD` | `recordId` 已被接收过（在患者级锁内判定，并发重复也安全） |

同一 `(患者, 指标, 观测时间点)` 的内容用不同 `recordId` 再次上报，视为幂等重复（返回 `duplicated=true`），不新增观测、不新增告警。

内置指标及合法范围（`MetricRegistry`，可在此扩展）：

| 指标 | 类型 | 范围 |
| --- | --- | --- |
| `TEMP_C` 体温 | 体征 | 30–45 |
| `HEART_RATE` 心率 | 体征 | 20–250 |
| `RESP_RATE` 呼吸频率 | 体征 | 0–80 |
| `O2_SAT` 血氧饱和度 | 体征 | 50–100 |
| `SYS_BP` / `DIA_BP` 收缩/舒张压 | 体征 | 30–300 / 20–200 |
| `CONSCIOUS` 意识（1=清醒,0=异常） | 体征 | 0–1 |
| `LACTATE` 乳酸 | 检验 | 0.2–25 |
| `WBC` 白细胞 | 检验 | 0.1–200 |
| `CREATININE` 肌酐 | 检验 | 10–2000 |

## 3. 风险规则与版本模型

- 一条规则版本 `RuleVersion` = 版本号 + 生效时间 `effectiveFrom` + 条件列表 + 中危阈值 `mediumScore` + 高危阈值 `highScore`，发布后不可变。
- 每条条件 `RuleCondition` = 指标 + 运算符（`LT/LTE/GT/GTE`）+ 阈值 + 命中分值。
- 判定窗口内**每个指标取最新值**；命中的多个条件按指标取**最高分**（NEWS 风格，避免同一指标区间重复累计），总分 = 各指标最高分之和。
- 严重度：`0 分 => 无`；`>0 且 < medium => LOW`；`>= medium => MEDIUM`；`>= high => HIGH`。
- 启动时内置版本 **v1**（`effectiveFrom=2000-01-01`，覆盖所有历史数据；`MEDIUM>=4，HIGH>=8`），可通过 API 发布 v2、v3……
- 一次上报的评估规则版本 = **该观测 `observedAt` 当时生效的版本**（`effectiveFrom <= observedAt` 中的最新版本）。因此迟到的历史数据即使在新版本发布后到达，仍按旧版本判定。
- 每次评估结果（含规则版本号、总分、逐条触发依据）只追加到评估历史，不被后续规则更新改写。

发布新版本示例：

```bash
curl -X POST localhost:8080/api/rules/versions -H 'Content-Type: application/json' -d '{
  "versionId":"v2",
  "description":"收紧氧合阈值",
  "effectiveFrom":"2026-10-07T16:00:00Z",
  "mediumScore":3, "highScore":6,
  "conditions":[{"metricCode":"O2_SAT","operator":"LTE","threshold":90,"score":3}]
}'
```

确定性重放（只读，不写任何状态）：

```bash
curl "localhost:8080/api/patients/P1/replay?asOf=2026-10-07T16:30:00Z&ruleVersionId=v1"
curl "localhost:8080/api/patients/P1/replay?asOf=2026-10-07T16:30:00Z&ruleVersionId=v2"
```

不传 `ruleVersionId` 时按 `asOf` 当时生效的版本重放。同版本多次重放结果逐字段相等；不同版本对同批数据可得出不同结论。

## 4. 告警状态流转

状态：`ACTIVE`（产生）→ `ACKNOWLEDGED`（确认）→ `ESCALATED`（升级）→ `RESOLVED`（解除）。

**自动动作（上报驱动，在患者级锁内）**

- 无未关闭告警且评估出风险：`RAISED`，创建告警，携带初始触发依据。
- 严重度升高，或同/更高级别下评分继续上升：`AUTO_ESCALATE`；若告警已被确认，则记为 `RE_ALERT_ESCALATE`（已确认患者风险继续升高时的再次提醒），均不新开告警。
- 风险未继续升高：`UNCHANGED`，不重复产生告警。
- 风险归零：不自动解除（解除必须人工闭环）。
- 解除后再次出现风险：产生一条**新**告警，历史解除告警原样保留。

**人工动作** `POST /api/alerts/{id}/transition`，body：`{"action":"ACKNOWLEDGE|ESCALATE|RESOLVE","operator":"...","reason":"..."}`

| 动作 | 允许的源状态 | 非法时 |
| --- | --- | --- |
| `ACKNOWLEDGE` | ACTIVE, ESCALATED | 已确认 / 已解除 → `ILLEGAL_TRANSITION`（409，含具体原因） |
| `ESCALATE` | ACTIVE, ACKNOWLEDGED | 已升级 / 已解除 → `ILLEGAL_TRANSITION` |
| `RESOLVE` | ACTIVE, ACKNOWLEDGED, ESCALATED | 已解除 → `ILLEGAL_TRANSITION` |

缺少 `operator` 返回 `OPERATOR_REQUIRED`；告警不存在返回 `ALERT_NOT_FOUND`（404）。每次流转（含系统自动产生 / 升级）都写入不可变事件轨迹 `events`，可通过 `GET /api/patients/{id}/alerts` 查看完整依据与来龙去脉。

## 5. 关键配置项（`application.properties`，前缀 `clinical-warning`）

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `max-timestamp-future` | `PT5M` | 观测时间允许领先接收时钟的上限 |
| `max-timestamp-age` | `P30D` | 观测时间允许回溯的历史跨度 |
| `lookback` | `PT24H` | 判定窗口：每指标取窗口内截至观测时刻的最新值 |
| `simulation.enabled` | `false` | 是否启动本地模拟上报 |
| `simulation.patient-count` | `3` | 模拟患者数量 |
| `simulation.interval` | `PT10S` | 模拟上报间隔 |

## 6. HTTP 接口一览

| 方法与路径 | 说明 |
| --- | --- |
| `POST /api/observations` | 上报一条观测，返回评估结果与告警动作 |
| `GET /api/patients/{id}/observations` | 查询患者全部观测（按观测时间有序） |
| `GET /api/patients/{id}/assessments` | 评估历史（只追加） |
| `GET /api/patients/{id}/alerts` | 患者告警及完整事件轨迹 |
| `GET /api/alerts/open` | 当前未关闭告警 |
| `GET /api/alerts/{id}` | 单条告警详情 |
| `POST /api/alerts/{id}/transition` | 确认 / 升级 / 解除 |
| `GET /api/rules/versions` | 规则版本列表 |
| `POST /api/rules/versions` | 发布新规则版本 |
| `GET /api/patients/{id}/replay?asOf=&ruleVersionId=` | 指定版本确定性重放 |

## 7. 本地验证

运行单测（控制台会打印 `[业务] 用例N => 测试通过...`，可直接看出业务覆盖与各用例情况）：

```bash
mvn test
```

测试共 15 个用例，覆盖业务：

| 测试类 | 覆盖业务 | 用例 |
| --- | --- | --- |
| `ObservationValidationTest` | 数据校验 | 缺字段、未知指标、越界、时间戳异常、重复 recordId |
| `LateAndDuplicateObservationTest` | 迟到 / 乱序 / 重复 | 迟到数据补算触发告警、同点重复幂等、风险持平不重告警 |
| `AlertLifecycleTest` | 告警闭环 | 产生→确认→再提醒→解除、重复确认/解除后操作/无操作人非法、解除后新发风险 |
| `ConcurrentObservationTest` | 并发一致性 | 24 线程并发单告警、16 线程相同报文恰好 1 条接受 |
| `RuleVersionSwitchingTest` | 规则版本 | v2 发布后历史按 v1、迟到历史数据按 v1、新数据按 v2、跨版本重放差异、纯函数确定性 |

启动服务（8080 被占用时可换端口）：

```bash
mvn spring-boot:run
# 或
java -jar target/clinical-early-warning-java-1.0-SNAPSHOT.jar --server.port=18080

# 开启本地持续模拟上报（虚拟患者体征/检验不断流入，观察日志中的评分与告警动作）
mvn spring-boot:run -Dspring-boot.run.profiles=sim
# 或
java -jar target/clinical-early-warning-java-1.0-SNAPSHOT.jar \
  --clinical-warning.simulation.enabled=true \
  --clinical-warning.simulation.patient-count=4 \
  --clinical-warning.simulation.interval=PT5S
```

快速手工链路：

```bash
# 1) 上报低氧 -> LOW 告警产生
curl -X POST localhost:8080/api/observations -H 'Content-Type: application/json' -d '{
  "recordId":"demo-1","patientId":"P-DEMO","metricCode":"O2_SAT","value":88,
  "observedAt":"2026-10-07T12:00:00Z"}'

# 2) 查看告警并确认（把 ALERT_ID 换成上一步返回的 alert.id）
curl -X POST localhost:8080/api/alerts/ALERT_ID/transition -H 'Content-Type: application/json' \
  -d '{"action":"ACKNOWLEDGE","operator":"护士小王","reason":"已床旁评估"}'

# 3) 再上报低血压 -> 已确认告警被再提醒升级（RE_ALERT_ESCALATE）
curl -X POST localhost:8080/api/observations -H 'Content-Type: application/json' -d '{
  "recordId":"demo-2","patientId":"P-DEMO","metricCode":"SYS_BP","value":80,
  "observedAt":"2026-10-07T12:01:00Z"}'

# 4) 查看完整轨迹（触发依据 + 每次流转原因）
curl localhost:8080/api/patients/P-DEMO/alerts
```

## 8. 设计要点

- `service/RuleEngine`：无状态纯函数，是“同版本重放结论一致”的根本保证。
- `store/PatientLocks`：按患者分片的可重入锁；`ObservationService` 与 `AlertService` 的所有读改写都在锁内完成。
- `store/*`：`ConcurrentHashMap` / `CopyOnWriteArrayList` 内存存储；观测按时间戳有序，评估与事件只追加。
- 时间来源统一注入 `java.time.Clock`，测试使用可控的 `MutableClock`，时间相关用例确定可重复。
