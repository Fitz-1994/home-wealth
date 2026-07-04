package com.homewealth.enums;

public enum InvestmentTxnType {
    BUY,        // 买入
    SELL,       // 卖出
    DIVIDEND,   // 分红到账（一般由系统从 dividend_income_record 联动写入）
    FEE,        // 手续费/税
    CASH_IN,    // 入金（外部转入投资账户）
    CASH_OUT,   // 出金（投资账户转出至外部）
    OPENING     // 开仓初始化（功能上线时为现有持仓合成的一笔）
}
