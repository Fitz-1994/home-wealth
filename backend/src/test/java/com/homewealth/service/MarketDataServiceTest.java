package com.homewealth.service;

import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.mapper.MarketPriceCacheMapper;
import com.homewealth.market.MarketQuote;
import com.homewealth.market.YahooFinanceFetcher;
import com.homewealth.model.MarketPriceCache;
import com.homewealth.service.impl.MarketDataServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @Mock
    private MarketPriceCacheMapper priceCacheMapper;
    @Mock
    private InvestmentHoldingMapper holdingMapper;
    @Mock
    private YahooFinanceFetcher yahooFetcher;
    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private MarketDataServiceImpl marketDataService;

    @Test
    @DisplayName("inferMarket - A股上交所识别")
    void testInferMarket_cnA_SS() throws Exception {
        assertThat(invokeInferMarket("600519.SS")).isEqualTo("CN_A");
        assertThat(invokeInferMarket("601398.SS")).isEqualTo("CN_A");
    }

    @Test
    @DisplayName("inferMarket - A股深交所识别")
    void testInferMarket_cnA_SZ() throws Exception {
        assertThat(invokeInferMarket("000858.SZ")).isEqualTo("CN_A");
        assertThat(invokeInferMarket("300750.SZ")).isEqualTo("CN_A");
    }

    @Test
    @DisplayName("inferMarket - 港股识别")
    void testInferMarket_hk() throws Exception {
        assertThat(invokeInferMarket("0700.HK")).isEqualTo("HK");
        assertThat(invokeInferMarket("9988.HK")).isEqualTo("HK");
    }

    @Test
    @DisplayName("inferMarket - 美股识别")
    void testInferMarket_us() throws Exception {
        assertThat(invokeInferMarket("AAPL")).isEqualTo("US");
        assertThat(invokeInferMarket("VOO")).isEqualTo("US");
        assertThat(invokeInferMarket("TSLA")).isEqualTo("US");
    }

    @Test
    @DisplayName("inferMarket - 汇率识别")
    void testInferMarket_fx() throws Exception {
        assertThat(invokeInferMarket("USDCNY=X")).isEqualTo("FX");
        assertThat(invokeInferMarket("EURCNY=X")).isEqualTo("FX");
        assertThat(invokeInferMarket("HKDCNY=X")).isEqualTo("FX");
    }

    @Test
    @DisplayName("inferMarket - 美股期权识别")
    void testInferMarket_usOpt() throws Exception {
        assertThat(invokeInferMarket("AAPL250321C00150000")).isEqualTo("US_OPT");
        assertThat(invokeInferMarket("SPY250620P05000000")).isEqualTo("US_OPT");
    }

    @Test
    @DisplayName("refreshSymbols - 获取行情并写入缓存")
    void testRefreshSymbols_savesCache() {
        MarketQuote quote = MarketQuote.builder()
                .symbol("AAPL")
                .symbolName("Apple Inc.")
                .price(BigDecimal.valueOf(257.46))
                .currency("USD")
                .changePct(BigDecimal.valueOf(-1.09))
                .build();

        when(yahooFetcher.fetchQuotes(anyList())).thenReturn(Map.of("AAPL", quote));
        when(exchangeRateService.getRate("USD", "CNY")).thenReturn(BigDecimal.valueOf(7.24));

        marketDataService.refreshSymbols(List.of("AAPL"));

        verify(priceCacheMapper, times(1)).upsert(any(MarketPriceCache.class));
        verify(priceCacheMapper, never()).markStale(any());
    }

    @Test
    @DisplayName("refreshSymbols - 获取失败时标记stale")
    void testRefreshSymbols_marksStaleOnMissing() {
        when(yahooFetcher.fetchQuotes(anyList())).thenReturn(Map.of());

        marketDataService.refreshSymbols(List.of("INVALID_SYM"));

        verify(priceCacheMapper, never()).upsert(any());
        verify(priceCacheMapper, times(1)).markStale("INVALID_SYM");
    }

    @Test
    @DisplayName("refreshAllActiveHoldings - 无持仓时不调用Yahoo接口")
    void testRefreshAllActiveHoldings_emptyHoldings() {
        when(holdingMapper.findAllActiveSymbols()).thenReturn(List.of());

        marketDataService.refreshAllActiveHoldings();

        verify(yahooFetcher, never()).fetchQuotes(any());
    }

    @Test
    @DisplayName("getLatestPrices - 空列表返回空Map")
    void testGetLatestPrices_empty() {
        Map<String, MarketPriceCache> result = marketDataService.getLatestPrices(List.of());
        assertThat(result).isEmpty();
        verify(priceCacheMapper, never()).findLatestBySymbols(any());
    }

    private String invokeInferMarket(String symbol) throws Exception {
        Method m = MarketDataServiceImpl.class.getDeclaredMethod("inferMarket", String.class);
        m.setAccessible(true);
        return (String) m.invoke(marketDataService, symbol);
    }
}
