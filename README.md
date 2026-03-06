# home-wealth

私有部署的家庭资产管理系统，支持多种资产类型管理、投资持仓跟踪（自动拉取 A 股/港股/美股行情）、资产大盘可视化，并提供完整 REST API 供外部服务集成。

## 功能概览

- **多类型资产管理**：流动资金、固定资产、应收款、投资理财、负债，每类可创建多个账户
- **普通账户**：直接录入资产金额，支持多币种，自动换算人民币，保留历史记录
- **投资账户**：录入持仓标的（代码+数量），自动通过 Yahoo Finance 拉取实时行情计算总市值，支持 A 股、港股、美股、港股/美股期权、外汇
- **每日快照**：自动记录每日净资产和投资资产数据，生成历史趋势图
- **资产大盘**：桑基图展示资产全景，净资产/投资资产折线图，持仓占比排行榜
- **双认证**：JWT（Web 前端）+ API Key（外部服务集成，如 OpenClaw Skill）
- **响应式 UI**：桌面端左侧边栏导航，移动端底部 TabBar，完整适配手机浏览器

## 技术架构

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + Spring MVC + MyBatis |
| 数据库 | MySQL 8.0 |
| 认证 | Spring Security + JWT + API Key |
| 前端 | Vue 3 + Vite + Naive UI + ECharts |
| 行情数据 | Yahoo Finance 非官方接口 |
| 部署 | Docker Compose |

## 快速启动

### 前提条件

- Docker & Docker Compose

### 1. 克隆并配置

```bash
git clone git@github.com:Fitz-1994/home-wealth.git
cd home-wealth
cp .env.example .env
```

编辑 `.env`，修改密码和 JWT 密钥：

```env
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_USER=hwuser
MYSQL_PASSWORD=your_db_password
JWT_SECRET=your_random_secret_key_at_least_32_chars
```

### 2. 启动服务

```bash
docker-compose up -d
```

服务启动后访问 `http://localhost`，首次使用需注册账号。

### 3. 停止服务

```bash
docker-compose down
```

数据存储在 Docker volume `mysql_data` 中，`down` 不会丢失数据。

## 本地开发

### 后端

需要本地 Java 17 + MySQL。

```bash
# 修改 backend/src/main/resources/application-dev.yml 中的数据库连接
cd backend
mvn spring-boot:run
# 后端运行在 http://localhost:8080
```

### 前端

```bash
cd frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173，/api 请求自动代理到 8080
```

## 数据库结构

| 表名 | 说明 |
|------|------|
| `users` | 用户账号 |
| `api_key` | API Key（供外部服务调用） |
| `asset_account` | 资产账户（普通/投资） |
| `regular_account_record` | 普通账户余额历史记录 |
| `investment_holding` | 投资持仓明细 |
| `market_price_cache` | 行情价格缓存 |
| `exchange_rate` | 汇率数据 |
| `daily_net_asset_snapshot` | 每日净资产快照 |
| `daily_investment_snapshot` | 每日投资资产快照 |

## 行情标的代码格式

| 市场 | 格式 | 示例 |
|------|------|------|
| A 股上交所 | `代码.SS` | `600519.SS`（贵州茅台） |
| A 股深交所 | `代码.SZ` | `000858.SZ`（五粮液） |
| 港股 | `代码.HK` | `0700.HK`（腾讯） |
| 美股 | 代码 | `AAPL`、`TSLA` |
| 港股期权 | OCC 格式 + `.HK` | `HSI250328C24000.HK` |
| 美股期权 | OCC 格式 | `AAPL250321C00150000` |
| 汇率 | `FROM+TO+X` | `USDCNY=X`、`HKDCNY=X` |

## 定时任务

| 任务 | 时间（上海时区） |
|------|----------------|
| A 股行情更新 | 工作日 15:30 |
| 港股行情更新 | 工作日 16:30 |
| 美股行情更新 | 工作日次日 06:00 |
| 汇率更新 | 每日 08:00 |
| 每日净资产快照 | 每日 23:59 |

## API 认证

### JWT 认证（Web 前端）

```bash
# 登录获取 token
curl -X POST http://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 使用 token 调用接口
curl http://localhost/api/dashboard/overview \
  -H "Authorization: Bearer <token>"
```

### API Key 认证（外部服务）

登录后在「设置」页面生成 API Key，调用时在请求头携带：

```bash
curl http://localhost/api/dashboard/overview \
  -H "X-API-Key: hw_sk_xxxxxxxx"
```

## 主要 API 接口

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/auth/me` | 当前用户信息 |
| GET | `/api/auth/api-keys` | API Key 列表 |
| POST | `/api/auth/api-keys` | 生成 API Key |
| DELETE | `/api/auth/api-keys/{id}` | 吊销 API Key |

### 资产账户

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/accounts` | 账户列表 |
| POST | `/api/accounts` | 创建账户 |
| PUT | `/api/accounts/{id}` | 更新账户 |
| DELETE | `/api/accounts/{id}` | 删除账户 |
| GET | `/api/accounts/summary` | 资产分类汇总（CNY） |
| GET | `/api/accounts/{id}/records/current` | 账户当前余额 |
| POST | `/api/accounts/{id}/records` | 更新账户余额 |

### 投资持仓

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/holdings` | 持仓列表（含实时市值） |
| POST | `/api/holdings` | 添加持仓 |
| PUT | `/api/holdings/{id}` | 修改持仓 |
| DELETE | `/api/holdings/{id}` | 清仓 |
| POST | `/api/holdings/validate-symbol` | 验证标的代码 |

### 行情数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/market/prices?symbols=` | 批量查询行情缓存 |
| POST | `/api/market/refresh` | 手动刷新我的持仓行情 |
| GET | `/api/market/rates` | 获取所有汇率 |

### 资产大盘

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 资产总览（净资产/各类汇总） |
| GET | `/api/dashboard/sankey` | 桑基图数据 |
| GET | `/api/dashboard/net-asset/history?days=90` | 净资产历史折线数据 |
| GET | `/api/dashboard/investment/history?days=90` | 投资资产历史折线数据 |
| GET | `/api/dashboard/holding-rank?top=20` | 持仓占比排行榜 |

### 快照

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/snapshots/trigger` | 手动生成今日快照 |
| DELETE | `/api/snapshots/{date}` | 删除某日快照 |

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1709712000000
}
```

## OpenClaw Skill 集成

项目内置 OpenClaw Skill 配置文件，位于 `skill/` 目录：

- `skill/home-wealth-openapi.yaml` — OpenAPI 3.0 规范，描述所有可调用接口
- `skill/home-wealth-skill.md` — Skill 说明文档，包含使用场景和调用示例

集成步骤：
1. 在「设置」页面生成一个 API Key
2. 将 API Key 和服务地址配置到 OpenClaw Skill 中
3. 参考 `skill/home-wealth-skill.md` 中的示例进行调用
