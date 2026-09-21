<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  adminApi,
  type AdminDocumentTreeNode
} from '@/api/admin'
import {
  buildSnapshotCleanupSummary,
  canManageDocumentSnapshotHistory,
  expandedOwnerIdsForSearch,
  filterAdminDocumentTree,
  uniqueDocumentIds
} from '@/utils/adminDocument'
import { alertSuccess, alertWarning, confirmAction, toastError } from '@/utils/feedback'
import { readAuthSession } from '@/utils/authSession'
import {
  Check, ChevronDown, ChevronRight, DatabaseZap, FileText,
  FolderTree, HardDrive, Loader2, RefreshCw, Search, Trash2, UserRound, X
} from 'lucide-vue-next'

const loading = ref(true)
const clearing = ref(false)
const tree = ref<AdminDocumentTreeNode[]>([])
const keyword = ref('')
const expandedUserIds = ref<Set<number>>(new Set())
const selectedDocumentIds = ref<Set<number>>(new Set())

const canClearSnapshotHistory = computed(() =>
  canManageDocumentSnapshotHistory(readAuthSession().scopes)
)
const filteredTree = computed(() => filterAdminDocumentTree(tree.value, keyword.value))
const selectedCount = computed(() => selectedDocumentIds.value.size)
const visibleDocumentCount = computed(() =>
  filteredTree.value.reduce((count, owner) => count + owner.documents.length, 0)
)

watch([keyword, tree], ([value]) => {
  if (!value.trim()) return
  expandedUserIds.value = new Set(expandedOwnerIdsForSearch(tree.value, value))
})

function ownerLabel(owner: AdminDocumentTreeNode): string {
  return owner.nickname?.trim() || owner.username?.trim() || `用户 #${owner.userId}`
}

function isExpanded(userId: number): boolean {
  return expandedUserIds.value.has(userId)
}

function toggleExpanded(userId: number): void {
  const next = new Set(expandedUserIds.value)
  if (next.has(userId)) next.delete(userId)
  else next.add(userId)
  expandedUserIds.value = next
}

function isSelected(documentId: number): boolean {
  return selectedDocumentIds.value.has(documentId)
}

function setDocumentSelected(documentId: number, selected: boolean): void {
  const next = new Set(selectedDocumentIds.value)
  if (selected) next.add(documentId)
  else next.delete(documentId)
  selectedDocumentIds.value = next
}

function areAllOwnerDocumentsSelected(owner: AdminDocumentTreeNode): boolean {
  return owner.documents.length > 0 && owner.documents.every(document => isSelected(document.documentId))
}

function setOwnerDocumentsSelected(owner: AdminDocumentTreeNode, selected: boolean): void {
  const next = new Set(selectedDocumentIds.value)
  for (const document of owner.documents) {
    if (selected) next.add(document.documentId)
    else next.delete(document.documentId)
  }
  selectedDocumentIds.value = next
}

function clearSelection(): void {
  selectedDocumentIds.value = new Set()
}

function formatDate(timestamp: number): string {
  if (!Number.isFinite(timestamp) || timestamp <= 0) return '-'
  return new Date(timestamp).toLocaleString('zh-CN', { hour12: false })
}

async function loadTree(): Promise<void> {
  loading.value = true
  try {
    tree.value = await adminApi.getDocumentTree()
  } catch (error) {
    console.error('Fetch admin document tree failed:', error)
    tree.value = []
  } finally {
    loading.value = false
  }
}

async function clearSnapshotHistory(): Promise<void> {
  const documentIds = uniqueDocumentIds(selectedDocumentIds.value)
  if (documentIds.length === 0 || clearing.value) return

  const confirmed = await confirmAction({
    title: '清理历史快照',
    content: `确定清理所选 ${documentIds.length} 篇文档的历史快照吗？将保留当前快照，仅删除历史或孤儿 .bin 对象；此操作不可恢复。`,
    okText: '确认清理',
    danger: true
  })
  if (!confirmed) return

  clearing.value = true
  try {
    const result = await adminApi.clearDocumentSnapshotHistory(documentIds)
    const failedIds = uniqueDocumentIds(result.documents
      .filter(item => !item.success)
      .map(item => item.documentId))
    selectedDocumentIds.value = new Set(failedIds)

    const summary = buildSnapshotCleanupSummary(result)
    if (failedIds.length > 0) alertWarning(summary, '部分历史快照未清理')
    else alertSuccess(summary, '历史快照清理完成')
    await loadTree()
  } catch (error) {
    console.error('Clear document snapshot history failed:', error)
    toastError('清理历史快照失败，请稍后重试')
  } finally {
    clearing.value = false
  }
}

onMounted(() => {
  void loadTree()
})
</script>

<template>
  <div class="mx-auto max-w-[1400px] space-y-8 pb-12">
    <header class="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
      <div class="flex items-center gap-4">
        <div class="flex h-12 w-12 items-center justify-center rounded-2xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
          <FolderTree class="h-6 w-6" />
        </div>
        <div>
          <h2 class="text-2xl font-bold tracking-tight text-white">协作文档</h2>
          <p class="mt-1 text-sm text-slate-400">浏览全部正常协作文档，并维护不再使用的快照对象</p>
        </div>
      </div>

      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <label class="relative block">
          <Search class="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索用户、文档标题或 ID"
            class="w-full rounded-xl border border-white/10 bg-black/20 py-2.5 pl-10 pr-4 text-sm text-slate-200 outline-none transition-all placeholder:text-slate-600 focus:border-cyan-500/50 focus:ring-4 focus:ring-cyan-500/5 sm:w-64"
          >
        </label>
        <button
          type="button"
          :disabled="loading || clearing"
          class="flex items-center justify-center gap-2 rounded-xl border border-white/10 bg-white/5 px-4 py-2.5 text-sm font-bold text-slate-300 transition-all hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
          @click="loadTree"
        >
          <RefreshCw class="h-4 w-4" :class="{ 'animate-spin': loading }" />
          刷新
        </button>
      </div>
    </header>

    <section class="glass-panel flex flex-col gap-4 rounded-2xl border border-white/10 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div class="flex items-center gap-3 text-sm">
        <div class="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-300">
          <HardDrive class="h-4 w-4" />
        </div>
        <div>
          <p class="font-bold text-slate-200">已选择 {{ selectedCount }} 篇文档</p>
          <p class="text-xs text-slate-500">当前目录显示 {{ visibleDocumentCount }} 篇文档</p>
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-if="selectedCount > 0"
          type="button"
          :disabled="clearing"
          class="inline-flex items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-bold text-slate-400 transition hover:bg-white/5 hover:text-slate-200 disabled:opacity-50"
          @click="clearSelection"
        >
          <X class="h-3.5 w-3.5" /> 清空选择
        </button>
        <button
          v-if="canClearSnapshotHistory"
          type="button"
          :disabled="selectedCount === 0 || clearing"
          class="inline-flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-500/15 px-4 py-2.5 text-sm font-bold text-rose-200 transition hover:bg-rose-500/25 disabled:cursor-not-allowed disabled:opacity-50"
          @click="clearSnapshotHistory"
        >
          <Loader2 v-if="clearing" class="h-4 w-4 animate-spin" />
          <Trash2 v-else class="h-4 w-4" />
          清理历史快照
        </button>
      </div>
    </section>

    <div v-if="loading" class="glass-panel flex flex-col items-center justify-center rounded-3xl border border-white/5 py-28 text-slate-400">
      <Loader2 class="h-9 w-9 animate-spin text-cyan-400" />
      <p class="mt-4 text-sm font-medium">正在读取协作文档目录...</p>
    </div>

    <div v-else-if="filteredTree.length === 0" class="glass-panel flex flex-col items-center justify-center rounded-3xl border border-white/5 py-28 text-center">
      <FolderTree class="h-14 w-14 text-slate-700" />
      <p class="mt-5 text-lg font-bold text-slate-300">{{ keyword ? '没有匹配的协作文档' : '暂无协作文档' }}</p>
      <p class="mt-1 text-sm text-slate-500">{{ keyword ? '尝试更换搜索条件' : '正常文档将在这里按所有者归类展示' }}</p>
    </div>

    <section v-else class="space-y-3" aria-label="协作文档目录">
      <article v-for="owner in filteredTree" :key="owner.userId" class="glass-panel overflow-hidden rounded-2xl border border-white/10">
        <div class="flex min-h-16 flex-wrap items-center gap-3 px-4 py-3 sm:px-5">
          <button
            type="button"
            class="flex min-w-0 flex-1 items-center gap-3 text-left"
            :aria-expanded="isExpanded(owner.userId)"
            @click="toggleExpanded(owner.userId)"
          >
            <ChevronDown v-if="isExpanded(owner.userId)" class="h-4 w-4 shrink-0 text-slate-500" />
            <ChevronRight v-else class="h-4 w-4 shrink-0 text-slate-500" />
            <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-sky-500/20 bg-sky-500/10 text-sky-300">
              <UserRound class="h-4 w-4" />
            </span>
            <span class="min-w-0">
              <span class="block truncate text-sm font-bold text-slate-100">{{ ownerLabel(owner) }}</span>
              <span class="mt-0.5 block text-xs text-slate-500">
                用户 #{{ owner.userId }}<template v-if="owner.username && owner.nickname"> · {{ owner.username }}</template>
                · {{ owner.documents.length }} 篇文档
              </span>
            </span>
          </button>
          <label class="inline-flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 text-xs font-bold text-slate-400 transition hover:bg-white/5 hover:text-slate-200">
            <input
              type="checkbox"
              class="h-4 w-4 accent-cyan-500"
              :checked="areAllOwnerDocumentsSelected(owner)"
              :disabled="clearing"
              @change="setOwnerDocumentsSelected(owner, ($event.target as HTMLInputElement).checked)"
            >
            选择本组
          </label>
        </div>

        <div v-show="isExpanded(owner.userId)" class="border-t border-white/5 bg-black/10">
          <div
            v-for="document in owner.documents"
            :key="document.documentId"
            class="flex flex-col gap-3 border-b border-white/5 px-5 py-4 last:border-b-0 sm:flex-row sm:items-center sm:gap-4"
          >
            <label class="flex cursor-pointer items-center gap-3 sm:w-8">
              <input
                type="checkbox"
                class="h-4 w-4 accent-cyan-500"
                :checked="isSelected(document.documentId)"
                :disabled="clearing"
                :aria-label="`选择文档 ${document.title}`"
                @change="setDocumentSelected(document.documentId, ($event.target as HTMLInputElement).checked)"
              >
              <span class="sr-only">选择 {{ document.title }}</span>
            </label>
            <FileText class="hidden h-4 w-4 shrink-0 text-cyan-300 sm:block" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-semibold text-slate-200">{{ document.title }}</p>
              <p class="mt-1 text-xs text-slate-500">文档 #{{ document.documentId }} · 最近修改 {{ formatDate(document.lastModifyTime) }}</p>
            </div>
            <div class="flex flex-wrap items-center gap-2 text-xs">
              <span class="rounded-md border border-white/10 bg-white/5 px-2 py-1 text-slate-400">修改者 #{{ document.lastModifyUserId ?? '-' }}</span>
              <span
                class="inline-flex items-center gap-1 rounded-md border px-2 py-1 font-medium"
                :class="document.hasSnapshot ? 'border-emerald-500/20 bg-emerald-500/10 text-emerald-300' : 'border-amber-500/20 bg-amber-500/10 text-amber-300'"
              >
                <Check v-if="document.hasSnapshot" class="h-3 w-3" />
                <DatabaseZap v-else class="h-3 w-3" />
                {{ document.hasSnapshot ? '已有当前快照' : '暂无当前快照' }}
              </span>
            </div>
          </div>
        </div>
      </article>
    </section>

    <p v-if="canClearSnapshotHistory" class="flex items-start gap-2 px-1 text-xs leading-5 text-slate-500">
      <DatabaseZap class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-400" />
      清理仅会删除未被当前快照指针引用的历史或孤儿快照；服务端将返回实际删除对象数和释放空间。
    </p>
  </div>
</template>

<style scoped>
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}
</style>
