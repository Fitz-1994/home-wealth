package com.homewealth.service.impl;

import com.homewealth.dto.response.DashboardOverviewVO;
import com.homewealth.dto.response.DividendSummaryVO;
import com.homewealth.dto.response.MonthlyReturnVO;
import com.homewealth.dto.response.ReturnSummaryVO;
import com.homewealth.mapper.UserMapper;
import com.homewealth.model.User;
import com.homewealth.notify.FeishuClient;
import com.homewealth.service.DashboardService;
import com.homewealth.service.DividendService;
import com.homewealth.service.InvestmentReturnService;
import com.homewealth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日飞书日报编排 —— 汇聚投资收益 / 分红汇总 / 提前退休进度 / 净资产总览，拼卡片并推送。
 * 报告日为「昨日」（对齐昨晚 23:59 生成的快照），09:00 次日推送时数据完整。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 各资产类别展示顺序与中文名 */
    private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>() {{
        put("LIQUID", "流动");
        put("FIXED", "固定");
        put("INVESTMENT", "投资");
        put("RECEIVABLE", "应收");
    }};

    private final UserMapper userMapper;
    private final InvestmentReturnService investmentReturnService;
    private final DividendService dividendService;
    private final DashboardService dashboardService;
    private final FeishuClient feishuClient;

    @Value("${feishu.retirement-target-cny:200000}")
    private BigDecimal retirementTarget;

    /** 日报白名单：逗号分隔的用户 id；留空表示所有活跃用户 */
    @Value("${feishu.digest-user-ids:}")
    private String digestUserIds;

    @Override
    public void pushDailyDigest() {
        if (!feishuClient.isEnabled()) {
            log.info("[Notify] Feishu disabled, skip daily digest");
            return;
        }
        List<Long> userIds = resolveDigestUserIds();
        if (userIds.isEmpty()) {
            log.info("[Notify] no target user for daily digest, skip");
            return;
        }
        for (Long userId : userIds) {
            try {
                pushDailyDigestForUser(userId);
            } catch (Exception e) {
                log.error("[Notify] daily digest failed for userId={}", userId, e);
            }
        }
    }

    /**
     * 解析日报目标用户：配置了白名单则取「白名单 ∩ 活跃用户」（顺序按白名单），
     * 白名单为空则回退为所有活跃用户。非法 id 段忽略。
     */
    private List<Long> resolveDigestUserIds() {
        List<Long> active = userMapper.findAllActiveUserIds();
        if (isBlank(digestUserIds)) {
            return active;
        }
        java.util.Set<Long> activeSet = new java.util.HashSet<>(active);
        List<Long> result = new ArrayList<>();
        for (String part : digestUserIds.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try {
                Long id = Long.valueOf(s);
                if (activeSet.contains(id) && !result.contains(id)) {
                    result.add(id);
                }
            } catch (NumberFormatException e) {
                log.warn("[Notify] ignore invalid digest user id: {}", s);
            }
        }
        return result;
    }

    @Override
    public void pushDailyDigestForUser(Long userId) {
        LocalDate reportDate = LocalDate.now(ZONE).minusDays(1);

        MonthlyReturnVO monthReturn = currentMonthReturn(userId, reportDate);
        ReturnSummaryVO summary = investmentReturnService.getReturnSummary(userId);
        DividendSummaryVO dividend = dividendService.getDividendSummary(userId);
        DashboardOverviewVO overview = dashboardService.getOverview(userId);

        User user = userMapper.findById(userId);
        String displayName = user == null ? null
                : (isBlank(user.getDisplayName()) ? user.getUsername() : user.getDisplayName());

        Map<String, Object> card = buildCard(reportDate, displayName, monthReturn, summary, dividend, overview);
        feishuClient.sendCard(null, card);
    }

    /** 取报告日所在自然月的收益（本月至今） */
    private MonthlyReturnVO currentMonthReturn(Long userId, LocalDate reportDate) {
        String key = String.format("%04d-%02d", reportDate.getYear(), reportDate.getMonthValue());
        for (MonthlyReturnVO m : investmentReturnService.getMonthlyReturns(userId, reportDate.getYear())) {
            if (key.equals(m.getPeriod())) return m;
        }
        return null;
    }

    // ============ 卡片构建 ============

    private Map<String, Object> buildCard(LocalDate reportDate, String displayName,
                                          MonthlyReturnVO monthReturn, ReturnSummaryVO summary,
                                          DividendSummaryVO dividend, DashboardOverviewVO overview) {
        String title = "📊 家庭资产日报 · " + reportDate.format(DateTimeFormatter.ISO_DATE)
                + (isBlank(displayName) ? "" : " · " + displayName);
        String mdLabel = reportDate.format(DateTimeFormatter.ofPattern("MM-dd"));

        List<String> lines = new ArrayList<>();
        lines.add(netAssetBlock(overview));
        lines.add("");
        lines.add(returnBlock(mdLabel, monthReturn, summary));
        lines.add("");
        lines.add(dividendBlock(dividend));
        lines.add("");
        lines.add(retirementBlock(dividend));

        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", String.join("\n", lines));

        Map<String, Object> divElement = new LinkedHashMap<>();
        divElement.put("tag", "div");
        divElement.put("text", text);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", Map.of("tag", "plain_text", "content", title));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", header);
        card.put("elements", List.of(divElement));
        return card;
    }

    /** 💰 净资产总览 */
    private String netAssetBlock(DashboardOverviewVO o) {
        if (o == null) return "💰 **净资产**　暂无数据";
        BigDecimal net = nz(o.getNetAssetCny());
        BigDecimal total = nz(o.getTotalAssetCny());
        BigDecimal liab = nz(o.getTotalLiabilityCny());
        StringBuilder sb = new StringBuilder();
        sb.append("💰 **净资产**　").append(money(net))
                .append("（总资产 ").append(money(total))
                .append(" / 负债 ").append(money(liab)).append("）");

        String cats = categoryLine(o.getCategories(), total);
        if (!cats.isEmpty()) {
            sb.append("\n　").append(cats);
        }
        return sb.toString();
    }

    private String categoryLine(Map<String, BigDecimal> categories, BigDecimal total) {
        if (categories == null || categories.isEmpty() || total.signum() <= 0) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : CATEGORY_LABELS.entrySet()) {
            BigDecimal v = nz(categories.get(e.getKey()));
            if (v.signum() <= 0) continue;
            BigDecimal pct = v.multiply(HUNDRED).divide(total, 0, RoundingMode.HALF_UP);
            parts.add(e.getValue() + " " + pct + "%");
        }
        return String.join(" · ", parts);
    }

    /** 📈 投资收益：本月 / 本年 / 全部，每行含金额 + 收益率 + 红绿符号 */
    private String returnBlock(String dateLabel, MonthlyReturnVO month, ReturnSummaryVO s) {
        StringBuilder sb = new StringBuilder();
        sb.append("📈 **投资收益（截至 ").append(dateLabel).append("）**");
        if (month != null) {
            sb.append("\n　").append(returnLine("本月", nz(month.getReturnPct()), nz(month.getAbsolutePnlCny())));
        } else {
            sb.append("\n　本月 暂无数据");
        }
        if (s != null) {
            sb.append("\n　").append(returnLine("本年", nz(s.getYtdPct()), nz(s.getYtdPnl())));
            sb.append("\n　").append(returnLine("全部", nz(s.getInceptionPct()), nz(s.getInceptionPnl())));
        }
        return sb.toString();
    }

    /** 单行收益：标签 🟢/🔴 +¥金额（+x.xx%） */
    private String returnLine(String label, BigDecimal pct, BigDecimal pnl) {
        return label + " " + gainEmoji(pnl) + " " + signedMoney(pnl) + "（" + signedPct(pct) + "）";
    }

    /** 🎁 分红现金流：本月 / 本年 / 滚动12月 各一行 */
    private String dividendBlock(DividendSummaryVO d) {
        if (d == null) return "🎁 **分红现金流**　暂无数据";
        return "🎁 **分红现金流**"
                + "\n　本月 " + money(nz(d.getCurrentMonthTotal()))
                + "\n　本年 " + money(nz(d.getCurrentYearTotal()))
                + "\n　滚动12月 " + money(nz(d.getLast12MonthsTotal()));
    }

    /** 🏖️ 提前退休进度（滚动12月分红 / 年度目标） */
    private String retirementBlock(DividendSummaryVO d) {
        BigDecimal rolling = d == null ? BigDecimal.ZERO : nz(d.getLast12MonthsTotal());
        if (retirementTarget == null || retirementTarget.signum() <= 0) {
            return "🏖️ **提前退休进度**　滚动12月分红 " + money(rolling);
        }
        BigDecimal ratio = rolling.divide(retirementTarget, 4, RoundingMode.HALF_UP);
        int pct = ratio.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).intValue();
        int filled = Math.max(0, Math.min(10, Math.round(pct / 10f)));
        String bar = "█".repeat(filled) + "░".repeat(10 - filled);
        return "🏖️ **提前退休进度**　" + bar + " " + pct + "%"
                + "（" + money(rolling) + " / 年目标 " + money(retirementTarget) + "）";
    }

    // ============ 格式化工具 ============

    private String money(BigDecimal v) {
        return "¥" + new DecimalFormat("#,##0").format(nz(v));
    }

    private String signedMoney(BigDecimal v) {
        v = nz(v);
        String s = new DecimalFormat("#,##0").format(v.abs());
        return (v.signum() < 0 ? "-¥" : "+¥") + s;
    }

    /** 入参已是百分数值（如 0.42 表示 0.42%），直接加符号与 % */
    private String signedPct(BigDecimal v) {
        v = nz(v).setScale(2, RoundingMode.HALF_UP);
        String sign = v.signum() < 0 ? "" : "+";   // 负号由 BigDecimal 自带
        return sign + v.stripTrailingZeros().toPlainString() + "%";
    }

    private String gainEmoji(BigDecimal v) {
        return nz(v).signum() < 0 ? "🔴" : "🟢";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
