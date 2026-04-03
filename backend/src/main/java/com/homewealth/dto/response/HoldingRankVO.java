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
        private Long groupId;               // 分组ID（null表示独立持仓）
        private String groupName;           // 分组名称
        private Integer memberCount;        // 分组成员数
    }
}
