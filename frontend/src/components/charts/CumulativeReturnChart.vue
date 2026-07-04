<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed } from 'vue'
import * as echarts from 'echarts'
import { useThemeStore } from '@/stores/theme'

export interface CumulativeSeries {
  name: string
  values: (number | null)[]
}

const props = defineProps<{
  dates: string[]
  /** 第一条视为「我的组合」，加粗突出显示 */
  series: CumulativeSeries[]
  /** 图例默认选中的系列名，未列出的默认隐藏（可点击图例开启） */
  defaultSelected?: string[]
}>()

const themeStore = useThemeStore()
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

// 分类色板（经 CVD/对比度校验），避开红/绿以免与涨跌色语义混淆
const PALETTE_LIGHT = ['#2a78d6', '#1baf7a', '#eda100', '#4a3aa7', '#e87ba4', '#eb6834']
const PALETTE_DARK = ['#3987e5', '#199e70', '#c98500', '#9085e9', '#d55181', '#d95926']

const palette = computed(() => (themeStore.isDark ? PALETTE_DARK : PALETTE_LIGHT))
const inkMuted = computed(() => (themeStore.isDark ? 'rgba(255,255,255,0.52)' : '#666666'))
const gridLine = computed(() => (themeStore.isDark ? 'rgba(255,255,255,0.09)' : '#efeff5'))

function fmtPct(v: number): string {
  return `${v >= 0 ? '+' : ''}${v.toFixed(2)}%`
}

function renderChart() {
  if (!chart) return

  const legendSelected: Record<string, boolean> = {}
  if (props.defaultSelected?.length) {
    for (const s of props.series) {
      legendSelected[s.name] = props.defaultSelected.includes(s.name)
    }
  }

  const series = props.series.map((s, i) => {
    const hero = i === 0
    return {
      name: s.name,
      type: 'line' as const,
      data: s.values,
      smooth: false,
      symbol: 'none',
      connectNulls: true,
      lineStyle: { width: hero ? 3 : 1.5, color: palette.value[i % palette.value.length] },
      itemStyle: { color: palette.value[i % palette.value.length] },
      emphasis: { focus: 'series' as const },
      z: hero ? 10 : 2
    }
  })

  chart.setOption(
    {
      legend: {
        top: 0,
        itemWidth: 14,
        data: props.series.map(s => s.name),
        selected: legendSelected,
        textStyle: { color: inkMuted.value }
      },
      tooltip: {
        trigger: 'axis',
        order: 'valueDesc',
        valueFormatter: (v: any) => (v == null ? '—' : fmtPct(Number(v)))
      },
      grid: { left: 16, right: 16, top: 40, bottom: 24, containLabel: true },
      xAxis: {
        type: 'category',
        data: props.dates,
        boundaryGap: false,
        axisLabel: { fontSize: 11, color: inkMuted.value },
        axisLine: { lineStyle: { color: gridLine.value } }
      },
      yAxis: {
        type: 'value',
        scale: true,
        axisLabel: {
          fontSize: 11,
          color: inkMuted.value,
          formatter: (v: number) => fmtPct(v)
        },
        splitLine: { lineStyle: { color: gridLine.value } }
      },
      series
    },
    true
  )
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

watch(
  [() => props.dates, () => props.series, () => themeStore.isDark],
  renderChart,
  { deep: true }
)

const resizeObserver = new ResizeObserver(() => chart?.resize())

onMounted(() => {
  initChart()
  if (chartRef.value) resizeObserver.observe(chartRef.value)
})

onUnmounted(() => {
  resizeObserver.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.chart-container { width: 100%; height: 360px; }
</style>
