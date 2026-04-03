<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { formatCny, formatPct, MARKET_TYPE_LABELS } from '@/utils/currency'

interface HoldingItem {
  holdingId?: number
  symbol: string
  symbolName?: string
  market?: string
  marketValueCny: number
  ratio?: number
  priceChangePct?: number
  groupId?: number
  groupName?: string
  memberCount?: number
}

const props = defineProps<{
  items: HoldingItem[]
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function buildTreeData(items: HoldingItem[]) {
  return items.map(item => ({
    name: item.groupName || item.symbolName || item.symbol,
    value: item.marketValueCny,
    changePct: item.priceChangePct || 0,
    symbol: item.symbol,
    ratio: item.ratio || 0,
    market: item.market,
    isGroup: !!item.groupName,
    memberCount: item.memberCount,
    itemStyle: {
      color: getColor(item.priceChangePct || 0)
    }
  }))
}

function getColor(changePct: number): string {
  // 涨红跌绿，范围 -3% ~ +3%
  const clamped = Math.max(-3, Math.min(3, changePct))
  const t = (clamped + 3) / 6 // 0 ~ 1

  // 绿 #18a058 → 灰 #555555 → 红 #d03050
  if (t <= 0.5) {
    const r = t * 2 // 0 ~ 1 within green-to-gray
    return lerpColor([0x18, 0xa0, 0x58], [0x55, 0x55, 0x55], r)
  } else {
    const r = (t - 0.5) * 2 // 0 ~ 1 within gray-to-red
    return lerpColor([0x55, 0x55, 0x55], [0xd0, 0x30, 0x50], r)
  }
}

function lerpColor(a: number[], b: number[], t: number): string {
  const r = Math.round(a[0] + (b[0] - a[0]) * t)
  const g = Math.round(a[1] + (b[1] - a[1]) * t)
  const bl = Math.round(a[2] + (b[2] - a[2]) * t)
  return `rgb(${r},${g},${bl})`
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  renderChart()
}

function renderChart() {
  if (!chart || !props.items?.length) return

  const data = buildTreeData(props.items)

  chart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const d = params.data
        if (!d) return ''
        const lines = [`<b>${d.name}</b>`]
        if (d.isGroup) {
          lines.push(`组合 · ${d.memberCount}个持仓`)
        } else if (d.symbol) {
          const marketLabel = MARKET_TYPE_LABELS[d.market] || d.market || ''
          lines.push(`${marketLabel} · ${d.symbol}`)
        }
        lines.push(`市值：${formatCny(params.value)}`)
        lines.push(`占比：${((d.ratio || 0) * 100).toFixed(1)}%`)
        lines.push(`涨跌：${formatPct(d.changePct)}`)
        return lines.join('<br/>')
      }
    },
    series: [{
      type: 'treemap',
      roam: false,
      nodeClick: false,
      breadcrumb: { show: false },
      width: '100%',
      height: '100%',
      itemStyle: {
        borderColor: '#1a1a2e',
        borderWidth: 2,
        gapWidth: 2
      },
      label: {
        show: true,
        color: '#fff',
        fontSize: 12,
        fontWeight: 500,
        formatter: (params: any) => {
          const d = params.data
          const pct = d?.changePct != null ? formatPct(d.changePct) : ''
          return `${params.name}\n${pct}`
        }
      },
      data
    }]
  })
}

watch(() => props.items, renderChart, { deep: true })

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
