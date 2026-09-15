<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  ChevronLeft, Zap, LayoutDashboard, FileText, Layers, Hash,
  Image as ImageIcon, LogOut, Search, Bell, Music, Globe
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const isLoading = ref(true)

const menuItems = computed(() => [
  { id: 'dashboard', label: '工作台', icon: LayoutDashboard, to: '/user/dashboard' },
  { id: 'notes', label: '我的笔记', icon: FileText, to: '/user/notes' },
  { id: 'public-notes', label: '公共笔记广场', icon: Globe, to: '/user/public-notes' },
  { id: 'topics', label: '主题管理', icon: Layers, to: '/user/topics' },
  { id: 'tags', label: '标签生态', icon: Hash, to: '/user/tags' },
  { id: 'images', label: '图床画廊', icon: ImageIcon, to: '/user/images' },
  { id: 'audio', label: '音频中心', icon: Music, to: '/user/audio' }
])

const activeMenu = computed(() => menuItems.value.find(item => isMenuActive(item.id)))

function isMenuActive(id: string): boolean {
  return route.path.startsWith(`/user/${id}`) || (id === 'dashboard' && route.path === '/user')
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function getRoleText(roleId: number) {
  if (roleId === 1 || roleId === 2) return 'Administrator'
  if (roleId === 3) return 'User'
  if (roleId === 4) return 'VIP User'
  return 'Unknown'
}

function goToProfile() {
  router.push('/user/profile')
}

function handleLogout() {
  authStore.logout()
}

onMounted(async () => {
  try {
    await authStore.fetchUserInfo()
  } catch (error) {
    console.error('获取用户信息失败', error)
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <div class="cn-shell">
    <aside :class="['cn-sidebar', isCollapsed ? 'w-20' : 'w-64']">
      <button class="sidebar-toggle" type="button" aria-label="Toggle sidebar" @click="toggleSidebar">
        <ChevronLeft :class="['h-3.5 w-3.5 transition-transform', isCollapsed ? 'rotate-180' : '']" />
      </button>

      <div class="flex h-16 items-center gap-3 border-b border-[var(--cn-border)] px-5">
        <div class="cn-brand-mark">
          <Zap class="h-4 w-4" />
        </div>
        <div :class="['min-w-0 transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">
          <div class="cn-brand-wordmark whitespace-nowrap">CORE<span> NODE</span></div>
          <div class="text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--cn-text-muted)]">Workspace</div>
        </div>
      </div>

      <nav class="flex-1 overflow-y-auto px-3 py-5">
        <template v-if="isLoading">
          <div v-for="i in 4" :key="i" class="skeleton mb-2 h-10 rounded-lg"></div>
        </template>
        <template v-else>
          <router-link
            v-for="item in menuItems"
            :key="item.id"
            :to="item.to"
            :class="['glass-nav-item mb-1 flex h-10 items-center gap-3 px-3 text-sm font-medium', isMenuActive(item.id) ? 'glass-nav-active' : '']"
          >
            <component :is="item.icon" class="h-[18px] w-[18px] shrink-0" />
            <span :class="['menu-label truncate transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">{{ item.label }}</span>
          </router-link>
        </template>
      </nav>

      <div class="border-t border-[var(--cn-border)] p-3">
        <button class="glass-logout-btn flex h-10 w-full items-center gap-3 px-3 text-sm font-medium" type="button" @click="handleLogout">
          <LogOut class="h-[18px] w-[18px] shrink-0" />
          <span :class="['truncate transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">Logout</span>
        </button>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="cn-topbar">
        <div class="min-w-0">
          <div class="truncate text-sm font-semibold text-[var(--cn-text)]">{{ activeMenu?.label || 'Dashboard' }}</div>
          <div class="mt-0.5 text-xs text-[var(--cn-text-muted)]">CORE NODE workspace</div>
        </div>

        <div class="flex items-center gap-3">
          <button class="topbar-icon" type="button" aria-label="Search">
            <Search class="h-4 w-4" />
          </button>
          <button class="topbar-icon relative" type="button" aria-label="Notifications">
            <Bell class="h-4 w-4" />
            <span class="absolute right-2 top-2 h-1.5 w-1.5 rounded-full bg-[var(--cn-danger)]"></span>
          </button>

          <button class="user-chip" type="button" @click="goToProfile">
            <template v-if="isLoading">
              <span class="skeleton h-7 w-20 rounded-md"></span>
            </template>
            <template v-else>
              <span class="hidden text-right sm:block">
                <span class="block max-w-[10rem] truncate text-sm font-semibold">{{ authStore.user?.nickname || authStore.user?.username || 'User' }}</span>
                <span class="block text-[10px] uppercase tracking-[0.16em] text-[var(--cn-text-muted)]">{{ getRoleText(authStore.user?.roleId ?? 0) }}</span>
              </span>
              <span class="avatar">{{ (authStore.user?.nickname || authStore.user?.username || 'U').charAt(0).toUpperCase() }}</span>
            </template>
          </button>
        </div>
      </header>

      <main class="cn-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.sidebar-toggle {
  position: absolute;
  right: -12px;
  top: 22px;
  z-index: 30;
  display: flex;
  height: 24px;
  width: 24px;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cn-border);
  border-radius: 999px;
  background: var(--cn-surface);
  color: var(--cn-text-muted);
  box-shadow: var(--cn-shadow-xs);
}

.sidebar-toggle:hover,
.topbar-icon:hover,
.user-chip:hover {
  border-color: var(--cn-border-strong);
  background: var(--cn-surface-muted);
  color: var(--cn-text);
}

.topbar-icon {
  display: inline-flex;
  height: 34px;
  width: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--cn-radius-sm);
  color: var(--cn-text-muted);
  transition: all var(--cn-fast) var(--cn-ease);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid transparent;
  border-radius: var(--cn-radius-md);
  padding: 4px 6px 4px 10px;
  color: var(--cn-text);
  transition: all var(--cn-fast) var(--cn-ease);
}

.avatar {
  display: inline-flex;
  height: 32px;
  width: 32px;
  align-items: center;
  justify-content: center;
  border-radius: var(--cn-radius-sm);
  background: var(--cn-accent);
  color: var(--cn-text-inverse);
  font-size: 12px;
  font-weight: 760;
}
</style>
