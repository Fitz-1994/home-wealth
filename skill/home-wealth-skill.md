# Home Wealth Skill

## 技能描述

连接家庭资产管理系统（home-wealth），可查询资产总览、净资产趋势、投资持仓情况、行情数据等，帮助用户随时了解家庭财务状况。

## 配置

在使用此技能前，需要配置以下参数：

| 参数 | 说明 | 示例 |
|------|------|------|
| `BASE_URL` | home-wealth 服务地址 | `http://192.168.1.100` |
| `API_KEY` | 在系统「设置」页面生成的 API Key | `hw_sk_xxxxxx` |

所有请求在 Header 中携带：
```
X-API-Key: {API_KEY}
```

## 能力列表

### 1. 查询资产总览

**触发词**：资产总览、总资产、净资产、我有多少钱、资产情况

**调用**：
```
GET {BASE_URL}/api/dashboard/overview
```

**返回说明**：
- `netAssetCny` — 净资产（人民币）
- `totalAssetCny` — 总资产
- `totalLiabilityCny` — 总负债
- `categories.LIQUID` — 流动资金
- `categories.FIXED` — 固定资产
- `categories.INVESTMENT` — 投资理财市值
- `categories.LIABILITY` — 负债

**示例问答**：
> 用户：我现在净资产是多少？
> → 调用 overview，回答净资产金额和各类资产分布

---

### 2. 查询净资产趋势

**触发词**：净资产变化、资产增长、最近净资产、净资产走势

**调用**：
```
GET {BASE_URL}/api/dashboard/net-asset/history?days=90
```

**参数**：`days` 可选 30 / 90 / 180 / 365 / 0（全部）

**示例问答**：
> 用户：最近半年净资产涨了多少？
> → 调用 history?days=180，计算首末差值并回答

---

### 3. 查询投资持仓

**触发词**：持仓、股票、我买了什么、投资情况、持仓列表、港股、A股、美股

**调用**：
```
GET {BASE_URL}/api/holdings
GET {BASE_URL}/api/holdings?market=CN_A   # 仅A股
GET {BASE_URL}/api/holdings?market=HK     # 仅港股
GET {BASE_URL}/api/holdings?market=US     # 仅美股
```

**返回字段说明**：
- `symbolName` — 标的名称
- `quantity` — 持仓数量
- `currentPrice` — 当前价格
- `priceCurrency` — 价格币种
- `marketValueCny` — 市值（人民币）
- `unrealizedPnl` — 浮动盈亏（人民币）
- `unrealizedPnlPct` — 浮动盈亏%
- `priceChangePct` — 今日涨跌幅%

**示例问答**：
> 用户：我的A股今天涨了多少？
> → 调用 holdings?market=CN_A，汇总各持仓今日涨跌

---

### 4. 查询持仓排行

**触发词**：持仓占比、哪个股票最多、仓位分布、持仓排名

**调用**：
```
GET {BASE_URL}/api/dashboard/holding-rank?top=10
```

**示例问答**：
> 用户：我持仓最重的是哪几个？
> → 调用 holding-rank，返回前5名持仓名称、市值和占比

---

### 5. 查询投资趋势

**触发词**：投资表现、投资账户变化、基金走势

**调用**：
```
GET {BASE_URL}/api/dashboard/investment/history?days=90
```

---

### 6. 查询汇率

**触发词**：汇率、美元汇率、港币汇率、今日汇率

**调用**：
```
GET {BASE_URL}/api/market/rates
```

**示例问答**：
> 用户：今天美元对人民币汇率是多少？
> → 调用 rates，返回 USD 对应 CNY 的汇率值

---

### 7. 刷新行情

**触发词**：刷新行情、更新股价、拉取最新行情

**调用**：
```
POST {BASE_URL}/api/market/refresh
```

---

## 回答规范

### 金额格式
- 1万以上：`X.XX 万元`
- 100万以上：`X.XX 百万元`
- 涨跌用颜色词辅助：上涨/盈利用「盈利」「上涨」，下跌/亏损用「亏损」「下跌」

### 持仓回答模板
```
您当前持有 {N} 只标的：
- {标的名} ({代码})：{数量}股，现价 {价格}{币种}，市值约 {市值}万元，今日 {涨跌幅}
...
合计投资市值约 {总市值} 万元
```

### 净资产回答模板
```
您当前净资产约 {金额}（总资产 {总资产} - 负债 {负债}）
其中：流动资金 {金额}，固定资产 {金额}，投资理财 {金额}
```

## 错误处理

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| 401 | API Key 无效或过期 | 提示用户在系统设置页面重新生成 Key |
| 404 | 资源不存在 | 提示数据可能尚未录入 |
| 4001 | 行情数据获取失败 | 告知用户行情接口暂时不可用，展示缓存数据（如有） |
| `isStale: true` | 持仓价格为历史缓存 | 在回答中注明"价格可能不是最新，建议刷新行情" |

## OpenAPI 规范

完整接口文档见同目录 `home-wealth-openapi.yaml`，可直接导入 Swagger UI 或 Postman 查看。
