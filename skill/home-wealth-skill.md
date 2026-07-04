# Home Wealth Skill

## 技能描述

连接家庭资产管理系统（home-wealth）。**既能查询**（资产总览、净资产/投资趋势、持仓、收益率、分红、交易流水、行情汇率），**也能录入与操作**（记录买卖交易、手工录价、管理持仓/账户/分组、刷新行情等）。让用户随时了解并维护家庭财务状况，尤其适合「白天成交、随口一句就把交易记进系统」的场景。

## 配置

| 参数 | 说明 | 示例 |
|------|------|------|
| `BASE_URL` | home-wealth 服务地址（局域网） | `http://192.168.1.100` |
| `API_KEY` | 在系统「设置」页面生成的 API Key | `hw_sk_xxxxxx` |

所有请求在 Header 中携带：
```
X-API-Key: {API_KEY}
```

- **一个 API Key 绑定一个用户**，所有读写都只作用在该用户自己的数据上，无需再传 userId。
- 返回统一包装：`{ "code": 200, "message": "success", "data": <业务数据>, "timestamp": ... }`。**`code=200` 为成功，业务数据在 `data` 字段**；非 200 见文末错误码表。

---

## ⚠️ 写操作安全规范（动手前先读这段）

系统只做**记账/跟踪**，不连券商、不真实下单、不划转任何资金。所有「操作」都是往你自己的账本里写数据。即便如此，写库仍需谨慎：

1. **查询类（GET）可直接调用**，无需确认。
2. **写操作（POST/PUT/DELETE 改数据）必须先复述、后执行**：把「要记什么/改什么」用自然语言回读给用户 → 得到明确「确认/对」→ 才调用接口。**记录交易、平仓、任何 DELETE 一律要确认。**
3. **DELETE 不可逆**。特别注意：**删除交易流水不会自动回滚持仓数量与现金余额**（要冲销请反向记一笔，而非删除）；删快照、删分组同理。
4. **金额约定**：记录**买入**时 `amount = 数量 × 价格 + 手续费`（amount 已含费）。
5. **币种按市场推断**（用户没说时）：A股 `CNY` / 港股 `HKD` / 美股 `USD`。
6. **不要**通过本技能改密码、创建/吊销 API Key、注册用户（`/api/auth/*`）——超出范围，涉及账号安全。

**记录交易的标准动作**（示范）：
> 用户：今天 380 买了 100 股腾讯
> Agent：（先 `GET /api/holdings` 找到腾讯 → 0700.HK / HK / HKD）
> → 回读：「记一笔买入：**腾讯控股 0700.HK**，100 股 @ 380 HKD，金额 38,000 HKD（含费）。确认吗？」
> 用户：确认
> → `POST /api/transactions`

---

## 领域速查

- **标的代码（Yahoo 格式）**：A股沪市 `600519.SS`、深市 `000858.SZ`；港股 `0700.HK`；美股 `AAPL`；汇率 `USDCNY=X`；港股期权 `HSI250328C24000.HK`。
- **`market` 取值**：`CN_A` / `HK` / `US` / `HK_OPT` / `US_OPT` / `FX`。
- **`txnType` 交易类型**：`BUY` 买入 · `SELL` 卖出 · `DIVIDEND` 分红 · `FEE` 费用 · `CASH_IN` 入金 · `CASH_OUT` 出金 · `OPENING` 建仓初始（系统合成，勿手动用）。
- **账户维度**：`account_type` = `REGULAR` / `INVESTMENT`；`asset_category` = `LIQUID` / `FIXED` / `RECEIVABLE` / `INVESTMENT` / `LIABILITY`。持仓只能挂在 `INVESTMENT` 账户下。
- **收益率单位**：所有 `*Pct` 字段**已是百分数**（如 `8.7` 表示 8.7%），直接加 `%`，勿再 ×100。

---

# 一、查询能力（只读，可直接调用）

### 1. 资产总览
**触发词**：资产总览、总资产、净资产、我有多少钱、资产情况
```
GET /api/dashboard/overview
```
- `netAssetCny` 净资产 · `totalAssetCny` 总资产 · `totalLiabilityCny` 总负债
- `categories.LIQUID/FIXED/INVESTMENT/RECEIVABLE/LIABILITY` 各类金额

### 2. 净资产趋势
**触发词**：净资产变化、资产走势、最近净资产
```
GET /api/dashboard/net-asset/history?days=90
```
`days` 可选 `30/90/180/365/0`（0=全部）。返回 `{ dates:[], values:[] }`。

### 3. 投资趋势
**触发词**：投资表现、投资账户变化、基金走势
```
GET /api/dashboard/investment/history?days=90
```

### 4. 持仓列表
**触发词**：持仓、股票、我买了什么、投资情况、港股/A股/美股
```
GET /api/holdings                # 全部
GET /api/holdings?market=CN_A    # A股（HK / US 同理）
GET /api/holdings?accountId={id} # 指定账户
GET /api/holdings/{id}           # 单条
```
字段：`id` 持仓 id · `symbolName` 名称 · `symbol` 代码 · `quantity` 数量 · `currentPrice` 现价 · `priceCurrency` 币种 · `marketValueCny` 市值(CNY) · `unrealizedPnl` 浮盈(CNY) · `unrealizedPnlPct` 浮盈% · `priceChangePct` 今日涨跌% · `isStale` 价格是否为历史缓存。
> ⚙️ 这是「按名字/代码找持仓」的入口——**卖出、分红、改持仓前先在这里拿到 `id`，作为 `holdingId` 传给交易接口**。

### 5. 持仓排行
**触发词**：持仓占比、哪个股票最多、仓位分布、集中度
```
GET /api/dashboard/holding-rank?top=10
```

### 6. 投资收益（收益率与盈亏）
**触发词**：收益、赚了多少、收益率、今年赚了多少、这个月收益
```
GET /api/returns/summary            # YTD / 近1月 / 近3月 / 累计
GET /api/returns/monthly?year=2026  # 各月
GET /api/returns/yearly             # 各年
GET /api/returns/daily?from=2026-06-01&to=2026-06-30
```
- summary：`ytdPct/ytdPnl` · `month1Pct/month1Pnl` · `month3Pct/month3Pnl` · `inceptionPct/inceptionPnl`（累计）· `inceptionDate`
- monthly/yearly：`period`（`2026-05`/`2026`）· `returnPct` · `absolutePnlCny`
- daily：`date` · `returnPct` · `pnlCny` · `beginValue` · `endValue` · `netCashflow`（Modified Dietz）
> `*Pct` 已是百分数，直接用。

### 7. 分红现金流
**触发词**：分红、股息、被动收入、退休现金流、分红进度
```
GET /api/dividend/summary                 # 滚动12月 / 本年 / 本月
GET /api/dividend/history?months=12
GET /api/dividend/detail?year=2026         # 该年各标的
GET /api/dividend/detail?year=2026&month=5 # 指定月
```
- summary：`last12MonthsTotal` · `currentYearTotal` · `currentMonthTotal`（均 CNY 折算）
- history：`months:["2026-01",…]` · `totals:[…]`
- detail：`symbol/symbolName/market` · `totalDividendCny` · `totalDividendOriginal/currency` · `eventCount`

### 8. 交易流水
**触发词**：交易记录、成交记录、买卖历史、流水
```
GET /api/transactions?from=&to=&txnType=&accountId=&holdingId=&page=0&size=50
```
返回 `{ items:[TransactionVO], total, page, size }`。所有过滤参数可选。

### 9. 账户
**触发词**：账户、有哪些账户、账户余额
```
GET /api/accounts            # 列表
GET /api/accounts/{id}
GET /api/accounts/values     # {accountId: 市值CNY}
GET /api/accounts/summary    # {assetCategory: 合计CNY}
```

### 10. 现金余额（投资账户内的现金）
```
GET /api/accounts/{accountId}/cash-balances   # [{currency, amount, cnyAmount}]
```

### 11. 定期/普通账户记录（现金、固定资产、应收、负债等的余额快照）
```
GET /api/accounts/{accountId}/records          # 历史
GET /api/accounts/{accountId}/records/current  # 当前值
```

### 12. 行情与汇率
**触发词**：汇率、美元/港币汇率、股价、行情
```
GET /api/market/rates             # {USD: 7.xx, HKD: 0.9x, ...} 对 CNY
GET /api/market/rates/{currency}  # 单个
GET /api/market/prices?symbols=600519.SS,0700.HK
```

### 13. 基准对比（沪深300 / 标普 等）
```
GET /api/benchmarks                                   # 可选基准列表
GET /api/benchmarks/{symbol}/returns?granularity=monthly&year=2026
GET /api/benchmarks/series?from=&to=
```

---

# 二、操作能力（写 — 必须先复述、经用户确认再调用）

### W1. 记录交易 ★（买 / 卖 / 分红 / 现金进出）
```
POST /api/transactions
```
| 字段 | 必填 | 说明 |
|---|---|---|
| `accountId` | ✅ | 投资账户 id（`GET /api/accounts` 里 `account_type=INVESTMENT` 的那个；只有一个就默认它） |
| `txnType` | ✅ | `BUY`/`SELL`/`DIVIDEND`/`FEE`/`CASH_IN`/`CASH_OUT` |
| `amount` | ✅ | 金额。**BUY = 数量×价格 + 手续费（含费）**；SELL 填卖出总额（系统再减 fee 入现金） |
| `currency` | ✅ | 币种（按市场推断） |
| `symbol` / `market` | BUY 建议填 | 标的代码 / 市场；BUY 时用于匹配或**自动建仓** |
| `holdingId` | **SELL/DIVIDEND 必填** | 指向已有持仓（先用能力 4 查到 id）；BUY 可省 |
| `quantity` | BUY/SELL ✅ | 数量（>0） |
| `price` | 建议 | 成交单价（每股/份） |
| `fee` | | 手续费，默认 0 |
| `tradeDate` | | `YYYY-MM-DD`，省略=今天 |
| `note` | | 备注 |

**副作用（需向用户解释）**：
- **BUY**：无对应持仓则**自动建仓**，有则更新加权平均成本；扣减该币种现金余额。
- **SELL**：需 `holdingId` 且持仓数量足够（否则报 `7003`）；减持，清零则持仓自动关闭；现金余额 +（amount−fee）。
- **DIVIDEND**：现金余额增加；并按成本回收法下调该持仓成本价。
- 记录后，**收益/净资产曲线要等当晚 23:59 快照才体现**，当天曲线不会立即变化。

> BUY 示例 body：`{"accountId":3,"txnType":"BUY","symbol":"0700.HK","market":"HK","quantity":100,"price":380,"amount":38000,"currency":"HKD","tradeDate":"2026-07-04"}`

### W2. 手工录价（港股期权等无公开行情的标的兜底）
```
POST /api/market/manual-price      body: {"symbol":"HSI...C.HK","price":123.4,"currency":"HKD"}
DELETE /api/market/manual-price/{symbol}
```

### W3. 刷新行情（拉取我持仓的最新价）
**触发词**：刷新行情、更新股价
```
POST /api/market/refresh
```

### W4. 持仓管理
```
POST   /api/holdings              body: {accountId, symbol, symbolName?, market, quantity, costPrice, priceCurrency, note?}
PUT    /api/holdings/{id}         # 改数量/成本/备注
DELETE /api/holdings/{id}         # 平仓（关闭持仓）
POST   /api/holdings/validate-symbol   body: {"symbol":"0700.HK"}   # 校验代码并预取名称/价格
POST   /api/holdings/batch        # 批量导入 [CreateHoldingRequest]
```
> 日常「买入」优先用 **W1 记录交易**（自动建仓 + 联动现金/成本）；`POST /api/holdings` 仅用于直接补录一个已有仓位。

### W5. 账户管理
```
POST   /api/accounts   body: {accountName, accountType, assetCategory, currency?, description?, sortOrder?}
PUT    /api/accounts/{id}
DELETE /api/accounts/{id}
```

### W6. 现金余额 upsert
```
POST   /api/accounts/{accountId}/cash-balances   body: {currency, amount, note?}
DELETE /api/accounts/{accountId}/cash-balances/{id}
```

### W7. 定期/普通账户余额记录（更新某账户当前余额）
```
POST   /api/accounts/{accountId}/records   body: {amount, currency?, recordDate?, note?}
DELETE /api/accounts/{accountId}/records/{recordId}
```

### W8. 持仓分组
```
POST   /api/holding-groups          body: {groupName, note?, symbols:[...]}
PUT    /api/holding-groups/{id}
PUT    /api/holding-groups/{id}/members   body: {symbols:[...]}
DELETE /api/holding-groups/{id}
```

### W9. 分红拉取 / 回填
```
POST /api/dividend/fetch              # 拉取最新分红事件
POST /api/dividend/backfill?months=3  # 用持仓×历史事件重算
```

### W10. 快照
```
POST   /api/snapshots/trigger    # 立即生成今日快照（一般夜间自动）
DELETE /api/snapshots/{date}     # YYYY-MM-DD，删除某日快照（不可逆）
```

### W11. 触发飞书日报
```
POST /api/notify/daily/trigger   # 立即推送一次「家庭资产日报」到飞书群
```

---

## 回答规范

### 金额格式
- 1 万以上：`X.XX 万元`；100 万以上：`X.XX 百万元`。
- 涨跌用词辅助：盈利/上涨 vs 亏损/下跌；必要时带 🟢/🔴。

### 持仓回答模板
```
您当前持有 {N} 只标的：
- {名称}（{代码}）：{数量}股，现价 {价格}{币种}，市值约 {市值}万元，今日 {涨跌幅}
…
合计投资市值约 {总市值} 万元
```

### 净资产回答模板
```
您当前净资产约 {金额}（总资产 {总资产} − 负债 {负债}）
其中：流动 {金额}，固定 {金额}，投资 {金额}，应收 {金额}
```

### 交易录入回执模板
```
✅ 已记录：{买入/卖出} {名称}（{代码}）{数量}股 @ {价格}{币种}，金额 {金额}{币种}（{日期}）
（收益/净资产曲线将于今晚快照后更新）
```

---

## 错误处理

| code | 含义 | 处理方式 |
|------|------|----------|
| 401 | API Key 无效/过期（`UNAUTHORIZED`） | 提示用户在系统「设置」页重新生成 Key |
| 403 | 无权限（`FORBIDDEN`） | 该数据不属于当前 Key 绑定的用户 |
| 404 | 资源不存在 | 数据可能尚未录入 |
| 2001 | 账户不存在 | 先 `GET /api/accounts` 确认账户 id |
| 3001 | 持仓不存在 | 卖出/分红前先用能力 4 查 `holdingId` |
| 3002 | 标的代码无效/取价失败 | 核对 Yahoo 格式；无行情标的用 W2 手工录价 |
| 3003 | 非投资账户不能加持仓 | 换用 `account_type=INVESTMENT` 的账户 |
| 4001 | 行情获取失败（`MARKET_DATA_FETCH_FAILED`） | 告知行情暂不可用，展示缓存价 |
| 7002 | 交易类型无效 | `txnType` 仅限枚举值 |
| 7003 | 持仓数量不足以卖出 | 复核持仓数量，减少卖出量 |
| `isStale: true` | 持仓价为历史缓存 | 回答中注明「价格可能不是最新，建议刷新行情」，或调 W3 |

---

## OpenAPI 规范

同目录 `home-wealth-openapi.yaml` 可导入 Swagger UI / Postman。
> 注意：该 yaml 较早，主要覆盖查询类接口；**交易流水、收益、基准等较新接口以本文档为准**。
