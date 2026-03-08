package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ParsedHoldingVO {
    private String symbol;        // 股票代码（Yahoo Finance格式，如 600519.SS / 0700.HK / AAPL）
    private String symbolName;    // 股票名称
    private BigDecimal quantity;  // 持仓数量
    private BigDecimal costPrice; // 成本价/均价
    private String priceCurrency; // 价格币种 CNY/USD/HKD
    private String market;        // CN_A / HK / US / FX / HK_OPT / US_OPT
    private Integer lotSize;      // 手数，A股默认100，其他默认1
    private String note;          // 备注（如解析置信度低时的说明）
}
