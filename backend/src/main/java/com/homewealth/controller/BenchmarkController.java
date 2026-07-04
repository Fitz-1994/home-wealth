package com.homewealth.controller;

import com.homewealth.dto.response.ApiResponse;
import com.homewealth.dto.response.BenchmarkInfoVO;
import com.homewealth.dto.response.BenchmarkReturnVO;
import com.homewealth.dto.response.BenchmarkSeriesVO;
import com.homewealth.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping
    public ApiResponse<List<BenchmarkInfoVO>> list() {
        return ApiResponse.success(benchmarkService.listBenchmarks());
    }

    @GetMapping("/{symbol}/returns")
    public ApiResponse<List<BenchmarkReturnVO>> returns(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "monthly") String granularity,
            @RequestParam(required = false) Integer year) {
        return ApiResponse.success(benchmarkService.getReturns(symbol, granularity, year));
    }

    /** 所有基准指数的日收盘序列（用于累计收益曲线对比） */
    @GetMapping("/series")
    public ApiResponse<List<BenchmarkSeriesVO>> series(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(benchmarkService.getDailySeries(from, to));
    }

    /** 手动触发历史回填（首次部署或补数据用，约 2 年日线） */
    @PostMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch() {
        benchmarkService.backfillHistory();
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        return ApiResponse.success(body);
    }
}
