# 本地组合估值与风险计算服务（Spring Boot）

一套完全运行在本地、不依赖任何外部或付费数据源的组合交易—估值—风险引擎：
行情由内置的**确定性模拟器**产生，支持下单、撤单、持仓估值、已实现/未实现盈亏、
可配置置信水平的历史模拟 VaR，以及拆股、现金分红等公司行为。所有金额使用 `BigDecimal`
统一舍入（现金 2 位小数、数量 8 位，`HALF_UP`），每账户分段锁保证并发下总账守恒。

## 1. 快速开始

```bash
mvn spring-boot:run
# 或
mvn package && java -jar target/portfolio-risk-engine-java-1.0-SNAPSHOT.jar
```

启动后：
- 预置演示账户 `demo`（初始现金 1,000,000.00）；
- 预置标的 `AAPL`、`MSFT`、`600519`、`000001`，每个标的带 60 个确定性种子历史价格；
- 服务端口默认 `8080`。

## 2. 接口用法

### 2.1 账户

```bash
# 新建账户（幂等：已存在则直接返回）
curl -X POST "http://localhost:8080/api/accounts?accountId=alice&initialCash=200000"
```

### 2.2 下单 / 撤单

`clientRequestId` 是**调用方幂等键**，同一账户下相同键的重复提交/网络重试只会返回首次结果，
绝不会重复扣款、重复加仓。

```bash
# 市价买入
curl -X POST http://localhost:8080/api/accounts/demo/orders \
  -H 'Content-Type: application/json' \
  -d '{"clientRequestId":"req-001","symbol":"AAPL","side":"BUY",
       "type":"MARKET","quantity":100}'

# 限价卖出（不可成交时挂 NEW，卖单预占可用数量）
curl -X POST http://localhost:8080/api/accounts/demo/orders \
  -H 'Content-Type: application/json' \
  -d '{"clientRequestId":"req-002","symbol":"AAPL","side":"SELL",
       "type":"LIMIT","quantity":50,"limitPrice":300}'

# 撤单（释放现金/数量预留）
curl -X POST http://localhost:8080/api/accounts/demo/orders/2/cancel

# 查询单笔 / 全部指令
curl http://localhost:8080/api/accounts/demo/orders/1
curl http://localhost:8080/api/accounts/demo/orders
```

订单字段：`status ∈ NEW/FILLED/CANCELLED/REJECTED`。
- 资金/数量不足、行情不可用等**成交期拒绝**不会抛 5xx，而是保留为
  `REJECTED` 并在 `rejectReason` 写明错误码，同样可凭幂等键查询；
- 参数级非法（未知账户/标的、数量非正）返回 4xx 错误响应。

### 2.3 估值与风险

```bash
# 置信水平可选，默认取 risk.engine.confidence（0.95）
curl "http://localhost:8080/api/accounts/demo/portfolio?confidence=0.99"

# 全账户批量估值：单账户失败进入 failures，不影响其他账户
curl http://localhost:8080/api/portfolios
```

返回包含：现金、已实现盈亏、总成本、总市值、未实现盈亏、总权益、分持仓估值，以及
`risk.historicalVaR`、`risk.samples`。

### 2.4 公司行为（按账户、按 `actionId` 幂等）

```bash
# 1 拆 2
curl -X POST http://localhost:8080/api/accounts/demo/corporate-actions \
  -H 'Content-Type: application/json' \
  -d '{"actionId":"split-2026-01","symbol":"AAPL","type":"SPLIT","factor":2}'

# 每股现金分红 0.50
curl -X POST http://localhost:8080/api/accounts/demo/corporate-actions \
  -H 'Content-Type: application/json' \
  -d '{"actionId":"div-2026-01","symbol":"AAPL","type":"DIVIDEND","cashPerShare":0.50}'
```

重复应用同一 `actionId` 返回 `duplicated=true` 且不二次调整；存在占用该标的数量的
未成交卖单时拒绝（`CORPORATE_ACTION_OPEN_ORDERS`），避免预留失真。

### 2.5 行情：随机游走刷新 / 确定性回放 / 故障注入

```bash
# 推进一步确定性随机游走
curl -X POST http://localhost:8080/api/market/refresh

# 按时间戳序列回放（同一标的时间戳必须严格递增）
curl -X POST http://localhost:8080/api/market/replay \
  -H 'Content-Type: application/json' \
  -d '[{"symbol":"AAPL","price":101,"timestamp":"2025-01-02T09:31:00Z"},
       {"symbol":"AAPL","price":103,"timestamp":"2025-01-02T09:32:00Z"}]'

# 测试支持：注入 TIMEOUT / DIRTY / UNAVAILABLE，不带参数清除
curl -X POST "http://localhost:8080/api/market/faults?kind=TIMEOUT"
curl -X POST http://localhost:8080/api/market/faults
```

## 3. 模型假设与边界

**交易**
- 市价单按最新有效收盘价全额成交；本工程为教学/研究模型，未模拟撮合深度、滑点、部分成交与 T+1。
- 限价单：可立即成交则成交，否则进入 `NEW`；买单预占现金，卖单预占数量，撤单释放。
- 费用模型：`fee = max(成交额 × feeRate, minFee)`，买入费计入持仓成本，卖出费冲减已实现盈亏。
- 卖出成本结转采用移动平均成本；持仓清零后成本归零。

**估值**
- 市值 = 数量 × 最新有效价；未实现盈亏 = 市值 − 剩余持仓成本；总权益 = 现金 + 总市值。
- **“无法计算”与“结果为零”严格区分**：
  - 空仓 → `status=OK`，市值/VaR 为 `0.00`（真实的零）；
  - 有持仓但无最新价、报价超过 `risk.engine.max-quote-age-seconds`、或行情源脏/超时/不可用
    → `status=IMPOSSIBLE`，数值字段为 `null` 且给出原因，**绝不使用默认价或过期价凑数**。

**风险（历史模拟法 VaR）**
- 不假设收益分布，不使用外部数据：取各持仓共同长度的历史价格，计算简单收益率，
  按账户当前持仓市值权重构造逐日历史损益，取损益分布的 `(1−confidence)` 分位数；
  VaR 为对应损失的非负金额。
- 可配置 `confidence ∈ (0,1)` 与最少收益样本数 `risk.engine.min-history-samples`；
  样本不足 → `IMPOSSIBLE / RISK_HISTORY_INSUFFICIENT`，而不是返回 0。
- 边界：历史模拟对尾部极端事件不做外推；组合内任一持仓缺行情时风险同样标记无法计算。

**公司行为**
- 拆股：数量按 `factor` 调整（可用数量同步），总成本不变（单位成本等比下降），现金不变。
- 分红：现金与可用现金增加 `持股 × 每股分红`，计入已实现盈亏，数量/成本不变；税前口径。
- 拆股只调整存量持仓，不会自动调整历史行情；真实市场在除权日会同步调整历史价格序列，
  使用风险指标前应通过 `/api/market/replay` 提供除权后的连续价格。

**并发与可复现**
- 每个账户映射到固定数量的分段锁（64 段），同账户写操作串行、跨账户并行；
  行情读取与账户锁分离，避免死锁。批量估值逐账户隔离异常。
- 所有时间戳来自可推进、不会倒退的 `SimClock`；行情生成是固定公式/固定序列，
  因此相同输入 + 相同行情回放必然得到相同的持仓、估值与 VaR。

## 4. 错误语义

所有错误返回 `{"code","message","time"}`，`code` 是稳定枚举，调用方可程序化分支：

| code | 含义 | 典型 HTTP |
| --- | --- | --- |
| `ACCOUNT_NOT_FOUND` / `ORDER_NOT_FOUND` | 账户/订单不存在 | 404 |
| `SYMBOL_NOT_FOUND` | 标的未注册 | 404 |
| `ORDER_DUPLICATE` / `CORPORATE_ACTION_DUPLICATE` | 幂等冲突（语义上成功路径返回原结果） | 409 |
| `ILLEGAL_AMOUNT` | 数量/限价非正 | 400 |
| `INSUFFICIENT_CASH` | 可用现金不足（成交期为订单 REJECTED） | 400/订单体 |
| `INSUFFICIENT_POSITION` | 可卖数量不足（成交期为订单 REJECTED） | 400/订单体 |
| `ORDER_NOT_CANCELLABLE` | 订单状态不可撤 | 409 |
| `ORDER_PRICE_UNAVAILABLE` / `PRICE_STALE` | 无行情或行情过期 | 503 |
| `MARKET_DATA_TIMEOUT` / `MARKET_DATA_DIRTY` / `MARKET_DATA_UNAVAILABLE` | 行情源三类故障 | 504/503 |
| `RISK_HISTORY_INSUFFICIENT` / `RISK_PRICE_UNAVAILABLE` | 风险无法计算 | 503 |
| `RISK_INVALID_CONFIDENCE` | 置信水平不在 (0,1) | 400 |
| `CORPORATE_ACTION_OPEN_ORDERS` | 未成交卖单占用，拒绝公司行为 | 409 |
| `CORPORATE_ACTION_INVALID` / `CORPORATE_ACTION_UNKNOWN_TYPE` | 公司行为参数非法 | 400 |
| `REPLAY_BAD_TIMESTAMP` / `REPLAY_UNKNOWN_SYMBOL` | 行情回放时间戳/标的非法 | 400 |

行情 SPI（`MarketDataProvider`）可替换为真实来源，但必须在边界把异常翻译为
`MarketDataException.Kind`（TIMEOUT/DIRTY/UNAVAILABLE）；`ValidatingMarketDataProvider`
负责价格合法性与陈旧度校验，底层异常永远不会裸抛给调用方。

## 5. 配置

`src/main/resources/application.properties`：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `risk.engine.fee-rate` | 0.0003 | 手续费费率 |
| `risk.engine.min-fee` | 1.00 | 单笔最低费用 |
| `risk.engine.confidence` | 0.95 | 默认 VaR 置信水平 |
| `risk.engine.min-history-samples` | 30 | 最少收益样本数 |
| `risk.engine.max-quote-age-seconds` | 3600 | 报价最大陈旧秒数 |
| `risk.engine.demo-initial-cash` | 1000000.00 | 演示账户初始现金 |
| `risk.engine.seed-history-size` | 60 | 每标的种子历史长度 |

## 6. 本地验证

```bash
mvn test
```

控制台会打印 `【业务用例】通过 ✓ / 失败 ✗` 与 `【业务模块】` 汇总（见
`tests/support/BusinessTestLogger`），测试报告位于 `target/surefire-reports`。
覆盖的关键业务：

- `TradingEngineTest`：买卖成交与费用、`clientRequestId` 重复提交幂等、
  资金不足/数量不足/标的不存在/账户不存在/数量非法的差异化拒绝、
  限价单现金/数量预留与撤单释放；
- `ValuationRiskTest`：市值与已实现/未实现盈亏、空仓“零值” vs 缺行情“无法计算”、
  历史样本不足、置信水平配置与校验、行情超时/脏数据/不可用的翻译、批量账户隔离；
- `CorporateActionTest`：拆股数量与成本守恒、分红派息、重复应用幂等、
  未成交卖单拦截、调整后估值自洽；
- `ConcurrencyAndReplayTest`：12 线程下单/卖出/撤单/重试交错下
  “现金 + 持仓成本 + 已实现盈亏 + 累计费用 = 初始现金”守恒，
  下单与行情刷新交错时“现金 + 市值 = 总权益”恒成立，相同输入回放两次结果完全一致；
- `WebApiIntegrationTest`：REST 全链路与 HTTP 错误语义。
