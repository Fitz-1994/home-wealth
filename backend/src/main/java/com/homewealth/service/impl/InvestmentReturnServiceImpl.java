package com.homewealth.service.impl;

import com.homewealth.dto.response.DailyReturnVO;
import com.homewealth.dto.response.MonthlyReturnVO;
import com.homewealth.dto.response.ReturnSummaryVO;
import com.homewealth.dto.response.YearlyReturnVO;
import com.homewealth.mapper.DailyInvestmentSnapshotMapper;
import com.homewealth.model.DailyInvestmentSnapshot;
import com.homewealth.service.InvestmentReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投资收益率计算 —— 日度 Modified Dietz 子周期回报 + 周期链式相乘。
 * 日收益率 r_d = (MV_end - MV_begin - F_d) / (MV_begin + 0.5 * F_d)
 * 周期收益率 = ∏(1 + r_d) - 1
 */
@Service
@RequiredArgsConstructor
public class InvestmentReturnServiceImpl implements InvestmentReturnService {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RATE_SCALE = 8;
    private static final int PCT_SCALE = 4;
    private static final int CNY_SCALE = 2;

    private final DailyInvestmentSnapshotMapper snapshotMapper;

    /** 单日 Modified Dietz 回报中间结果 */
    private static class DayReturn {
        LocalDate date;
        BigDecimal ratio;       // r_d（比率，非百分比）
        BigDecimal beginValue;
        BigDecimal endValue;
        BigDecimal netCashflow;
        BigDecimal pnl;
    }

    @Override
    public List<DailyReturnVO> getDailyReturns(Long userId, LocalDate from, LocalDate to) {
        List<DayReturn> all = computeAll(userId);
        List<DailyReturnVO> result = new ArrayList<>();
        for (DayReturn d : all) {
            if (from != null && d.date.isBefore(from)) continue;
            if (to != null && d.date.isAfter(to)) continue;
            DailyReturnVO vo = new DailyReturnVO();
            vo.setDate(d.date);
            vo.setReturnPct(toPct(d.ratio));
            vo.setBeginValue(scaleCny(d.beginValue));
            vo.setEndValue(scaleCny(d.endValue));
            vo.setNetCashflow(scaleCny(d.netCashflow));
            vo.setPnlCny(scaleCny(d.pnl));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<MonthlyReturnVO> getMonthlyReturns(Long userId, Integer year) {
        List<DayReturn> all = computeAll(userId);
        Map<String, List<DayReturn>> grouped = new LinkedHashMap<>();
        for (DayReturn d : all) {
            if (year != null && d.date.getYear() != year) continue;
            String key = String.format("%04d-%02d", d.date.getYear(), d.date.getMonthValue());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<MonthlyReturnVO> result = new ArrayList<>();
        for (Map.Entry<String, List<DayReturn>> e : grouped.entrySet()) {
            Agg agg = aggregate(e.getValue());
            MonthlyReturnVO vo = new MonthlyReturnVO();
            vo.setPeriod(e.getKey());
            vo.setReturnPct(agg.returnPct);
            vo.setAbsolutePnlCny(agg.pnl);
            vo.setBeginValue(agg.beginValue);
            vo.setEndValue(agg.endValue);
            vo.setNetCashflow(agg.netCashflow);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<YearlyReturnVO> getYearlyReturns(Long userId) {
        List<DayReturn> all = computeAll(userId);
        Map<String, List<DayReturn>> grouped = new LinkedHashMap<>();
        for (DayReturn d : all) {
            String key = String.valueOf(d.date.getYear());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }
        List<YearlyReturnVO> result = new ArrayList<>();
        for (Map.Entry<String, List<DayReturn>> e : grouped.entrySet()) {
            Agg agg = aggregate(e.getValue());
            YearlyReturnVO vo = new YearlyReturnVO();
            vo.setPeriod(e.getKey());
            vo.setReturnPct(agg.returnPct);
            vo.setAbsolutePnlCny(agg.pnl);
            vo.setBeginValue(agg.beginValue);
            vo.setEndValue(agg.endValue);
            vo.setNetCashflow(agg.netCashflow);
            result.add(vo);
        }
        return result;
    }

    @Override
    public ReturnSummaryVO getReturnSummary(Long userId) {
        List<DayReturn> all = computeAll(userId);
        ReturnSummaryVO vo = new ReturnSummaryVO();
        if (all.isEmpty()) {
            BigDecimal zero = BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
            BigDecimal zeroCny = BigDecimal.ZERO.setScale(CNY_SCALE, RoundingMode.HALF_UP);
            vo.setYtdPct(zero); vo.setYtdPnl(zeroCny);
            vo.setMonth1Pct(zero); vo.setMonth1Pnl(zeroCny);
            vo.setMonth3Pct(zero); vo.setMonth3Pnl(zeroCny);
            vo.setInceptionPct(zero); vo.setInceptionPnl(zeroCny);
            return vo;
        }

        LocalDate today = LocalDate.now();
        vo.setInceptionDate(all.get(0).date);

        Agg ytd = aggregate(filter(all, LocalDate.of(today.getYear(), 1, 1)));
        vo.setYtdPct(ytd.returnPct);
        vo.setYtdPnl(ytd.pnl);

        Agg m1 = aggregate(filter(all, today.minusMonths(1)));
        vo.setMonth1Pct(m1.returnPct);
        vo.setMonth1Pnl(m1.pnl);

        Agg m3 = aggregate(filter(all, today.minusMonths(3)));
        vo.setMonth3Pct(m3.returnPct);
        vo.setMonth3Pnl(m3.pnl);

        Agg inception = aggregate(all);
        vo.setInceptionPct(inception.returnPct);
        vo.setInceptionPnl(inception.pnl);

        return vo;
    }

    // ============ 内部计算 ============

    private List<DayReturn> computeAll(Long userId) {
        List<DailyInvestmentSnapshot> snaps = snapshotMapper.findByUserId(userId, null, null);
        List<DayReturn> result = new ArrayList<>();
        for (int i = 0; i < snaps.size(); i++) {
            DailyInvestmentSnapshot cur = snaps.get(i);
            DayReturn dr = new DayReturn();
            dr.date = cur.getSnapshotDate();
            dr.endValue = nz(cur.getTotalValueCny());
            dr.netCashflow = nz(cur.getNetCashflowCny());
            if (i == 0) {
                // 首个快照（功能上线日）没有前一日，收益率视为 0
                dr.beginValue = dr.endValue;
                dr.ratio = BigDecimal.ZERO;
                dr.pnl = BigDecimal.ZERO;
            } else {
                dr.beginValue = nz(snaps.get(i - 1).getTotalValueCny());
                dr.pnl = dr.endValue.subtract(dr.beginValue).subtract(dr.netCashflow);
                BigDecimal denom = dr.beginValue.add(dr.netCashflow.multiply(HALF));
                dr.ratio = denom.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : dr.pnl.divide(denom, RATE_SCALE, RoundingMode.HALF_UP);
            }
            result.add(dr);
        }
        return result;
    }

    private static class Agg {
        BigDecimal returnPct;
        BigDecimal pnl;
        BigDecimal beginValue;
        BigDecimal endValue;
        BigDecimal netCashflow;
    }

    private Agg aggregate(List<DayReturn> days) {
        Agg agg = new Agg();
        if (days.isEmpty()) {
            agg.returnPct = BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
            agg.pnl = BigDecimal.ZERO.setScale(CNY_SCALE, RoundingMode.HALF_UP);
            agg.beginValue = agg.pnl;
            agg.endValue = agg.pnl;
            agg.netCashflow = agg.pnl;
            return agg;
        }
        BigDecimal product = BigDecimal.ONE;
        BigDecimal pnl = BigDecimal.ZERO;
        BigDecimal netCashflow = BigDecimal.ZERO;
        for (DayReturn d : days) {
            product = product.multiply(BigDecimal.ONE.add(d.ratio));
            pnl = pnl.add(d.pnl);
            netCashflow = netCashflow.add(d.netCashflow);
        }
        agg.returnPct = product.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
        agg.pnl = scaleCny(pnl);
        agg.netCashflow = scaleCny(netCashflow);
        agg.beginValue = scaleCny(days.get(0).beginValue);
        agg.endValue = scaleCny(days.get(days.size() - 1).endValue);
        return agg;
    }

    /** 取 date >= from 的日序列 */
    private List<DayReturn> filter(List<DayReturn> all, LocalDate from) {
        List<DayReturn> result = new ArrayList<>();
        for (DayReturn d : all) {
            if (!d.date.isBefore(from)) result.add(d);
        }
        return result;
    }

    private BigDecimal toPct(BigDecimal ratio) {
        return ratio.multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleCny(BigDecimal v) {
        return nz(v).setScale(CNY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
