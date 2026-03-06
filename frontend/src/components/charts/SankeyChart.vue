<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

interface SankeyNode { name: string }
interface SankeyLink { source: string; target: string; value: number }

const props = defineProps<{
  data: { nodes: SankeyNode[]; links: SankeyLink[] } | null
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chart || !props.data) return
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const val = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(params.value)
        return `${params.name}<br/>金额：${val}`
      }
    },
    series: [{
      type: 'sankey',
      layout: 'none',
      emphasis: { focus: 'adjacency' },
      data: props.data.nodes,
      links: props.data.links,
      label: { position: 'right', fontSize: 12 },
      lineStyle: { color: 'source', opacity: 0.5 }
    }]
  })
}

watch(() => props.data, renderChart, { deep: true })

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
.chart-container { width: 100%; height: 400px; }
</style>
