<template>
  <div class="holdings-view">
    <div class="page-header">
      <h2>投资持仓</h2>
      <div class="header-actions">
        <n-button @click="openParseDialog">📷 截图识别</n-button>
        <n-button type="primary" @click="openCreateDialog">添加持仓</n-button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filters">
      <n-select
        v-model:value="filterMarket"
        :options="marketFilterOptions"
        placeholder="全部市场"
        clearable
        style="width:140px"
        @update:value="loadHoldings"
      />
      <n-select
        v-model:value="filterAccount"
        :options="accountFilterOptions"
        placeholder="全部账户"
        clearable
        style="width:160px"
        @update:value="loadHoldings"
      />
    </div>

    <n-spin :show="loading">
      <n-empty v-if="!aggregatedHoldings.length" description="暂无持仓，请添加" />
      <div v-else class="holdings-grid">
        <n-card
          v-for="g in aggregatedHoldings"
          :key="g.key"
          class="holding-card"
          hoverable
        >
          <div class="holding-header">
            <div>
              <div class="symbol-name">{{ g.symbolName || g.symbol }}</div>
              <div class="symbol-code">{{ g.symbol }} · {{ marketLabel(g.market) }}</div>
              <div class="account-tag">{{ accountName(g.accountId) }}</div>
            </div>
            <div class="change-pct" :class="(g.priceChangePct ?? 0) >= 0 ? 'up' : 'down'">
              {{ formatPct(g.priceChangePct) }}
            </div>
          </div>

          <n-divider style="margin: 10px 0" />

          <div class="holding-stats">
            <div class="stat-item">
              <div class="stat-label">持仓数量</div>
              <div class="stat-value">{{ g.totalQuantity }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">现价</div>
              <div class="stat-value">{{ g.priceCurrency }} {{ g.currentPrice }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">市值</div>
              <div class="stat-value primary">{{ formatCny(g.totalMarketValueCny) }}</div>
            </div>
            <div class="stat-item" v-if="g.totalUnrealizedPnl != null">
              <div class="stat-label">浮盈亏</div>
              <div class="stat-value" :class="g.totalUnrealizedPnl >= 0 ? 'up' : 'down'">
                {{ formatCny(g.totalUnrealizedPnl) }}
                <span style="font-size:11px">（{{ formatPct(g.unrealizedPnlPct) }}）</span>
              </div>
            </div>
          </div>

          <n-alert v-if="g.isStale" type="warning" size="small" :show-icon="false" style="margin-top:8px">
            行情数据可能不是最新
          </n-alert>

          <!-- 多笔明细展开 -->
          <template v-if="g.items.length > 1">
            <div class="expand-toggle" @click="toggleExpand(g.key)">
              <span>{{ expandedKeys.has(g.key) ? '▲' : '▼' }} {{ g.items.length }} 笔明细</span>
            </div>
            <div v-if="expandedKeys.has(g.key)" class="sub-items">
              <div v-for="h in g.items" :key="h.id" class="sub-item">
                <span class="sub-qty">× {{ h.quantity }}</span>
                <span v-if="h.costPrice" class="sub-cost">成本 {{ h.priceCurrency }} {{ h.costPrice }}</span>
                <span v-if="h.note" class="sub-note">{{ h.note }}</span>
                <div class="sub-actions">
                  <n-button text size="tiny" @click="editHolding(h)">编辑</n-button>
                  <n-popconfirm @positive-click="closeHolding(h.id)">
                    <template #trigger><n-button text size="tiny" type="error">清仓</n-button></template>
                    确认清仓这笔 {{ h.quantity }} 股？
                  </n-popconfirm>
                </div>
              </div>
            </div>
          </template>

          <!-- 单笔直接显示操作按钮 -->
          <div v-else class="holding-actions">
            <n-button text size="small" @click="editHolding(g.items[0])">编辑</n-button>
            <n-popconfirm @positive-click="closeHolding(g.items[0].id)">
              <template #trigger>
                <n-button text size="small" type="error">清仓</n-button>
              </template>
              确认清仓 {{ g.symbolName || g.symbol }}？
            </n-popconfirm>
          </div>
        </n-card>
      </div>
    </n-spin>

    <!-- 截图识别对话框 -->
    <n-modal v-model:show="showParseDialog" style="width:900px;max-width:95vw" preset="card" title="📷 截图识别持仓">
      <div class="parse-layout">
        <!-- 左：上传区 -->
        <div class="parse-upload-col">
          <div
            class="upload-zone"
            :class="{ 'drag-over': isDragging }"
            @click="triggerFileInput"
            @dragover.prevent="isDragging = true"
            @dragleave="isDragging = false"
            @drop.prevent="onDrop"
          >
            <img v-if="previewUrl" :src="previewUrl" class="preview-img" />
            <div v-else class="upload-placeholder">
              <div style="font-size:36px">📁</div>
              <div>点击或拖拽上传截图</div>
              <div style="font-size:12px;color:#999;margin-top:4px">支持 PNG / JPG / WEBP</div>
            </div>
          </div>
          <input ref="fileInputRef" type="file" accept="image/*" style="display:none" @change="onFileSelect" />
          <n-button
            type="primary" block style="margin-top:12px"
            :loading="parsing" :disabled="!selectedFile"
            @click="parseImage"
          >
            {{ parsing ? '识别中...' : '开始识别' }}
          </n-button>
        </div>

        <!-- 右：识别结果 -->
        <div class="parse-result-col">
          <n-empty v-if="!parsedRows.length" description="上传截图后点击「开始识别」" style="padding:40px 0" />
          <template v-else>
            <div class="parse-result-tip">共识别 {{ parsedRows.length }} 条，请确认或修改后导入</div>
            <div class="parse-table-wrap">
              <table class="parse-table">
                <thead>
                  <tr>
                    <th>账户</th><th>代码</th><th>名称</th><th>数量</th><th>成本价</th><th>币种</th><th>市场</th><th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in parsedRows" :key="i">
                    <td>
                      <n-select v-model:value="row.accountId" :options="investmentAccountOptions" size="small" style="width:110px" />
                    </td>
                    <td><n-input v-model:value="row.symbol" size="small" style="width:110px" /></td>
                    <td><n-input v-model:value="row.symbolName" size="small" style="width:90px" /></td>
                    <td><n-input-number v-model:value="row.quantity" size="small" :precision="4" style="width:90px" /></td>
                    <td><n-input-number v-model:value="row.costPrice" size="small" :precision="4" style="width:90px" /></td>
                    <td>
                      <n-select v-model:value="row.priceCurrency" :options="currencyOptions" size="small" style="width:80px" />
                    </td>
                    <td>
                      <n-select v-model:value="row.market" :options="marketOptions" size="small" style="width:90px" />
                    </td>
                    <td>
                      <n-button text size="small" type="error" @click="parsedRows.splice(i, 1)">删除</n-button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div style="margin-top:12px;display:flex;justify-content:flex-end;gap:8px">
              <n-button @click="parsedRows = []">清空</n-button>
              <n-button type="primary" :loading="importing" @click="confirmImport">确认导入</n-button>
            </div>
          </template>
        </div>
      </div>
    </n-modal>

    <!-- 新建/编辑持仓对话框 -->
    <n-modal v-model:show="showDialog" preset="dialog" :title="editingHolding ? '编辑持仓' : '添加持仓'">
      <n-form :model="form" label-placement="left" label-width="90">
        <n-form-item label="投资账户" required v-if="!editingHolding">
          <n-select v-model:value="form.accountId" :options="investmentAccountOptions" />
        </n-form-item>
        <n-form-item label="标的代码" required v-if="!editingHolding">
          <n-input-group>
            <n-input v-model:value="form.symbol" placeholder="如：600519.SS / AAPL" />
            <n-button :loading="validating" @click="validateSymbol">验证</n-button>
          </n-input-group>
          <div v-if="validatedName" class="validated-name">✓ {{ validatedName }}</div>
        </n-form-item>
        <n-form-item label="市场类型" required v-if="!editingHolding">
          <n-select v-model:value="form.market" :options="marketOptions" :disabled="!!validatedName" />
        </n-form-item>
        <n-form-item label="价格币种" required v-if="!editingHolding">
          <n-select v-model:value="form.priceCurrency" :options="currencyOptions" :disabled="!!validatedName" />
        </n-form-item>
        <n-form-item label="持仓数量" required>
          <n-input-number v-model:value="form.quantity" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item label="成本价">
          <n-input-number v-model:value="form.costPrice" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="form.note" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showDialog = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="submitHolding">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { useMessage } from 'naive-ui'
import { holdingsApi } from '@/api/holdings'
import { accountsApi } from '@/api/accounts'
import { formatCny, formatPct, MARKET_TYPE_LABELS } from '@/utils/currency'

const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const validating = ref(false)

// ── 截图识别 ──
const showParseDialog = ref(false)
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const parsing = ref(false)
const importing = ref(false)
const isDragging = ref(false)
const parsedRows = ref<any[]>([])
const fileInputRef = ref<HTMLInputElement>()
const defaultImportAccountId = computed(() =>
  investmentAccountOptions.value[0]?.value ?? null
)

function openParseDialog() {
  showParseDialog.value = true
  selectedFile.value = null
  previewUrl.value = ''
  parsedRows.value = []
}

function triggerFileInput() { fileInputRef.value?.click() }

function onFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) setFile(file)
}

function onDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) setFile(file)
}

function setFile(file: File) {
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  parsedRows.value = []
}

async function parseImage() {
  if (!selectedFile.value) return
  parsing.value = true
  try {
    const result: any = await holdingsApi.parseImage(selectedFile.value)
    if (!result?.length) { message.warning('未识别到持仓数据，请换一张更清晰的截图'); return }
    parsedRows.value = result.map((r: any) => ({
      ...r,
      accountId: defaultImportAccountId.value,
      quantity: r.quantity ?? 0
    }))
    message.success(`识别到 ${result.length} 条持仓`)
  } catch (e: any) {
    message.error(e.message || '识别失败，请检查 AI_API_KEY 配置')
  } finally {
    parsing.value = false
  }
}

async function confirmImport() {
  const rows = parsedRows.value.filter(r => r.accountId && r.symbol && r.quantity > 0)
  if (!rows.length) { message.warning('没有可导入的有效数据'); return }
  importing.value = true
  try {
    const requests = rows.map(r => ({
      accountId: r.accountId,
      symbol: r.symbol,
      symbolName: r.symbolName || '',
      market: r.market || 'CN_A',
      quantity: r.quantity,
      costPrice: r.costPrice || null,
      priceCurrency: r.priceCurrency || 'CNY',
      note: r.note || ''
    }))
    await holdingsApi.batchImport(requests)
    message.success(`成功导入 ${rows.length} 条持仓`)
    showParseDialog.value = false
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}
const holdings = ref<any[]>([])
const allAccounts = ref<any[]>([])
const showDialog = ref(false)
const editingHolding = ref<any>(null)
const filterMarket = ref<string | null>(null)
const filterAccount = ref<number | null>(null)
const validatedName = ref('')

const investmentAccountOptions = computed(() =>
  allAccounts.value
    .filter(a => a.accountType === 'INVESTMENT')
    .map(a => ({ label: a.accountName, value: a.id }))
)

const accountFilterOptions = computed(() => [
  { label: '全部账户', value: null },
  ...allAccounts.value
    .filter(a => a.accountType === 'INVESTMENT')
    .map(a => ({ label: a.accountName, value: a.id }))
])

const accountMap = computed(() =>
  Object.fromEntries(allAccounts.value.map(a => [a.id, a.accountName]))
)

const accountName = (id: number) => accountMap.value[id] || ''

// 展开/收起状态
const expandedKeys = reactive(new Set<string>())
const toggleExpand = (key: string) => {
  expandedKeys.has(key) ? expandedKeys.delete(key) : expandedKeys.add(key)
}

// 按 accountId + symbol 聚合持仓
const aggregatedHoldings = computed(() => {
  const groups = new Map<string, any>()
  for (const h of holdings.value) {
    const key = `${h.accountId}-${h.symbol}`
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        accountId: h.accountId,
        symbol: h.symbol,
        symbolName: h.symbolName,
        market: h.market,
        currentPrice: h.currentPrice,
        priceCurrency: h.priceCurrency,
        priceChangePct: h.priceChangePct,
        isStale: h.isStale,
        totalQuantity: 0,
        totalMarketValueCny: 0,
        totalUnrealizedPnl: null as number | null,
        totalCostCny: 0,
        hasCost: false,
        items: [] as any[]
      })
    }
    const g = groups.get(key)!
    g.items.push(h)
    g.totalQuantity = +(g.totalQuantity + +h.quantity).toFixed(6)
    g.totalMarketValueCny = +(g.totalMarketValueCny + +(h.marketValueCny ?? 0)).toFixed(4)
    if (h.unrealizedPnl != null) {
      g.totalUnrealizedPnl = +((g.totalUnrealizedPnl ?? 0) + +h.unrealizedPnl).toFixed(4)
      g.hasCost = true
    }
    if (h.marketValueCny != null && h.unrealizedPnl != null) {
      g.totalCostCny += +(h.marketValueCny - h.unrealizedPnl).toFixed(4)
    }
    g.isStale = g.isStale || h.isStale
  }
  // 计算合并后浮盈亏%
  for (const g of groups.values()) {
    if (g.hasCost && g.totalCostCny > 0) {
      g.unrealizedPnlPct = +((g.totalUnrealizedPnl / g.totalCostCny) * 100).toFixed(2)
    }
  }
  return [...groups.values()]
})

const marketLabel = (key: string) => MARKET_TYPE_LABELS[key] || key

const marketOptions = Object.entries(MARKET_TYPE_LABELS).map(([k, v]) => ({ label: v, value: k }))
const marketFilterOptions = [{ label: '全部', value: null }, ...marketOptions]
const currencyOptions = ['CNY', 'USD', 'HKD', 'EUR', 'JPY', 'GBP'].map(c => ({ label: c, value: c }))

const defaultForm = () => ({
  accountId: null as number | null,
  symbol: '',
  market: 'CN_A',
  quantity: 0,
  costPrice: null as number | null,
  priceCurrency: 'CNY',
  note: ''
})

const form = ref(defaultForm())

async function loadHoldings() {
  loading.value = true
  try {
    const params: any = {}
    if (filterMarket.value) params.market = filterMarket.value
    if (filterAccount.value) params.accountId = filterAccount.value
    holdings.value = await holdingsApi.list(params) as any[]
  } finally {
    loading.value = false
  }
}

async function loadAccounts() {
  allAccounts.value = await accountsApi.list() as any[]
}

async function validateSymbol() {
  if (!form.value.symbol) return
  validating.value = true
  validatedName.value = ''
  try {
    const data: any = await holdingsApi.validateSymbol(form.value.symbol, form.value.priceCurrency)
    validatedName.value = data.symbolName || form.value.symbol
    if (data.market) form.value.market = data.market
    if (data.priceCurrency) {
      form.value.priceCurrency = data.priceCurrency
    }
    message.success(`验证成功：${validatedName.value}，当前价 ${data.priceCurrency} ${data.currentPrice}`)
  } catch (e: any) {
    message.error('标的代码无效或无法获取价格')
  } finally {
    validating.value = false
  }
}

function openCreateDialog() {
  editingHolding.value = null
  form.value = defaultForm()
  validatedName.value = ''
  showDialog.value = true
}

function editHolding(h: any) {
  editingHolding.value = h
  form.value = {
    accountId: h.accountId,
    symbol: h.symbol,
    market: h.market,
    quantity: h.quantity,
    costPrice: h.costPrice,
    priceCurrency: h.priceCurrency,
    note: h.note || ''
  }
  showDialog.value = true
}

async function submitHolding() {
  submitting.value = true
  try {
    if (editingHolding.value) {
      await holdingsApi.update(editingHolding.value.id, form.value)
      message.success('持仓已更新')
    } else {
      await holdingsApi.create(form.value)
      message.success('持仓已添加')
    }
    showDialog.value = false
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function closeHolding(id: number) {
  try {
    await holdingsApi.close(id)
    message.success('已清仓')
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message)
  }
}

onMounted(() => {
  loadHoldings()
  loadAccounts()
})
</script>

<style scoped>
.holdings-view { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }

/* 截图识别弹窗 */
.parse-layout { display: flex; gap: 16px; min-height: 360px; }
.parse-upload-col { width: 220px; flex-shrink: 0; display: flex; flex-direction: column; }
.parse-result-col { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.upload-zone {
  flex: 1; min-height: 200px; border: 2px dashed var(--hw-border); border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; overflow: hidden; transition: border-color .2s;
}
.upload-zone:hover, .upload-zone.drag-over { border-color: #18a058; }
.upload-placeholder { text-align: center; color: #999; padding: 20px; }
.preview-img { width: 100%; height: 100%; object-fit: contain; }
.parse-result-tip { font-size: 13px; color: #666; margin-bottom: 8px; }
.parse-table-wrap { flex: 1; overflow: auto; }
.parse-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.parse-table th { text-align: left; padding: 6px 8px; background: var(--hw-bg-secondary); font-weight: 600; white-space: nowrap; }
.parse-table td { padding: 4px 8px; border-top: 1px solid var(--hw-border); vertical-align: middle; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
.holdings-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.holding-header { display: flex; justify-content: space-between; align-items: flex-start; }
.symbol-name { font-size: 15px; font-weight: 600; }
.symbol-code { font-size: 12px; color: #999; margin-top: 2px; }
.account-tag { display: inline-block; font-size: 11px; color: #666; background: var(--hw-border); border-radius: 4px; padding: 1px 6px; margin-top: 4px; }
.change-pct { font-size: 16px; font-weight: 600; }
.change-pct.up { color: #d03050; }
.change-pct.down { color: #18a058; }
.holding-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.stat-label { font-size: 11px; color: #999; margin-bottom: 2px; }
.stat-value { font-size: 14px; font-weight: 500; }
.stat-value.primary { color: #18a058; }
.stat-value.up { color: #d03050; }
.stat-value.down { color: #18a058; }
.holding-actions { display: flex; gap: 8px; margin-top: 10px; }
.validated-name { font-size: 12px; color: #18a058; margin-top: 4px; }
.expand-toggle { font-size: 12px; color: #999; margin-top: 10px; cursor: pointer; user-select: none; }
.expand-toggle:hover { color: #18a058; }
.sub-items { margin-top: 6px; border-top: 1px solid var(--hw-border); padding-top: 6px; display: flex; flex-direction: column; gap: 6px; }
.sub-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #666; }
.sub-qty { font-weight: 600; color: #333; min-width: 50px; }
.sub-cost { color: #999; }
.sub-note { color: #bbb; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub-actions { margin-left: auto; display: flex; gap: 4px; }
</style>
