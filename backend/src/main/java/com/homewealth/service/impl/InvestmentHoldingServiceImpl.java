package com.homewealth.service.impl;

import com.homewealth.dto.request.CreateHoldingRequest;
import com.homewealth.dto.response.HoldingWithPriceVO;
import com.homewealth.exception.BusinessException;
import com.homewealth.exception.ErrorCode;
import com.homewealth.mapper.AssetAccountMapper;
import com.homewealth.mapper.InvestmentHoldingMapper;
import com.homewealth.model.AssetAccount;
import com.homewealth.model.InvestmentHolding;
import com.homewealth.model.MarketPriceCache;
import com.homewealth.service.ExchangeRateService;
import com.homewealth.service.InvestmentHoldingService;
import com.homewealth.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentHoldingServiceImpl implements InvestmentHoldingService {

    private final InvestmentHoldingMapper holdingMapper;
    private final AssetAccountMapper accountMapper;
    private final MarketDataService marketDataService;
    private final ExchangeRateService exchangeRateService;

    @Override
    public List<HoldingWithPriceVO> listHoldings(Long userId, Long accountId, String market) {
        List<InvestmentHolding> holdings = holdingMapper.findByUserId(userId, accountId, market);
        if (holdings.isEmpty()) return Collections.emptyList();

        List<String> symbols = holdings.stream().map(InvestmentHolding::getSymbol).distinct().toList();
        Map<String, MarketPriceCache> priceMap = marketDataService.getLatestPrices(symbols);

        return holdings.stream()
                .map(h -> buildVO(h, priceMap.get(h.getSymbol())))
                .collect(Collectors.toList());
    }

    @Override
    public HoldingWithPriceVO getHolding(Long userId, Long holdingId) {
        InvestmentHolding holding = holdingMapper.findById(holdingId);
        if (holding == null || !holding.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
        }
        MarketPriceCache price = marketDataService.getLatestPrice(holding.getSymbol());
        return buildVO(holding, price);
    }

    @Override
    public HoldingWithPriceVO createHolding(Long userId, CreateHoldingRequest request) {
        AssetAccount account = accountMapper.findById(request.getAccountId());
        if (account == null || !account.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (!"INVESTMENT".equals(account.getAccountType())) {
            throw new BusinessException(ErrorCode.NOT_INVESTMENT_ACCOUNT);
        }

        String symbol = request.getSymbol().toUpperCase();
        // 同一账户下同一标的只保留一条记录：存在则覆盖，不存在才新建
        InvestmentHolding existing = holdingMapper.findByAccountAndSymbol(request.getAccountId(), symbol);
        InvestmentHolding holding;
        if (existing != null) {
            if (request.getSymbolName() != null) existing.setSymbolName(request.getSymbolName());
            existing.setQuantity(request.getQuantity());
            existing.setCostPrice(request.getCostPrice());
            existing.setPriceCurrency(request.getPriceCurrency());
            existing.setNote(request.getNote());
            holdingMapper.update(existing);
            holding = existing;
        } else {
            holding = new InvestmentHolding();
            holding.setAccountId(request.getAccountId());
            holding.setUserId(userId);
            holding.setSymbol(symbol);
            holding.setSymbolName(request.getSymbolName());
            holding.setMarket(request.getMarket());
            holding.setQuantity(request.getQuantity());
            holding.setCostPrice(request.getCostPrice());
            holding.setPriceCurrency(request.getPriceCurrency());
            holding.setNote(request.getNote());
            holding.setIsActive(true);
            holdingMapper.insert(holding);
        }

        // 立即获取行情
        marketDataService.refreshSymbols(List.of(holding.getSymbol()));
        MarketPriceCache price = marketDataService.getLatestPrice(holding.getSymbol());
        return buildVO(holding, price);
    }

    @Override
    public HoldingWithPriceVO updateHolding(Long userId, Long holdingId, CreateHoldingRequest request) {
        InvestmentHolding holding = holdingMapper.findById(holdingId);
        if (holding == null || !holding.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
        }
        if (request.getSymbolName() != null) holding.setSymbolName(request.getSymbolName());
        if (request.getQuantity() != null) holding.setQuantity(request.getQuantity());
        if (request.getCostPrice() != null) holding.setCostPrice(request.getCostPrice());
        if (request.getPriceCurrency() != null) holding.setPriceCurrency(request.getPriceCurrency());
        if (request.getNote() != null) holding.setNote(request.getNote());
        holdingMapper.update(holding);

        MarketPriceCache price = marketDataService.getLatestPrice(holding.getSymbol());
        return buildVO(holding, price);
    }

    @Override
    public void closeHolding(Long userId, Long holdingId) {
        InvestmentHolding holding = holdingMapper.findById(holdingId);
        if (holding == null || !holding.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.HOLDING_NOT_FOUND);
        }
        holdingMapper.deactivate(holdingId, userId);
    }

    @Override
    public List<HoldingWithPriceVO> batchImport(Long userId, List<CreateHoldingRequest> requests) {
        List<HoldingWithPriceVO> results = new ArrayList<>();
        for (CreateHoldingRequest request : requests) {
            HoldingWithPriceVO vo = createHolding(userId, request);
            results.add(vo);
        }
        return results;
    }

    @Override
    public HoldingWithPriceVO validateSymbol(String symbol, String priceCurrency) {
        marketDataService.refreshSymbols(List.of(symbol.toUpperCase()));
        MarketPriceCache price = marketDataService.getLatestPrice(symbol.toUpperCase());
        if (price == null || price.getIsStale()) {
            throw new BusinessException(ErrorCode.SYMBOL_INVALID);
        }
        HoldingWithPriceVO vo = new HoldingWithPriceVO();
        vo.setSymbol(price.getSymbol());
        vo.setSymbolName(price.getSymbolName());
        vo.setMarket(price.getMarket());
        vo.setCurrentPrice(price.getPrice());
        vo.setPriceCurrency(price.getCurrency());
        vo.setMarketValueCny(price.getCnyPrice());
        return vo;
    }

    private HoldingWithPriceVO buildVO(InvestmentHolding holding, MarketPriceCache price) {
        HoldingWithPriceVO vo = new HoldingWithPriceVO();
        vo.setId(holding.getId());
        vo.setAccountId(holding.getAccountId());
        vo.setSymbol(holding.getSymbol());
        vo.setMarket(holding.getMarket());
        vo.setQuantity(holding.getQuantity());
        vo.setCostPrice(holding.getCostPrice());

        // 优先使用行情缓存中的标的名称（来自 Yahoo Finance shortName）
        if (price != null && price.getSymbolName() != null && !price.getSymbolName().isEmpty()) {
            vo.setSymbolName(price.getSymbolName());
        } else {
            vo.setSymbolName(holding.getSymbolName());
        }

        if (price != null) {
            vo.setCurrentPrice(price.getPrice());
            vo.setPriceCurrency(price.getCurrency());
            vo.setPriceChangePct(price.getChangePct());
            vo.setPriceUpdatedAt(price.getFetchedAt());
            vo.setStale(price.getIsStale());

            // 市值 = 数量 × 单价 × cny汇率
            BigDecimal marketValue = holding.getQuantity()
                    .multiply(price.getPrice());
            BigDecimal marketValueCny = exchangeRateService.toCny(marketValue, price.getCurrency());
            vo.setMarketValueCny(marketValueCny);

            // 浮盈
            if (holding.getCostPrice() != null) {
                BigDecimal costTotal = holding.getCostPrice()
                        .multiply(holding.getQuantity());
                BigDecimal costCny = exchangeRateService.toCny(costTotal, holding.getPriceCurrency());
                BigDecimal pnl = marketValueCny.subtract(costCny);
                vo.setUnrealizedPnl(pnl);
                if (costCny.compareTo(BigDecimal.ZERO) != 0) {
                    vo.setUnrealizedPnlPct(pnl.divide(costCny, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
                }
            }
        } else {
            vo.setStale(true);
        }
        return vo;
    }
}
