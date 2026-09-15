<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  ChevronLeft, Shield, LayoutDashboard, ShieldAlert, Users, FileText,
  Layers, Hash, Image as ImageIcon, Mail, Power, Search, Bell, Music
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isCollapsed = ref(false)
const isLoading = ref(true)

const menuItems = computed(() => [
  { id: 'dashboard', label: '控制台大盘', icon: LayoutDashboard, to: '/admin/dashboard' },
  { id: 'audit', label: '审核大厅', icon: ShieldAlert, to: '/admin/audit' },
  { id: 'users', label: '用户管理', icon: Users, to: '/admin/users' },
  { id: 'notes', label: '全局笔记', icon: FileText, to: '/admin/notes' },
  { id: 'topics', label: '主题调度', icon: Layers, to: '/admin/topics' },
  { id: 'tags', label: '标签矩阵', icon: Hash, to: '/admin/tags' },
  { id: 'images', label: '云端图床', icon: ImageIcon, to: '/admin/images' },
  { id: 'audio', label: '音频任务', icon: Music, to: '/admin/audio' },
  { id: 'email', label: '邮件中心', icon: Mail, to: '/admin/email' }
])

const activeMenu = computed(() => menuItems.value.find(item => isMenuActive(item.id)))

function isMenuActive(id: string): boolean {
  if (id === 'dashboard' && (route.path === '/admin' || route.path === '/admin/')) return true
  return route.path.startsWith(`/admin/${id}`)
}

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

function goToProfile() {
  router.push('/admin/profile')
}

function handleLogout() {
  authStore.adminLogout()
}

onMounted(async () => {
  try {
    await authStore.fetchAdminUserInfo()
  } catch (error) {
    console.error('获取管理员信息失败', error)
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
          <Shield class="h-4 w-4" />
        </div>
        <div :class="['min-w-0 transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">
          <div class="flex items-center gap-2 whitespace-nowrap">
            <div class="cn-brand-wordmark">CORE<span> NODE</span></div>
            <span class="rounded border border-[var(--cn-border-strong)] px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-[0.16em] text-[var(--cn-text-muted)]">Admin</span>
          </div>
          <div class="text-[10px] font-semibold uppercase tracking-[0.22em] text-[var(--cn-text-muted)]">Control room</div>
        </div>
      </div>

      <nav class="flex-1 overflow-y-auto px-3 py-5">
        <template v-if="isLoading">
          <div v-for="i in 5" :key="i" class="skeleton mb-2 h-10 rounded-lg"></div>
        </template>
        <template v-else>
          <router-link
            v-for="item in menuItems"
            :key="item.id"
            :to="item.to"
            :class="['glass-nav-item relative mb-1 flex h-10 items-center gap-3 px-3 text-sm font-medium', isMenuActive(item.id) ? 'glass-nav-active' : '']"
          >
            <component :is="item.icon" class="h-[18px] w-[18px] shrink-0" />
            <span :class="['menu-label truncate transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">{{ item.label }}</span>
            <span v-if="item.id === 'audit' && !isCollapsed" class="ml-auto h-1.5 w-1.5 rounded-full bg-[var(--cn-danger)]"></span>
          </router-link>
        </template>
      </nav>

      <div class="border-t border-[var(--cn-border)] p-3">
        <button class="glass-logout-btn flex h-10 w-full items-center gap-3 px-3 text-sm font-medium" type="button" @click="handleLogout">
          <Power class="h-[18px] w-[18px] shrink-0" />
          <span :class="['truncate transition-all', isCollapsed ? 'w-0 opacity-0' : 'opacity-100']">Logout</span>
        </button>
      </div>
    </aside>

    <div class="flex min-w-0 flex-1 flex-col">
      <header class="cn-topbar">
        <div class="min-w-0">
          <div class="truncate text-sm font-semibold text-[var(--cn-text)]">{{ activeMenu?.label || 'Dashboard' }}</div>
          <div class="mt-0.5 text-xs text-[var(--cn-text-muted)]">CORE NODE administration</div>
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
                <span class="block max-w-[10rem] truncate text-sm font-semibold">{{ authStore.user?.nickname || authStore.user?.username || 'Admin' }}</span>
                <span class="block text-[10px] uppercase tracking-[0.16em] text-[var(--cn-text-muted)]">Administrator</span>
              </span>
              <span class="avatar">{{ (authStore.user?.nickname || authStore.user?.username || 'A').charAt(0).toUpperCase() }}</span>
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
