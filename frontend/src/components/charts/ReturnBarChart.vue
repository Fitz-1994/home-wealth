<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  categories: string[]
  values: number[]
  unit: 'pct' | 'cny'
  overlayName?: string
  overlayValues?: (number | null)[]
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

// 中国习惯：涨红跌绿
const UP = '#d03050'
const DOWN = '#18a058'

function fmt(v: number): string {
  if (props.unit === 'pct') return `${v.toFixed(2)}%`
  const abs = Math.abs(v)
  let s: string
  if (abs >= 1e8) s = `${(v / 1e8).toFixed(2)}亿`
  else if (abs >= 1e4) s = `${(v / 1e4).toFixed(2)}万`
  else s = v.toFixed(2)
  return s
}

function renderChart() {
  if (!chart) return
  const hasOverlay = !!props.overlayName && Array.isArray(props.overlayValues) && props.overlayValues.length > 0
  const myName = props.unit === 'pct' ? '收益率' : '收益额'

  const series: any[] = [{
    name: myName,
    type: 'bar',
    data: props.values.map(v => ({
      value: v,
      itemStyle: { color: v >= 0 ? UP : DOWN }
    })),
    barMaxWidth: 36,
    label: {
      show: props.categories.length <= 14 && !hasOverlay,
      position: 'top',
      fontSize: 10,
      formatter: (p: any) => fmt(p.value)
    }
  }]

  if (hasOverlay) {
    series.push({
      name: props.overlayName,
      type: 'line',
      data: props.overlayValues,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#f0a020', width: 2 },
      itemStyle: { color: '#f0a020' },
      connectNulls: true
    })
  }

  chart.setOption({
    legend: hasOverlay ? { top: 0, data: [myName, props.overlayName], itemWidth: 14 } : undefined,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: any) => (v == null ? '—' : fmt(Number(v)))
    },
    grid: { left: 16, right: 16, top: hasOverlay ? 36 : 16, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.categories,
      axisLabel: { fontSize: 11, rotate: props.categories.length > 14 ? 45 : 0 }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: {
        fontSize: 11,
        formatter: (v: number) => fmt(v)
      }
    },
    series
  }, true)
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

watch([
  () => props.categories,
  () => props.values,
  () => props.unit,
  () => props.overlayValues
], renderChart, { deep: true })

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
.chart-container { width: 100%; height: 320px; }
</style>
