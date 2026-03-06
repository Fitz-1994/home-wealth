<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  dates: string[]
  values: number[]
  title?: string
  color?: string
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chart) return
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const p = params[0]
        const val = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(p.value)
        return `${p.axisValue}<br/>${p.marker}${props.title || '金额'}：${val}`
      }
    },
    grid: { left: 16, right: 16, top: 16, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.dates,
      axisLabel: { fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        fontSize: 11,
        formatter: (v: number) => {
          if (v >= 1e8) return `${(v / 1e8).toFixed(0)}亿`
          if (v >= 1e4) return `${(v / 1e4).toFixed(0)}万`
          return v.toString()
        }
      }
    },
    series: [{
      type: 'line',
      data: props.values,
      smooth: true,
      lineStyle: { color: props.color || '#18a058', width: 2 },
      itemStyle: { color: props.color || '#18a058' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: (props.color || '#18a058') + '40' },
          { offset: 1, color: (props.color || '#18a058') + '00' }
        ])
      }
    }]
  })
}

watch([() => props.dates, () => props.values], renderChart, { deep: true })

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
.chart-container { width: 100%; height: 300px; }
</style>
