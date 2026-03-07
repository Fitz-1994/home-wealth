<template>
  <div class="settings-view">
    <h2>设置</h2>

    <!-- 主题 -->
    <n-card title="显示主题" class="settings-card">
      <n-radio-group :value="themeStore.mode" @update:value="themeStore.setMode" name="theme">
        <n-space>
          <n-radio value="light">浅色</n-radio>
          <n-radio value="dark">深色</n-radio>
          <n-radio value="system">跟随系统</n-radio>
        </n-space>
      </n-radio-group>
    </n-card>

    <!-- 修改密码 -->
    <n-card title="修改密码" class="settings-card">
      <n-form :model="passwordForm" label-placement="left" label-width="90">
        <n-form-item label="原密码">
          <n-input v-model:value="passwordForm.oldPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item label="新密码">
          <n-input v-model:value="passwordForm.newPassword" type="password" show-password-on="click" />
        </n-form-item>
      </n-form>
      <n-button type="primary" :loading="changingPwd" @click="changePassword">更新密码</n-button>
    </n-card>

    <!-- API Key 管理 -->
    <n-card title="API Key 管理" class="settings-card">
      <div style="margin-bottom:12px">
        <p style="font-size:13px;color:#666">
          API Key 可供外部服务（如 OpenClaw Skill）调用本系统接口。请求时在请求头携带 <code>X-API-Key: &lt;key&gt;</code>。
        </p>
      </div>

      <n-button type="primary" size="small" @click="showCreateKeyDialog = true" style="margin-bottom:16px">
        生成新 Key
      </n-button>

      <n-table :bordered="false" size="small">
        <thead>
          <tr>
            <th>名称</th>
            <th>Key 前缀</th>
            <th>最后使用</th>
            <th>过期时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="key in apiKeys" :key="key.id">
            <td>{{ key.keyName }}</td>
            <td><code>{{ key.keyPrefix }}...</code></td>
            <td>{{ key.lastUsedAt ? formatDate(key.lastUsedAt) : '从未使用' }}</td>
            <td>{{ key.expiresAt ? formatDate(key.expiresAt) : '永不过期' }}</td>
            <td>
              <n-popconfirm @positive-click="revokeKey(key.id)">
                <template #trigger>
                  <n-button text type="error" size="small">吊销</n-button>
                </template>
                确认吊销此 Key？
              </n-popconfirm>
            </td>
          </tr>
          <tr v-if="!apiKeys.length">
            <td colspan="5" style="text-align:center;color:#999">暂无 API Key</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 生成 Key 对话框 -->
    <n-modal v-model:show="showCreateKeyDialog" preset="dialog" title="生成 API Key">
      <n-form :model="keyForm" label-placement="left" label-width="80">
        <n-form-item label="Key 名称" required>
          <n-input v-model:value="keyForm.keyName" placeholder="如：openclaw-skill" />
        </n-form-item>
        <n-form-item label="过期时间">
          <n-date-picker
            v-model:formatted-value="keyForm.expiresAt"
            type="datetime"
            value-format="yyyy-MM-dd HH:mm:ss"
            clearable
            placeholder="不填则永不过期"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showCreateKeyDialog = false">取消</n-button>
        <n-button type="primary" :loading="creatingKey" @click="createKey">生成</n-button>
      </template>
    </n-modal>

    <!-- 展示新生成的 Key -->
    <n-modal v-model:show="showNewKey" preset="dialog" title="API Key 已生成">
      <n-alert type="warning" title="请立即保存，此 Key 只显示一次！" />
      <div style="margin-top:12px">
        <n-input :value="newKeyValue" readonly>
          <template #suffix>
            <n-button text @click="copyKey">复制</n-button>
          </template>
        </n-input>
      </div>
      <template #action>
        <n-button type="primary" @click="showNewKey = false">我已保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { authApi } from '@/api/auth'
import { useThemeStore } from '@/stores/theme'
import dayjs from 'dayjs'

const themeStore = useThemeStore()

const message = useMessage()
const apiKeys = ref<any[]>([])
const passwordForm = ref({ oldPassword: '', newPassword: '' })
const changingPwd = ref(false)
const showCreateKeyDialog = ref(false)
const showNewKey = ref(false)
const creatingKey = ref(false)
const newKeyValue = ref('')
const keyForm = ref({ keyName: '', expiresAt: null as string | null })

const formatDate = (d: string) => dayjs(d).format('YYYY-MM-DD HH:mm')

async function loadApiKeys() {
  apiKeys.value = await authApi.listApiKeys() as any[]
}

async function changePassword() {
  changingPwd.value = true
  try {
    await authApi.changePassword(passwordForm.value)
    message.success('密码已更新')
    passwordForm.value = { oldPassword: '', newPassword: '' }
  } catch (e: any) {
    message.error(e.message)
  } finally {
    changingPwd.value = false
  }
}

async function createKey() {
  if (!keyForm.value.keyName) return
  creatingKey.value = true
  try {
    const result: any = await authApi.createApiKey({
      keyName: keyForm.value.keyName,
      expiresAt: keyForm.value.expiresAt || undefined
    })
    newKeyValue.value = result.key
    showCreateKeyDialog.value = false
    showNewKey.value = true
    keyForm.value = { keyName: '', expiresAt: null }
    await loadApiKeys()
  } catch (e: any) {
    message.error(e.message)
  } finally {
    creatingKey.value = false
  }
}

async function revokeKey(id: number) {
  try {
    await authApi.revokeApiKey(id)
    message.success('API Key 已吊销')
    await loadApiKeys()
  } catch (e: any) {
    message.error(e.message)
  }
}

function copyKey() {
  navigator.clipboard.writeText(newKeyValue.value)
  message.success('已复制到剪贴板')
}

onMounted(loadApiKeys)
</script>

<style scoped>
.settings-view { padding: 16px; max-width: 800px; }
.settings-view h2 { margin: 0 0 16px; }
.settings-card { margin-bottom: 20px; }
</style>
