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

    <!-- 账户现金余额 -->
    <div v-if="cashBalances.length" class="cash-section">
      <div class="cash-header">
        <h3 style="margin:0;font-size:15px">账户现金</h3>
        <div style="display:flex;gap:6px">
          <n-button size="small" @click="openCashFlow('CASH_IN')">入金</n-button>
          <n-button size="small" @click="openCashFlow('CASH_OUT')">出金</n-button>
          <n-button size="small" @click="openCashDialog()">手工调整</n-button>
        </div>
      </div>
      <div class="cash-grid">
        <n-card v-for="c in cashBalances" :key="c.id" class="cash-card" size="small">
          <div class="cash-info">
            <div>
              <span class="cash-currency">{{ c.currency }}</span>
              <span class="cash-account">{{ accountName(c.accountId) }}</span>
            </div>
            <div class="cash-amount">{{ formatNumber(c.amount, 2) }}</div>
          </div>
          <div class="cash-cny">≈ {{ formatCny(c.cnyAmount) }}</div>
          <div v-if="c.note" class="cash-note">{{ c.note }}</div>
          <div class="cash-actions">
            <n-button text size="tiny" @click="openCashFlow('CASH_IN', c.accountId, c.currency)">入金</n-button>
            <n-button text size="tiny" @click="openCashFlow('CASH_OUT', c.accountId, c.currency)">出金</n-button>
            <n-button text size="tiny" @click="openCashDialog(c)">编辑</n-button>
            <n-popconfirm @positive-click="deleteCash(c)">
              <template #trigger><n-button text size="tiny" type="error">删除</n-button></template>
              确认删除 {{ c.currency }} 现金记录？
            </n-popconfirm>
          </div>
        </n-card>
      </div>
      <n-divider style="margin: 8px 0 16px" />
    </div>
    <div v-else-if="investmentAccountOptions.length" class="cash-section">
      <div class="cash-header">
        <h3 style="margin:0;font-size:15px">账户现金</h3>
        <div style="display:flex;gap:6px">
          <n-button size="small" @click="openCashFlow('CASH_IN')">入金</n-button>
          <n-button size="small" @click="openCashDialog()">手工添加</n-button>
        </div>
      </div>
      <div style="color:var(--hw-text-muted);font-size:13px;margin-bottom:16px">暂无现金记录</div>
    </div>

    <!-- 现金编辑对话框 -->
    <n-modal v-model:show="showCashDialog" preset="dialog" :title="editingCash ? '编辑现金' : '添加现金'">
      <n-form :model="cashForm" label-placement="left" label-width="80">
        <n-form-item label="投资账户" required>
          <n-select v-model:value="cashForm.accountId" :options="investmentAccountOptions" :disabled="!!editingCash" />
        </n-form-item>
        <n-form-item label="币种" required>
          <n-select v-model:value="cashForm.currency" :options="currencyOptions" :disabled="!!editingCash" />
        </n-form-item>
        <n-form-item label="金额" required>
          <n-input-number v-model:value="cashForm.amount" :precision="2" style="width:100%" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="cashForm.note" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showCashDialog = false">取消</n-button>
        <n-button type="primary" :loading="cashSubmitting" @click="submitCash">保存</n-button>
      </template>
    </n-modal>

    <!-- 持仓分组 -->
    <div class="group-section">
      <div class="cash-header">
        <h3 style="margin:0;font-size:15px">持仓分组</h3>
        <n-button size="small" @click="openGroupDialog()">添加分组</n-button>
      </div>
      <n-empty v-if="!holdingGroups.length" description="暂无分组" style="padding:8px 0" />
      <div v-else class="cash-grid">
        <n-card v-for="g in holdingGroups" :key="g.id" class="cash-card" size="small">
          <div class="group-title">{{ g.groupName }}</div>
          <div v-if="g.note" class="cash-note">{{ g.note }}</div>
          <div class="group-members">
            <n-tag v-for="m in g.members" :key="m.symbol" size="small" :bordered="false" style="margin:2px">
              {{ m.symbolName || m.symbol }}
            </n-tag>
            <span v-if="!g.members?.length" class="cash-note">暂无成员</span>
          </div>
          <div class="cash-actions">
            <n-button text size="tiny" @click="openGroupDialog(g)">编辑</n-button>
            <n-button text size="tiny" @click="openMemberDialog(g)">管理成员</n-button>
            <n-popconfirm @positive-click="deleteGroup(g.id)">
              <template #trigger><n-button text size="tiny" type="error">删除</n-button></template>
              确认删除分组「{{ g.groupName }}」？成员将恢复为独立排行。
            </n-popconfirm>
          </div>
        </n-card>
      </div>
      <n-divider style="margin: 8px 0 16px" />
    </div>

    <!-- 分组创建/编辑对话框 -->
    <n-modal v-model:show="showGroupDialog" preset="dialog" :title="editingGroup ? '编辑分组' : '添加分组'">
      <n-form :model="groupForm" label-placement="left" label-width="80">
        <n-form-item label="分组名称" required>
          <n-input v-model:value="groupForm.groupName" placeholder="如：恒生指数、中概互联" />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="groupForm.note" />
        </n-form-item>
        <n-form-item v-if="!editingGroup" label="初始成员">
          <n-select v-model:value="groupForm.symbols" :options="ungroupedSymbolOptions" multiple placeholder="选择标的（可选）" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showGroupDialog = false">取消</n-button>
        <n-button type="primary" :loading="groupSubmitting" @click="submitGroup">保存</n-button>
      </template>
    </n-modal>

    <!-- 成员管理对话框 -->
    <n-modal v-model:show="showMemberDialog" preset="dialog" title="管理分组成员">
      <div v-if="editingGroupForMembers" style="margin-bottom:12px;font-weight:600">{{ editingGroupForMembers.groupName }}</div>
      <n-select
        v-model:value="memberForm.symbols"
        :options="memberSymbolOptions"
        multiple
        placeholder="选择要加入分组的标的"
      />
      <template #action>
        <n-button @click="showMemberDialog = false">取消</n-button>
        <n-button type="primary" :loading="memberSubmitting" @click="submitMembers">保存</n-button>
      </template>
    </n-modal>

    <n-spin :show="loading">
      <n-empty v-if="!aggregatedHoldings.length" description="暂无持仓，请添加" />
      <div v-else class="holdings-list">
        <!-- 表头 -->
        <div class="list-header">
          <div class="col-rank">#</div>
          <div class="col-name">标的</div>
          <div class="col-qty">数量</div>
          <div class="col-price">现价</div>
          <div class="col-value">市值(CNY)</div>
          <div class="col-pnl">浮盈亏</div>
          <div class="col-change">涨跌</div>
          <div class="col-actions">操作</div>
        </div>
        <!-- 列表行 -->
        <div v-for="(g, idx) in aggregatedHoldings" :key="g.key" class="list-row-wrap">
          <div class="list-row" :class="{ 'stale': g.isStale }">
            <div class="col-rank">{{ idx + 1 }}</div>
            <div class="col-name">
              <div class="symbol-name">{{ g.symbolName || g.symbol }}</div>
              <div class="symbol-meta">{{ g.symbol }} · {{ marketLabel(g.market) }}
                <span class="account-tag">{{ accountName(g.accountId) }}</span>
              </div>
            </div>
            <div class="col-qty">{{ g.totalQuantity }}</div>
            <div class="col-price">
              <template v-if="g.currentPrice != null">{{ g.priceCurrency }} {{ g.currentPrice }}</template>
              <span v-else class="no-quote">—</span>
              <n-tag v-if="g.priceSource === 'MANUAL'" size="tiny" type="warning" :bordered="false" style="margin-left:4px">手工</n-tag>
              <n-tag v-else-if="g.currentPrice == null" size="tiny" type="error" :bordered="false"
                     style="margin-left:4px" title="无可用行情源，该持仓未计入市值">
                无行情
              </n-tag>
              <n-tag v-else-if="staleDays(g.priceTradeDate) >= STALE_DAYS_THRESHOLD" size="tiny" type="error"
                     :bordered="false" style="margin-left:4px"
                     :title="`最后成交日 ${g.priceTradeDate}`">
                过期 {{ staleDays(g.priceTradeDate) }} 天
              </n-tag>
            </div>
            <div class="col-value">{{ formatCny(g.totalMarketValueCny) }}</div>
            <div class="col-pnl" v-if="g.totalUnrealizedPnl != null" :class="g.totalUnrealizedPnl >= 0 ? 'up' : 'down'">
              {{ formatCny(g.totalUnrealizedPnl) }}
              <span class="pnl-pct">{{ formatPct(g.unrealizedPnlPct) }}</span>
            </div>
            <div class="col-pnl" v-else>—</div>
            <div class="col-change" :class="(g.priceChangePct ?? 0) >= 0 ? 'up' : 'down'">
              {{ formatPct(g.priceChangePct) }}
            </div>
            <div class="col-actions">
              <n-button text size="tiny" type="primary" @click="openBuy(g.items[0])">加仓</n-button>
              <n-button text size="tiny" type="warning" @click="openSell(g.items[0])">卖出</n-button>
              <n-button text size="tiny" @click="openManualPriceDialog(g)">录价</n-button>
              <template v-if="g.items.length > 1">
                <n-button text size="tiny" @click="toggleExpand(g.key)">
                  {{ expandedKeys.has(g.key) ? '收起' : '明细' }}({{ g.items.length }})
                </n-button>
              </template>
              <template v-else>
                <n-button text size="tiny" @click="editHolding(g.items[0])">编辑</n-button>
                <n-popconfirm @positive-click="closeHolding(g.items[0].id)">
                  <template #trigger><n-button text size="tiny" type="error">清仓</n-button></template>
                  确认清仓 {{ g.symbolName || g.symbol }}？
                </n-popconfirm>
              </template>
            </div>
          </div>
          <!-- 多笔明细展开 -->
          <div v-if="g.items.length > 1 && expandedKeys.has(g.key)" class="sub-rows">
            <div v-for="h in g.items" :key="h.id" class="sub-row">
              <div class="col-rank"></div>
              <div class="col-name sub-label">
                <span v-if="h.note">{{ h.note }}</span>
                <span v-else class="sub-muted">第{{ g.items.indexOf(h) + 1 }}笔</span>
              </div>
              <div class="col-qty">{{ h.quantity }}</div>
              <div class="col-price">
                <span v-if="h.costPrice">成本 {{ h.costPrice }}</span>
              </div>
              <div class="col-value">{{ formatCny(h.marketValueCny) }}</div>
              <div class="col-pnl" v-if="h.unrealizedPnl != null" :class="h.unrealizedPnl >= 0 ? 'up' : 'down'">
                {{ formatCny(h.unrealizedPnl) }}
              </div>
              <div class="col-pnl" v-else>—</div>
              <div class="col-change"></div>
              <div class="col-actions">
                <n-button text size="tiny" @click="editHolding(h)">编辑</n-button>
                <n-popconfirm @positive-click="closeHolding(h.id)">
                  <template #trigger><n-button text size="tiny" type="error">清仓</n-button></template>
                  确认清仓这笔 {{ h.quantity }} 股？
                </n-popconfirm>
              </div>
            </div>
          </div>
        </div>
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

    <!-- 交易快捷入口 Modal -->
    <TxnFormModal
      v-model:show="showTxnForm"
      :prefill="txnPrefill"
      :account-options="investmentAccountOptions"
      :holdings="holdings"
      @created="onTxnCreated"
    />

    <!-- 手工录入价格对话框 -->
    <n-modal v-model:show="showManualPriceDialog" preset="dialog" :title="`录入手工价 — ${manualPriceForm.symbolName || manualPriceForm.symbol}`">
      <div style="margin-bottom:8px;color:var(--hw-text-muted);font-size:12px">
        手工价会覆盖自动抓取，定时刷新会跳过该标的。适用于 Yahoo 无数据的港股期权等场景。
      </div>
      <n-form :model="manualPriceForm" label-placement="left" label-width="80">
        <n-form-item label="标的">
          <n-input :value="manualPriceForm.symbol" disabled />
        </n-form-item>
        <n-form-item label="价格" required>
          <n-input-number v-model:value="manualPriceForm.price" :precision="6" style="width:100%" />
        </n-form-item>
        <n-form-item label="币种">
          <n-select v-model:value="manualPriceForm.currency" :options="currencyOptions" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button v-if="manualPriceForm.priceSource === 'MANUAL'"
                  type="warning" :loading="manualPriceSubmitting"
                  @click="clearManualPrice">恢复自动</n-button>
        <n-button @click="showManualPriceDialog = false">取消</n-button>
        <n-button type="primary" :loading="manualPriceSubmitting" @click="submitManualPrice">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive } from 'vue'
import { useMessage } from 'naive-ui'
import { holdingsApi, cashBalanceApi, holdingGroupApi } from '@/api/holdings'
import { accountsApi } from '@/api/accounts'
import { dashboardApi } from '@/api/dashboard'
import { formatCny, formatNumber, formatPct, MARKET_TYPE_LABELS } from '@/utils/currency'
import TxnFormModal from '@/components/transactions/TxnFormModal.vue'

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
        priceTradeDate: h.priceTradeDate,
        priceSource: h.priceSource,
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
    // 合并行取最旧的交易日 —— 过期程度以最差的一笔为准
    if (h.priceTradeDate && (!g.priceTradeDate || h.priceTradeDate < g.priceTradeDate)) {
      g.priceTradeDate = h.priceTradeDate
    }
  }
  // 计算合并后浮盈亏%
  for (const g of groups.values()) {
    if (g.hasCost && g.totalCostCny > 0) {
      g.unrealizedPnlPct = +((g.totalUnrealizedPnl / g.totalCostCny) * 100).toFixed(2)
    }
  }
  const result = [...groups.values()]
  result.sort((a, b) => (b.totalMarketValueCny || 0) - (a.totalMarketValueCny || 0))
  return result
})

const marketLabel = (key: string) => MARKET_TYPE_LABELS[key] || key

// 超过该天数未更新即在列表中明确标红。取 3 天是为了跳过正常的周末休市
// （周五收盘价在周日看是 2 天），只暴露真正的数据源故障。
const STALE_DAYS_THRESHOLD = 3

/** 距该价格对应交易日已过去的自然天数 */
const staleDays = (tradeDate?: string | null): number => {
  if (!tradeDate) return 0
  const traded = new Date(`${tradeDate}T00:00:00`)
  if (Number.isNaN(traded.getTime())) return 0
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.max(0, Math.round((today.getTime() - traded.getTime()) / 86_400_000))
}

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

// ── 手工录入价格 ──
const showManualPriceDialog = ref(false)
const manualPriceSubmitting = ref(false)
const manualPriceForm = ref({
  symbol: '',
  symbolName: '',
  price: null as number | null,
  currency: 'CNY',
  priceSource: '' as string
})

function openManualPriceDialog(g: any) {
  manualPriceForm.value = {
    symbol: g.symbol,
    symbolName: g.symbolName,
    // 当前是手工价时回填便于微调；否则空着让用户输入
    price: g.priceSource === 'MANUAL' ? Number(g.currentPrice) : null,
    currency: g.priceCurrency || 'CNY',
    priceSource: g.priceSource || ''
  }
  showManualPriceDialog.value = true
}

async function submitManualPrice() {
  const f = manualPriceForm.value
  if (!f.price || f.price <= 0) {
    message.error('价格必须大于 0')
    return
  }
  manualPriceSubmitting.value = true
  try {
    await dashboardApi.upsertManualPrice({
      symbol: f.symbol,
      price: f.price,
      currency: f.currency
    })
    message.success('已保存手工价')
    showManualPriceDialog.value = false
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    manualPriceSubmitting.value = false
  }
}

async function clearManualPrice() {
  manualPriceSubmitting.value = true
  try {
    await dashboardApi.deleteManualPrice(manualPriceForm.value.symbol)
    message.success('已清除手工价，下次刷新恢复自动数据源')
    showManualPriceDialog.value = false
    await loadHoldings()
  } catch (e: any) {
    message.error(e.message || '清除失败')
  } finally {
    manualPriceSubmitting.value = false
  }
}

// ── 现金余额 ──
const cashBalances = ref<any[]>([])
const showCashDialog = ref(false)
const editingCash = ref<any>(null)
const cashSubmitting = ref(false)
const cashForm = ref({
  accountId: null as number | null,
  currency: 'USD',
  amount: 0,
  note: ''
})

async function loadCashBalances() {
  const accounts = allAccounts.value.filter(a => a.accountType === 'INVESTMENT')
  const all: any[] = []
  for (const acct of accounts) {
    try {
      const list = await cashBalanceApi.list(acct.id) as any[]
      all.push(...list)
    } catch { /* ignore */ }
  }
  cashBalances.value = all
}

function openCashDialog(existing?: any) {
  editingCash.value = existing || null
  if (existing) {
    cashForm.value = {
      accountId: existing.accountId,
      currency: existing.currency,
      amount: existing.amount,
      note: existing.note || ''
    }
  } else {
    cashForm.value = {
      accountId: investmentAccountOptions.value[0]?.value ?? null,
      currency: 'USD',
      amount: 0,
      note: ''
    }
  }
  showCashDialog.value = true
}

async function submitCash() {
  if (!cashForm.value.accountId || !cashForm.value.currency) {
    message.warning('请选择账户和币种')
    return
  }
  cashSubmitting.value = true
  try {
    await cashBalanceApi.upsert(cashForm.value.accountId, {
      currency: cashForm.value.currency,
      amount: cashForm.value.amount,
      note: cashForm.value.note
    })
    message.success(editingCash.value ? '现金余额已更新' : '现金余额已添加')
    showCashDialog.value = false
    await loadCashBalances()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    cashSubmitting.value = false
  }
}

async function deleteCash(c: any) {
  try {
    await cashBalanceApi.delete(c.accountId, c.id)
    message.success('已删除')
    await loadCashBalances()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}

// ── 持仓分组 ──
const holdingGroups = ref<any[]>([])
const showGroupDialog = ref(false)
const showMemberDialog = ref(false)
const editingGroup = ref<any>(null)
const editingGroupForMembers = ref<any>(null)
const groupSubmitting = ref(false)
const memberSubmitting = ref(false)
const groupForm = ref({ groupName: '', note: '', symbols: [] as string[] })
const memberForm = ref({ symbols: [] as string[] })

// 去重的标的列表（多个账户同一 symbol 只出现一次）
const distinctSymbols = computed(() => {
  const seen = new Map<string, any>()
  for (const h of holdings.value as any[]) {
    if (!seen.has(h.symbol)) seen.set(h.symbol, h)
  }
  return [...seen.values()]
})

// 已被分组的 symbol 集合
const groupedSymbols = computed(() => {
  const s = new Set<string>()
  for (const g of holdingGroups.value) {
    for (const m of g.members || []) s.add(m.symbol)
  }
  return s
})

// 未分组的标的选项（用于创建分组时选择初始成员）
const ungroupedSymbolOptions = computed(() =>
  distinctSymbols.value
    .filter((h: any) => !groupedSymbols.value.has(h.symbol))
    .map((h: any) => ({
      label: `${h.symbolName || h.symbol} (${h.symbol})`,
      value: h.symbol
    }))
)

// 成员管理选项（当前组成员 + 未分组标的）
const memberSymbolOptions = computed(() => {
  const currentSymbols = new Set<string>(
    (editingGroupForMembers.value?.members || []).map((m: any) => m.symbol)
  )
  return distinctSymbols.value
    .filter((h: any) => currentSymbols.has(h.symbol) || !groupedSymbols.value.has(h.symbol))
    .map((h: any) => ({
      label: `${h.symbolName || h.symbol} (${h.symbol})`,
      value: h.symbol
    }))
})

async function loadGroups() {
  try {
    holdingGroups.value = await holdingGroupApi.list() as any[]
  } catch { holdingGroups.value = [] }
}

function openGroupDialog(existing?: any) {
  editingGroup.value = existing || null
  groupForm.value = existing
    ? { groupName: existing.groupName, note: existing.note || '', symbols: [] }
    : { groupName: '', note: '', symbols: [] }
  showGroupDialog.value = true
}

function openMemberDialog(g: any) {
  editingGroupForMembers.value = g
  memberForm.value.symbols = (g.members || []).map((m: any) => m.symbol)
  showMemberDialog.value = true
}

async function submitGroup() {
  if (!groupForm.value.groupName.trim()) { message.warning('请输入分组名称'); return }
  groupSubmitting.value = true
  try {
    if (editingGroup.value) {
      await holdingGroupApi.update(editingGroup.value.id, {
        groupName: groupForm.value.groupName,
        note: groupForm.value.note
      })
      message.success('分组已更新')
    } else {
      await holdingGroupApi.create({
        groupName: groupForm.value.groupName,
        note: groupForm.value.note,
        symbols: groupForm.value.symbols.length ? groupForm.value.symbols : undefined
      })
      message.success('分组已创建')
    }
    showGroupDialog.value = false
    await loadGroups()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    groupSubmitting.value = false
  }
}

async function submitMembers() {
  if (!editingGroupForMembers.value) return
  memberSubmitting.value = true
  try {
    await holdingGroupApi.updateMembers(editingGroupForMembers.value.id, memberForm.value.symbols)
    message.success('成员已更新')
    showMemberDialog.value = false
    await loadGroups()
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    memberSubmitting.value = false
  }
}

async function deleteGroup(id: number) {
  try {
    await holdingGroupApi.delete(id)
    message.success('分组已删除')
    await loadGroups()
  } catch (e: any) {
    message.error(e.message || '删除失败')
  }
}

// ── 交易快捷入口 ──
const showTxnForm = ref(false)
const txnPrefill = ref<any>({})

function openBuy(h: any) {
  txnPrefill.value = {
    txnType: 'BUY',
    accountId: h.accountId,
    holdingId: h.id,
    symbol: h.symbol,
    market: h.market,
    currency: h.priceCurrency || 'CNY',
    lockTxnType: true,
    lockAccount: true,
    lockHolding: true,
    lockCurrency: true
  }
  showTxnForm.value = true
}

function openSell(h: any) {
  txnPrefill.value = {
    txnType: 'SELL',
    accountId: h.accountId,
    holdingId: h.id,
    symbol: h.symbol,
    market: h.market,
    currency: h.priceCurrency || 'CNY',
    lockTxnType: true,
    lockAccount: true,
    lockHolding: true,
    lockCurrency: true
  }
  showTxnForm.value = true
}

function openCashFlow(type: 'CASH_IN' | 'CASH_OUT', accountId?: number, currency?: string) {
  txnPrefill.value = {
    txnType: type,
    accountId: accountId ?? null,
    currency: currency ?? 'CNY',
    lockTxnType: true,
    lockAccount: !!accountId,
    lockCurrency: !!currency
  }
  showTxnForm.value = true
}

async function onTxnCreated() {
  await loadHoldings()
  await loadCashBalances()
}

onMounted(async () => {
  await loadAccounts()
  loadHoldings()
  loadCashBalances()
  loadGroups()
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
.parse-result-tip { font-size: 13px; color: var(--hw-text-muted); margin-bottom: 8px; }
.parse-table-wrap { flex: 1; overflow: auto; }
.parse-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.parse-table th { text-align: left; padding: 6px 8px; background: var(--hw-bg-secondary); font-weight: 600; white-space: nowrap; }
.parse-table td { padding: 4px 8px; border-top: 1px solid var(--hw-border); vertical-align: middle; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
.validated-name { font-size: 12px; color: #18a058; margin-top: 4px; }

/* 列表布局 */
.holdings-list { border: 1px solid var(--hw-border); border-radius: 8px; overflow: hidden; }
.list-header, .list-row, .sub-row {
  display: grid;
  grid-template-columns: 40px 1.5fr 0.7fr 0.8fr 1fr 1fr 0.6fr 0.7fr;
  align-items: center;
  padding: 10px 12px;
  gap: 8px;
}
.list-header {
  background: var(--hw-bg-secondary);
  font-size: 12px;
  font-weight: 600;
  color: var(--hw-text-secondary);
  border-bottom: 1px solid var(--hw-border);
}
.list-row {
  border-bottom: 1px solid var(--hw-border);
  font-size: 13px;
  transition: background .15s;
}
.list-row:hover { background: var(--hw-bg-secondary); }
.list-row.stale { opacity: 0.7; }
.no-quote { color: var(--text-tertiary, #999); }
.list-row-wrap:last-child .list-row:not(:has(+ .sub-rows)),
.list-row-wrap:last-child .sub-rows .sub-row:last-child { border-bottom: none; }
.col-rank { font-size: 13px; font-weight: 600; color: var(--hw-text-muted); text-align: center; }
.col-name .symbol-name { font-size: 14px; font-weight: 600; }
.col-name .symbol-meta { font-size: 11px; color: var(--hw-text-secondary); margin-top: 2px; }
.account-tag { display: inline-block; font-size: 10px; color: var(--hw-text-muted); background: var(--hw-border); border-radius: 3px; padding: 0 4px; margin-left: 4px; }
.col-value { font-weight: 600; }
.col-pnl .pnl-pct { font-size: 11px; margin-left: 2px; }
.col-change { font-weight: 600; }
.up { color: #d03050; }
.down { color: #18a058; }
.col-actions { display: flex; gap: 4px; justify-content: flex-end; }

/* 子行明细 */
.sub-rows { background: var(--hw-bg-secondary); }
.sub-row {
  font-size: 12px;
  color: var(--hw-text-secondary);
  border-bottom: 1px solid var(--hw-border);
  padding: 6px 12px;
}
.sub-label { font-size: 12px; }
.sub-muted { color: var(--hw-text-muted); }

/* 账户现金 */
.cash-section { margin-bottom: 8px; }
.cash-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.cash-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }
.cash-card { }
.cash-info { display: flex; justify-content: space-between; align-items: center; }
.cash-currency { font-weight: 600; font-size: 14px; }
.cash-account { font-size: 11px; color: var(--hw-text-muted); background: var(--hw-border); border-radius: 4px; padding: 1px 6px; margin-left: 6px; }
.cash-amount { font-size: 15px; font-weight: 600; }
.cash-cny { font-size: 12px; color: var(--hw-text-secondary); margin-top: 4px; }
.cash-note { font-size: 11px; color: var(--hw-text-muted); margin-top: 2px; }
.cash-actions { display: flex; gap: 8px; margin-top: 6px; }

/* 持仓分组 */
.group-section { margin-bottom: 8px; }
.group-title { font-weight: 600; font-size: 14px; }
.group-members { display: flex; flex-wrap: wrap; margin-top: 6px; }
</style>
