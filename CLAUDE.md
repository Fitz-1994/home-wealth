# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

### Backend (Spring Boot + MyBatis)
```bash
# Compile
/Users/fitz/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn -f backend/pom.xml clean compile

# Package (skip tests)
/Users/fitz/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn -f backend/pom.xml clean package -DskipTests

# Run tests
/Users/fitz/.m2/wrapper/dists/apache-maven-3.9.6-bin/3311e1d4/apache-maven-3.9.6/bin/mvn -f backend/pom.xml test
```

### Frontend (Vue 3 + Vite)
```bash
cd frontend
npm install
npm run dev      # dev server (port 5173)
npm run build    # production build
```

### Docker (full stack)
```bash
# Build and start all services
docker-compose up -d --build

# Rebuild only backend or frontend
docker-compose up -d --build backend
docker-compose up -d --build frontend

# View logs
docker-compose logs -f backend
docker-compose logs -f frontend

# MySQL shell (use container env vars to avoid auth issues)
docker exec hw-mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" home_wealth'
```

MySQL data persists in the named volume `mysql_data`. It is only lost with `docker-compose down -v`.

## Architecture Overview

### Backend (`backend/src/main/java/com/homewealth/`)

**Layered architecture:** Controller → Service → Mapper (MyBatis XML) → MySQL

Key packages:
- `controller/` — REST endpoints. All return `ApiResponse<T>` wrapper: `{ code, message, data, timestamp }`.
- `service/impl/` — Business logic. Key services: `InvestmentHoldingServiceImpl`, `DashboardServiceImpl`, `SnapshotServiceImpl`, `MarketDataServiceImpl`, `ExchangeRateServiceImpl`.
- `mapper/` — MyBatis interfaces. Corresponding XML files in `resources/mapper/`.
- `model/` — Entity classes (9 tables). Use Lombok `@Data`.
- `dto/` — `request/` for input, `response/` for output VOs.
- `market/` — External market data fetchers:
  - `YahooFinanceFetcher`: uses `v8/finance/chart/{symbol}` (no auth needed), concurrent per-symbol requests via `CompletableFuture`.
  - `SinaFinanceFetcher`: GBK-encoded A股/港股 Chinese names. Symbol conversion: `600519.SS→sh600519`, `0700.HK→hk00700`.
- `scheduler/` — Cron jobs (Asia/Shanghai): A股 15:30, 港股 16:30, 美股 06:00 next day, 汇率 08:00, snapshots 23:59.
- `security/` — `JwtAuthFilter` supports two auth methods: `Authorization: Bearer <jwt>` and `X-API-Key: hw_sk_...` (SHA-256 hashed in DB).
- `exception/` — `GlobalExceptionHandler` + `BusinessException(ErrorCode)`.

**Database schema** is in `resources/schema.sql` and auto-executed on startup (`spring.sql.init.mode: always`). Schema uses `CREATE TABLE IF NOT EXISTS` so it's safe to re-run.

**No Flyway/Liquibase.** Schema changes must be applied manually to existing databases (e.g., `ALTER TABLE` via `docker exec hw-mysql ...`).

### Frontend (`frontend/src/`)

- `api/` — Axios instance + per-module API wrappers (auth, accounts, holdings, market, dashboard).
- `stores/` — Pinia stores: `auth.ts`.
- `views/` — Page components: `LoginView`, `DashboardView`, `AccountsView`, `HoldingsView`, `SettingsView`.
- `components/layout/AppLayout.vue` — Responsive layout: ≥768px shows left sidebar, <768px shows bottom TabBar.
- `components/charts/` — ECharts wrappers: SankeyChart, NetAssetLineChart, InvestmentLineChart.

Frontend proxies `/api/` to the backend via Nginx in production (`nginx.conf`). In dev, configure Vite proxy in `vite.config.ts`.

## Key Domain Concepts

**Asset accounts** have two dimensions:
- `account_type`: `REGULAR` (cash/fixed/receivable) or `INVESTMENT` (brokerage)
- `asset_category`: `LIQUID`, `FIXED`, `RECEIVABLE`, `INVESTMENT`, `LIABILITY`
- `INVESTMENT` account_type is always `INVESTMENT` asset_category.

**Investment holdings** use Yahoo Finance symbol format:
- A股: `600519.SS` (Shanghai), `000858.SZ` (Shenzhen)
- 港股: `0700.HK`
- 美股: `AAPL`
- FX/汇率: `USDCNY=X`
- Options: OCC format for US, `HSI250328C24000.HK` for HK

**Symbol names**: Yahoo Finance `shortName` is used for all markets. For A股 and 港股, Sina Finance overrides with Chinese names (e.g., 贵州茅台, 腾讯控股).

**Lot size defaults**: CN_A=100, US_OPT/HK_OPT=100, all others=1.

**Price currency** is the source of truth for cost calculations. There is no separate cost currency field.

**Snapshots** (`daily_net_asset_snapshot`, `daily_investment_snapshot`) are generated nightly at 23:59 and used for historical chart data. Triggered manually via `POST /api/snapshots/trigger`.

## Environment Configuration

Copy `.env.example` to `.env` before first run. Key variables: `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET`.

Backend profiles: `dev` (local MySQL defaults) and `prod` (reads from env vars, used in Docker).
