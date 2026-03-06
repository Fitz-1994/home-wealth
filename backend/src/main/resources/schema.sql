-- ============================================
-- 家庭资产管理系统 数据库初始化脚本
-- charset: utf8mb4, engine: InnoDB
-- ============================================

CREATE DATABASE IF NOT EXISTS home_wealth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE home_wealth;

-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `users` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`     VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`     VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  `display_name` VARCHAR(100)          COMMENT '显示名称',
  `role`         VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER',
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 2. API Key 表（供外部服务调用）
-- ============================================
CREATE TABLE IF NOT EXISTS `api_key` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL COMMENT '所属用户',
  `key_name`     VARCHAR(100) NOT NULL COMMENT '备注名称（如：openclaw-skill）',
  `key_value`    VARCHAR(64)  NOT NULL COMMENT 'SHA256哈希存储',
  `key_prefix`   VARCHAR(12)  NOT NULL COMMENT '明文前缀，用于列表展示（如 hw_sk_Ab3x）',
  `is_active`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否有效',
  `last_used_at` DATETIME              COMMENT '最后使用时间',
  `expires_at`   DATETIME              COMMENT 'NULL表示永不过期',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key_value` (`key_value`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_apikey_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key表';

-- ============================================
-- 3. 资产账户表
-- ============================================
-- account_type: REGULAR(普通账户) / INVESTMENT(投资账户)
-- asset_category: LIQUID(流动资金) / FIXED(固定资产) / RECEIVABLE(应收款)
--                 / INVESTMENT(投资理财) / LIABILITY(负债)
-- 注意: account_type=INVESTMENT 时 asset_category 固定为 INVESTMENT
CREATE TABLE IF NOT EXISTS `asset_account` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `user_id`        BIGINT       NOT NULL COMMENT '所属用户',
  `account_name`   VARCHAR(100) NOT NULL COMMENT '账户名称',
  `account_type`   VARCHAR(20)  NOT NULL COMMENT 'REGULAR/INVESTMENT',
  `asset_category` VARCHAR(20)  NOT NULL COMMENT 'LIQUID/FIXED/RECEIVABLE/INVESTMENT/LIABILITY',
  `currency`       VARCHAR(10)  NOT NULL DEFAULT 'CNY' COMMENT '主币种',
  `description`    VARCHAR(500)          COMMENT '备注描述',
  `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `is_active`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否激活',
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_account_type` (`account_type`),
  CONSTRAINT `fk_account_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产账户表';

-- ============================================
-- 4. 普通账户资产记录表（account_type=REGULAR）
-- ============================================
-- 每次修改插入新记录，is_current=1 表示当前有效记录
CREATE TABLE IF NOT EXISTS `regular_account_record` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `account_id`  BIGINT         NOT NULL COMMENT '关联账户ID',
  `user_id`     BIGINT         NOT NULL COMMENT '所属用户（冗余）',
  `amount`      DECIMAL(20, 4) NOT NULL COMMENT '金额（账户本币）',
  `currency`    VARCHAR(10)    NOT NULL COMMENT '币种',
  `cny_rate`    DECIMAL(15, 6) NOT NULL DEFAULT 1.000000 COMMENT '记录时对CNY汇率',
  `cny_amount`  DECIMAL(20, 4) NOT NULL COMMENT '折算人民币金额',
  `record_date` DATE           NOT NULL COMMENT '记录日期',
  `is_current`  TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '是否当前有效记录',
  `note`        VARCHAR(500)            COMMENT '备注',
  `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by`  BIGINT                  COMMENT '创建人',
  PRIMARY KEY (`id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_user_date` (`user_id`, `record_date`),
  KEY `idx_current` (`account_id`, `is_current`),
  CONSTRAINT `fk_record_account` FOREIGN KEY (`account_id`) REFERENCES `asset_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通账户资产记录表';

-- ============================================
-- 5. 投资持仓明细表（account_type=INVESTMENT）
-- ============================================
-- market: CN_A(A股) / HK(港股) / US(美股) / HK_OPT(港股期权) / US_OPT(美股期权) / FX(汇率/外汇)
-- symbol 为 Yahoo Finance 格式：600519.SS / 0700.HK / AAPL / USDCNY=X
CREATE TABLE IF NOT EXISTS `investment_holding` (
  `id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '持仓ID',
  `account_id`     BIGINT         NOT NULL COMMENT '关联投资账户ID',
  `user_id`        BIGINT         NOT NULL COMMENT '所属用户',
  `symbol`         VARCHAR(50)    NOT NULL COMMENT '标的代码（Yahoo Finance格式）',
  `symbol_name`    VARCHAR(200)            COMMENT '标的名称',
  `market`         VARCHAR(20)    NOT NULL COMMENT 'CN_A/HK/US/HK_OPT/US_OPT/FX',
  `quantity`       DECIMAL(20, 6) NOT NULL COMMENT '持仓数量',
  `cost_price`     DECIMAL(20, 6)          COMMENT '成本价（可选）',
  `cost_currency`  VARCHAR(10)             COMMENT '成本价币种',
  `price_currency` VARCHAR(10)    NOT NULL COMMENT '行情价格币种',
  `lot_size`       INT            NOT NULL DEFAULT 1 COMMENT '每手股数/合约乘数',
  `is_active`      TINYINT(1)     NOT NULL DEFAULT 1 COMMENT '是否持有中',
  `note`           VARCHAR(500)            COMMENT '备注',
  `created_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_account_id` (`account_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_symbol` (`symbol`),
  CONSTRAINT `fk_holding_account` FOREIGN KEY (`account_id`) REFERENCES `asset_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投资持仓明细表';

-- ============================================
-- 6. 行情价格缓存表
-- ============================================
CREATE TABLE IF NOT EXISTS `market_price_cache` (
  `id`          BIGINT         NOT NULL AUTO_INCREMENT,
  `symbol`      VARCHAR(50)    NOT NULL COMMENT '标的代码',
  `symbol_name` VARCHAR(200)            COMMENT '标的名称',
  `market`      VARCHAR(20)    NOT NULL COMMENT '市场类型',
  `price`       DECIMAL(20, 6) NOT NULL COMMENT '当前价格（原始币种）',
  `currency`    VARCHAR(10)    NOT NULL COMMENT '价格币种',
  `cny_rate`    DECIMAL(15, 6) NOT NULL DEFAULT 1.000000 COMMENT '对CNY汇率',
  `cny_price`   DECIMAL(20, 6) NOT NULL COMMENT '折算人民币价格',
  `change_pct`  DECIMAL(10, 4)          COMMENT '涨跌幅%',
  `trade_date`  DATE           NOT NULL COMMENT '行情日期',
  `source`      VARCHAR(20)    NOT NULL DEFAULT 'YAHOO' COMMENT '数据来源: YAHOO/SINA/MANUAL',
  `is_stale`    TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否过期数据',
  `fetched_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间',
  `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_symbol_date` (`symbol`, `trade_date`),
  KEY `idx_trade_date` (`trade_date`),
  KEY `idx_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行情价格缓存表';

-- ============================================
-- 7. 汇率表
-- ============================================
CREATE TABLE IF NOT EXISTS `exchange_rate` (
  `id`            BIGINT         NOT NULL AUTO_INCREMENT,
  `from_currency` VARCHAR(10)    NOT NULL COMMENT '原始币种',
  `to_currency`   VARCHAR(10)    NOT NULL DEFAULT 'CNY' COMMENT '目标币种',
  `rate`          DECIMAL(15, 6) NOT NULL COMMENT '汇率（1单位from_currency = rate to_currency）',
  `rate_date`     DATE           NOT NULL COMMENT '汇率日期',
  `source`        VARCHAR(20)    NOT NULL DEFAULT 'YAHOO',
  `created_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency_date` (`from_currency`, `to_currency`, `rate_date`),
  KEY `idx_rate_date` (`rate_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='汇率表';

-- ============================================
-- 8. 每日净资产快照表
-- ============================================
CREATE TABLE IF NOT EXISTS `daily_net_asset_snapshot` (
  `id`                  BIGINT         NOT NULL AUTO_INCREMENT,
  `user_id`             BIGINT         NOT NULL COMMENT '用户ID',
  `snapshot_date`       DATE           NOT NULL COMMENT '快照日期',
  `total_asset_cny`     DECIMAL(20, 4) NOT NULL COMMENT '总资产（CNY）',
  `total_liability_cny` DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '总负债（CNY）',
  `net_asset_cny`       DECIMAL(20, 4) NOT NULL COMMENT '净资产（CNY）',
  `liquid_cny`          DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '流动资金（CNY）',
  `fixed_cny`           DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '固定资产（CNY）',
  `receivable_cny`      DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '应收款（CNY）',
  `investment_cny`      DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '投资理财（CNY）',
  `liability_cny`       DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '负债（CNY）',
  `note`                VARCHAR(500)            COMMENT '备注',
  `created_at`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `snapshot_date`),
  KEY `idx_snapshot_date` (`snapshot_date`),
  CONSTRAINT `fk_net_snapshot_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日净资产快照表';

-- ============================================
-- 9. 每日投资资产快照表
-- ============================================
CREATE TABLE IF NOT EXISTS `daily_investment_snapshot` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT         NOT NULL COMMENT '用户ID',
  `snapshot_date`    DATE           NOT NULL COMMENT '快照日期',
  `total_value_cny`  DECIMAL(20, 4) NOT NULL COMMENT '投资总市值（CNY）',
  `total_cost_cny`   DECIMAL(20, 4)          COMMENT '投资总成本（CNY，可选）',
  `unrealized_pnl`   DECIMAL(20, 4)          COMMENT '浮动盈亏（CNY，可选）',
  `cn_a_value_cny`   DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT 'A股市值',
  `hk_value_cny`     DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '港股市值',
  `us_value_cny`     DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '美股市值',
  `hk_opt_value_cny` DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '港股期权市值',
  `us_opt_value_cny` DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '美股期权市值',
  `other_value_cny`  DECIMAL(20, 4) NOT NULL DEFAULT 0 COMMENT '其他投资市值',
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `snapshot_date`),
  CONSTRAINT `fk_inv_snapshot_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日投资资产快照表';

-- ============================================
-- 初始化种子汇率数据（定时任务启动后会自动更新）
-- ============================================
INSERT IGNORE INTO `exchange_rate` (`from_currency`, `to_currency`, `rate`, `rate_date`, `source`) VALUES
('USD', 'CNY', 7.2500, CURDATE(), 'MANUAL'),
('HKD', 'CNY', 0.9300, CURDATE(), 'MANUAL'),
('EUR', 'CNY', 7.8000, CURDATE(), 'MANUAL'),
('JPY', 'CNY', 0.0480, CURDATE(), 'MANUAL'),
('GBP', 'CNY', 9.1000, CURDATE(), 'MANUAL'),
('CNY', 'CNY', 1.0000, CURDATE(), 'MANUAL');
