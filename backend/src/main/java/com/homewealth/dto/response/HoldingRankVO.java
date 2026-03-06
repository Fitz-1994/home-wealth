package com.homewealth.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class HoldingRankVO {
    private List<HoldingRankItem> items;
    private BigDecimal totalValueCny;

    @Data
    public static class HoldingRankItem {
        private Long holdingId;
        private String symbol;
        private String symbolName;
        private String market;
        private BigDecimal quantity;
        private BigDecimal currentPrice;
        private String priceCurrency;
        private BigDecimal marketValueCny;
        private BigDecimal ratio;           // 占总投资比例（0~1）
        private BigDecimal priceChangePct;
    }
}
