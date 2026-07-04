package com.homewealth.scheduler;

import com.homewealth.service.BenchmarkService;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final MarketDataService marketDataService;
    private final ExchangeRateService exchangeRateService;
    private final BenchmarkService benchmarkService;

    // A股收盘后更新（工作日 15:30 上海时间）
    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Shanghai")
    public void updateCNAStockPrices() {
        log.info("[Scheduler] Updating A-share prices...");
        marketDataService.refreshAllActiveHoldings();
    }

    // 港股收盘后更新（工作日 16:30 上海时间）
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Shanghai")
    public void updateHKStockPrices() {
        log.info("[Scheduler] Updating HK-share prices...");
        marketDataService.refreshAllActiveHoldings();
    }

    // 基准指数收盘点位更新（工作日 16:35 上海时间，A股/港股收盘后）
    @Scheduled(cron = "0 35 16 * * MON-FRI", zone = "Asia/Shanghai")
    public void updateBenchmarkQuotes() {
        log.info("[Scheduler] Updating benchmark index quotes...");
        benchmarkService.fetchAndSaveAll();
    }

    // 基准指数补抓（次日早 6:05，覆盖美股指数收盘）
    @Scheduled(cron = "0 5 6 * * TUE-SAT", zone = "Asia/Shanghai")
    public void updateBenchmarkQuotesAfterUSClose() {
        log.info("[Scheduler] Updating benchmark index quotes (after US close)...");
        benchmarkService.fetchAndSaveAll();
    }

    // 美股收盘后更新（次日早 6:00，美东时间约 17:00 前一天）
    @Scheduled(cron = "0 0 6 * * TUE-SAT", zone = "Asia/Shanghai")
    public void updateUSStockPrices() {
        log.info("[Scheduler] Updating US-stock prices...");
        marketDataService.refreshAllActiveHoldings();
    }

    // 公募基金净值确认后更新（工作日 21:30，QDII 基金可能延迟但会在下一轮更新）
    @Scheduled(cron = "0 30 21 * * MON-FRI", zone = "Asia/Shanghai")
    public void updateFundNAV() {
        log.info("[Scheduler] Updating mutual fund NAV...");
        marketDataService.refreshAllActiveHoldings();
    }

    // 汇率每日 08:00 更新
    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Shanghai")
    public void updateExchangeRates() {
        log.info("[Scheduler] Updating exchange rates...");
        exchangeRateService.refreshRates();
    }
}
