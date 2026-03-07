<template>
  <div class="app-layout">
    <!-- 桌面端：左侧边栏 -->
    <aside v-if="!isMobile" class="sidebar">
      <div class="sidebar-header">
        <span class="logo-text">家庭资产</span>
        <n-dropdown :options="themeOptions" @select="themeStore.setMode">
          <n-button text class="theme-btn">
            <n-icon :size="18" :component="themeIcon" />
          </n-button>
        </n-dropdown>
      </div>
      <n-menu
        :options="menuOptions"
        :value="currentRoute"
        @update:value="navigate"
      />
      <div class="sidebar-footer">
        <n-dropdown :options="userMenuOptions" @select="handleUserMenu">
          <div class="user-info">
            <n-avatar round size="small">{{ userInitial }}</n-avatar>
            <span class="username">{{ user?.displayName || user?.username }}</span>
          </div>
        </n-dropdown>
      </div>
    </aside>

    <!-- 主内容区 -->
    <main class="main-content" :class="{ 'with-bottom-nav': isMobile }">
      <router-view />
    </main>

    <!-- 移动端：底部导航 -->
    <nav v-if="isMobile" class="bottom-nav">
      <div
        v-for="item in mobileNavItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: currentRoute === item.key }"
        @click="navigate(item.key)"
      >
        <n-icon :size="22">
          <component :is="item.icon" />
        </n-icon>
        <span class="nav-label">{{ item.label }}</span>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBreakpoints, breakpointsTailwind } from '@vueuse/core'
import { NIcon } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import {
  HomeOutline, WalletOutline, TrendingUpOutline, SettingsOutline,
  SunnyOutline, MoonOutline, PartlySunnyOutline
} from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const breakpoints = useBreakpoints(breakpointsTailwind)
const isMobile = breakpoints.smaller('md')

const themeOptions = [
  { label: '浅色', key: 'light', icon: () => h(NIcon, null, { default: () => h(SunnyOutline) }) },
  { label: '深色', key: 'dark', icon: () => h(NIcon, null, { default: () => h(MoonOutline) }) },
  { label: '跟随系统', key: 'system', icon: () => h(NIcon, null, { default: () => h(PartlySunnyOutline) }) },
]

const themeIcon = computed(() => {
  if (themeStore.mode === 'dark') return MoonOutline
  if (themeStore.mode === 'light') return SunnyOutline
  return PartlySunnyOutline
})

const user = computed(() => authStore.user)
const userInitial = computed(() => (user.value?.displayName || user.value?.username || '?')[0])
const currentRoute = computed(() => route.name as string)

const menuOptions = [
  { label: '资产大盘', key: 'dashboard', icon: () => h(HomeOutline) },
  { label: '账户管理', key: 'accounts', icon: () => h(WalletOutline) },
  { label: '投资持仓', key: 'holdings', icon: () => h(TrendingUpOutline) },
  { label: '设置', key: 'settings', icon: () => h(SettingsOutline) }
]

const mobileNavItems = [
  { key: 'dashboard', label: '大盘', icon: HomeOutline },
  { key: 'accounts', label: '账户', icon: WalletOutline },
  { key: 'holdings', label: '持仓', icon: TrendingUpOutline },
  { key: 'settings', label: '设置', icon: SettingsOutline }
]

const userMenuOptions = [
  { label: '退出登录', key: 'logout' }
]

function navigate(key: string) {
  router.push({ name: key })
}

function handleUserMenu(key: string) {
  if (key === 'logout') {
    authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  border-right: 1px solid var(--hw-border);
  display: flex;
  flex-direction: column;
  background: var(--hw-sidebar-bg);
  transition: background 0.2s, border-color 0.2s;
}

.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid var(--hw-border);
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #18a058;
  flex: 1;
}

.theme-btn {
  color: var(--hw-text-muted);
}
.theme-btn:hover {
  color: var(--hw-text);
}

.sidebar-footer {
  margin-top: auto;
  padding: 16px;
  border-top: 1px solid var(--hw-border);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: background 0.2s;
}
.user-info:hover { background: var(--hw-hover-bg); }
.username { font-size: 14px; color: var(--hw-text); }

.main-content {
  flex: 1;
  overflow-y: auto;
  background: var(--hw-page-bg);
  transition: background 0.2s;
}
.main-content.with-bottom-nav {
  padding-bottom: 64px;
}

.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: var(--hw-sidebar-bg);
  border-top: 1px solid var(--hw-border);
  display: flex;
  align-items: center;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
  transition: background 0.2s, border-color 0.2s;
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
  color: var(--hw-nav-inactive);
  transition: color 0.2s;
}
.nav-item.active { color: #18a058; }
.nav-label { font-size: 11px; }
</style>
