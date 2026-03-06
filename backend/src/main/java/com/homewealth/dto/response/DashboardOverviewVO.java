package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DashboardOverviewVO {
    private BigDecimal totalAssetCny;
    private BigDecimal totalLiabilityCny;
    private BigDecimal netAssetCny;
    private Map<String, BigDecimal> categories;   // LIQUID/FIXED/RECEIVABLE/INVESTMENT/LIABILITY -> CNY amount
    private BigDecimal investmentMarketValue;
    private LocalDateTime lastUpdated;
}
