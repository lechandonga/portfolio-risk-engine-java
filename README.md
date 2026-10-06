# 本地组合估值与风险计算服务 (Portfolio Risk Engine)

基于 Spring Boot 3 / Java 21 的**纯本地**组合交易、估值与风险计算服务。
行情全部来自内置模拟数据源（几何随机游走 + 可手工发布的报价），
**不依赖任何外部或付费数据源、不发起任何网络请求**。

## 1. 功能总览

- **交易指令**：买入 / 卖出（两阶段：下单冻结 → 成交 / 撤单释放）。
- **幂等**：`clientRequestId` 唯一键，客户端重试 / 重复提交只会回放原结果，
  不会重复扣款、重复加仓。
- **拒绝原因可区分**：资金不足、可用数量不足、标的不存在、标的停牌、非整手、
  价格越界、订单状态非法等，均有独立错误码（见第 5 节）。
- **估值**：按最新行情计算持仓市值、已实现 / 未实现盈亏、总权益。
- **风险**：历史模拟法 VaR 与 Expected Shortfall（ES），置信水平可配置 / 可按请求覆盖。
  行情缺失或历史样本不足时明确返回 `NOT_COMPUTABLE`，**绝不静默返回 0 或使用默认价 / 过期价**。
- **行情来源可替换**：实现 `MarketDataSource` SPI 即可；来源超时 / 脏数据统一转换为
  受控的 `MARKET_*` 错误，底层异常不会抛给调用方。
- **公司行为**：拆股（SPLIT）与现金分红（CASH_DIVIDEND）。同一条行为对同一账户
  只能应用一次（原子 claim），重复应用被 `CORPORATE_ACTION_DUPLICATE` 拒绝。
- **并发一致性**：每个账户一把锁，下单 / 成交 / 撤单 / 行情刷新在同一账户上串行化，
  不同账户互不阻塞；现金台账流水代数和恒等于账户现金余额。
- **故障隔离**：批量估值 / 风险中一个账户失败不影响其他账户。
- **可复现**：种子数据使用固定随机种子，测试中时钟可控；相同输入 + 相同行情回放
  得到完全相同的结果。

## 2. 快速开始

```bash
# 构建 + 运行全部单测（需要 JDK 21+、Maven 3.8+）
mvn test

# 启动服务（端口 8080，见 application.properties）
mvn spring-boot:run
```

启动时自动播种：

- 标的：`AAA`、`BBB`（可交易，100 股整手，±10% 价格带宽），`CCC`（已停牌）。
- 账户：`acct-1`（Alice, 50 万现金）、`acct-2`（Bob, 20 万现金）。
- 行情：`AAA` / `BBB` 各 250 个固定种子的历史收盘价 + 一条最新报价。

## 3. 接口用法

所有请求 / 响应均为 JSON。金额统一为字符串数字，现金精确到分（HALF_UP）。

### 3.1 账户

```bash
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' \
  -d '{"accountId":"a1","name":"Alice","initialCash":"100000"}'

curl localhost:8080/api/accounts/a1
curl localhost:8080/api/accounts/a1/ledger
```

### 3.2 标的

```bash
curl -X POST localhost:8080/api/instruments -H 'Content-Type: application/json' \
  -d '{"symbol":"AAA","name":"Alpha Co","lotSize":100,"maxPriceDeviation":0.10,"active":true}'
curl localhost:8080/api/instruments
```

### 3.3 订单（两阶段）

```bash
# 1) 下单：校验并冻结（买入冻结现金，卖出冻结股数）
curl -X POST localhost:8080/api/accounts/acct-1/orders \
  -H 'Content-Type: application/json' \
  -d '{"clientRequestId":"req-001","side":"BUY","symbol":"AAA","quantity":1000}'
# -> orderId, status=NEW

# 2a) 成交：按当前最新行情成交
curl -X POST localhost:8080/api/accounts/acct-1/orders/<orderId>/fill

# 2b) 或撤单：释放冻结
curl -X POST localhost:8080/api/accounts/acct-1/orders/<orderId>/cancel

curl localhost:8080/api/accounts/acct-1/orders
```

重试安全：用**完全相同**的 `clientRequestId` 再 POST，返回原订单，不会二次扣款。

### 3.4 估值与风险

```bash
curl localhost:8080/api/accounts/acct-1/valuation
curl 'localhost:8080/api/accounts/acct-1/risk?confidence=0.99'

# 跨账户批量（单账户失败被隔离为 error 条目，不影响其他账户）
curl localhost:8080/api/valuations
curl 'localhost:8080/api/risks?confidence=0.95'
```

估值行 `status`：

- `OK`：已按新鲜行情估值；
- `MARKET_DATA_UNAVAILABLE`：无新鲜行情，`marketValue/unrealizedPnl` 为 `null`，
  该标的出现在 `unavailableSymbols` 中且**不计入总额**。

风险行 `status`：

- `OK`：含 `dailyVolatility`、`var`、`expectedShortfall`；
- `NOT_COMPUTABLE`：行情缺失 / 历史不足，`var` 等字段为 `null`，
  标的列入 `notComputableSymbols`。

### 3.5 公司行为

```bash
# 注册 2:1 拆股
curl -X POST localhost:8080/api/corporate-actions -H 'Content-Type: application/json' \
  -d '{"actionId":"split-2026-1","symbol":"AAA","type":"SPLIT","ratio":2}'
curl -X POST localhost:8080/api/accounts/acct-1/corporate-actions/split-2026-1/apply

# 注册每股 0.50 元现金分红
curl -X POST localhost:8080/api/corporate-actions -H 'Content-Type: application/json' \
  -d '{"actionId":"div-2026-1","symbol":"AAA","type":"CASH_DIVIDEND","cashPerShare":0.50}'
curl -X POST localhost:8080/api/accounts/acct-1/corporate-actions/div-2026-1/apply
```

拆股：股数 ×ratio（含冻结股数，必须得到整数股），**总成本不变、均价同比下降**。
分红：按当前持仓股数派现一次，写入 `DIVIDEND` 台账流水。

### 3.6 本地行情管理（测试 / 演示）

```bash
# 发布最新价（asOf 可省略，取引擎时钟）
curl -X POST localhost:8080/api/market/quotes -H 'Content-Type: application/json' \
  -d '{"symbol":"AAA","price":10.50}'

# 故障注入（演示错误语义）：NONE / TIMEOUT / DIRTY / MISSING
curl -X POST 'localhost:8080/api/market/faults?symbol=AAA&fault=TIMEOUT'
```

## 4. 估值与风险模型：假设与边界

**估值**

- 持仓为多头、不允许卖空；估值货币单一，金额保留 2 位小数。
- 买入成本基准 = 买入价款（不含佣金；佣金 / 印花税作为费用直接扣现金，并在
  `FEE_DETAIL` 流水中单独列示，便于审计与汇总）。
- 卖出按**加权平均成本**结转，取整残差吸收进剩余成本基准，保证
  “已结转成本 + 剩余成本 == 结转前成本”，逐笔精确到分。
- 已实现盈亏 = 卖出净到账（价款−费用）− 结转成本；未实现盈亏 = 市值 − 剩余成本。
- 报价新鲜度窗口默认 15 秒（`engine.market.staleness-millis`）；超出即视为过期，
  **不会使用旧价计算**。

**风险（历史模拟法）**

- 日收益 `r_i = P_i / P_{i-1} − 1`；
- 损失序列取 `−r` 降序，`VaR_c = 市值 × 第 ceil((1−c)·N) 大损失`；
- `ES_c = 市值 × 尾部损失均值`；`dailyVolatility` 为收益样本标准差。
- 组合 VaR/ES 为各成分**直接加总**（保守口径，不假设跨标的分散化收益）。
- 默认 `c = 0.95`，历史窗口 250，最少需要 30 个日收益（`engine.risk.*` 可配）。
- 边界：样本不足、无历史、无新鲜报价、置信水平越界 → 明确的
  `NOT_COMPUTABLE` / 校验错误，而不是 0。

**并发与确定性**

- 账户级互斥锁：同一账户的下单 / 撤单 / 成交 / 公司行为串行化，不同账户并行。
- 幂等索引、公司行为 claim、台账均为并发安全结构；终态订单无残留冻结。
- 时钟为可替换的 `LongSupplier`，种子行情使用固定随机种子，便于确定性回放。

**已知简化（非生产级假设）**

- 撮合是“按当前报价立即全量成交”的模拟，无盘口 / 部分成交 / 涨跌停撮合。
- 无持久化（内存存储，重启即回到种子数据）；无鉴权；单一法币。

## 5. 错误语义

所有业务错误返回统一结构 `{"code","message","time"}`，`code` 稳定可机读：

| code | HTTP | 含义 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 422 | 参数非法（数量非正、置信水平越界等） |
| `UNKNOWN_INSTRUMENT` | 404 | 标的不存在 |
| `INACTIVE_INSTRUMENT` | 422 | 标的停牌，不可交易 |
| `ACCOUNT_NOT_FOUND` / `ORDER_NOT_FOUND` | 404 | 账户 / 订单不存在 |
| `INSUFFICIENT_CASH` | 422 | 可用资金不足（消息含所需 / 可用金额） |
| `INSUFFICIENT_QUANTITY` | 422 | 可用持仓不足（卖空被拒） |
| `QUANTITY_NOT_MULTIPLE_OF_LOT` | 422 | 数量不是整手倍数 |
| `PRICE_OUT_OF_BAND` | 422 | 限价相对参考价偏离带宽 |
| `ORDER_ALREADY_FINAL` / `ORDER_NOT_CANCELLABLE` / `ORDER_NOT_FILLABLE` | 422 | 订单状态机非法迁移 |
| `MARKET_DATA_UNAVAILABLE` | 503 | 无行情（区分于结果为零） |
| `MARKET_DATA_STALE` | 503 | 行情过期 |
| `MARKET_DATA_TIMEOUT` | 503 | 行情来源超时（已被门面转换，底层异常不外泄） |
| `MARKET_DATA_DIRTY` / `MARKET_SOURCE_FAULT` | 503 | 脏数据 / 来源其他故障 |
| `RISK_NOT_COMPUTABLE` | 422 | 风险无法计算（样本不足等） |
| `CORPORATE_ACTION_UNKNOWN_INSTRUMENT` / `CORPORATE_ACTION_DUPLICATE` | 422 | 公司行为标的未知 / 重复应用 |
| `CONCURRENT_MODIFICATION` | 409 | 并发冲突（预留） |

## 6. 本地验证

```bash
mvn test
```

测试日志按业务模块输出覆盖横幅、每个用例的通过 / 失败情况和模块汇总，例如：

```
==================== 业务覆盖: 交易指令-重复提交与拒绝原因 ====================
[用例通过] 交易指令-重复提交与拒绝原因 | 相同 clientRequestId 重试: 只生成一张单, 只冻结一次资金
...
-------------------- 交易指令-重复提交与拒绝原因 测试汇总: 通过 7 个, 失败 0 个 --------------------
```

共 26 个用例，覆盖六类业务：

| 测试类 | 覆盖业务 |
| --- | --- |
| `OrderIdempotencyAndRejectionTests` | 重复提交幂等、资金 / 数量不足、标的不存在 / 停牌、非整手、撤单 / 终态、费用扣款 |
| `ValuationMarketDataTests` | 市值与未实现盈亏、已实现盈亏、行情缺失、过期、来源超时、脏数据 |
| `CorporateActionTests` | 拆股（数量 / 成本 / 市值自洽）、现金分红、重复应用拒绝、未知标的 |
| `RiskModelTests` | VaR/ES 计算、样本不足 / 无历史的 NOT_COMPUTABLE、置信水平校验 |
| `ConcurrencyInterleaveTests` | 并发重试只下一单、下单 / 撤单 / 行情刷新交错下的台账与净值守恒、账户故障隔离 |
| `DeterministicReplayTests` | 相同输入 + 固定行情两次回放结果完全一致（REST 端到端） |

也可按类运行：

```bash
mvn test -Dtest=ConcurrencyInterleaveTests
```

## 7. 替换行情来源

实现 `com.github.lechandonga.portfolioriskenginejava.market.MarketDataSource`
（`latest(symbol)` 与 `history(symbol, maxPoints)`），在配置中以该接口注册 Bean 即可替换。
适配器内部故障应抛出 `MarketDataSourceException`；`MarketDataService` 负责：
独立超时控制（默认 500ms）、脏数据校验（空 / 非正价格）、新鲜度校验，
并把一切来源异常归一化为受控的 `MARKET_*` 业务错误。
