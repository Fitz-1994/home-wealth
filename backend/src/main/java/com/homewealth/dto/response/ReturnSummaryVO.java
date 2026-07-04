package com.homewealth.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ReturnSummaryVO {
    private LocalDate inceptionDate;     // 首个快照日期

    private BigDecimal ytdPct;
    private BigDecimal ytdPnl;

    private BigDecimal month1Pct;
    private BigDecimal month1Pnl;

    private BigDecimal month3Pct;
    private BigDecimal month3Pnl;

    private BigDecimal inceptionPct;
    private BigDecimal inceptionPnl;
}
