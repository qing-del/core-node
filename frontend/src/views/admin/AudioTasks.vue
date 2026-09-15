<script setup lang="ts">
import { ref } from 'vue'
import { audioApi, type AudioTaskVO } from '@/api/audio'
import { buildResourceUrl } from '@/utils/resourceUrl'
import {
  Clock, CheckCircle2, XCircle, RotateCcw, Calendar,
  ChevronLeft, ChevronRight, Loader2, Music, User, Search, FileText, X
} from 'lucide-vue-next'

const loading = ref(true)
const tasks = ref<AudioTaskVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const filterUserId = ref<number | undefined>()
const showSourceModal = ref(false)
const sourceTitle = ref('')
const sourceContent = ref('')

function openSourceModal(task: AudioTaskVO) {
  sourceTitle.value = '任务 #' + task.id + ' 源文本'
  sourceContent.value = task.sourceText || ''
  showSourceModal.value = true
}

async function fetchTasks() {
  // 清除无效的筛选条件
  if (filterUserId.value !== undefined && Number.isNaN(filterUserId.value)) {
    filterUserId.value = undefined
  }
  loading.value = true
  try {
    const res = await audioApi.adminList({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      userId: filterUserId.value
    })
    tasks.value = res.records
    total.value = res.total
  } catch (error) {
    console.error('Fetch admin audio tasks failed:', error)
  } finally {
    loading.value = false
  }
}

function getStatusInfo(status: number) {
  switch (status) {
    case 0: return { label: '排队中', icon: Clock, cls: 'text-amber-400 bg-amber-500/10 border-amber-500/20' }
    case 1: return { label: '合成中', icon: Loader2, cls: 'text-blue-400 bg-blue-500/10 border-blue-500/20 animate-pulse' }
    case 2: return { label: '已完成', icon: CheckCircle2, cls: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20' }
    case -1: return { label: '失败', icon: XCircle, cls: 'text-rose-400 bg-rose-500/10 border-rose-500/20' }
    default: return { label: '未知', icon: Clock, cls: 'text-slate-400 bg-slate-500/10 border-slate-500/20' }
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString()
}

function handleSearch() {
  currentPage.value = 1
  fetchTasks()
}

import { onMounted } from 'vue'

onMounted(() => {
  fetchTasks()
})
</script>

<template>
  <div class="max-w-[1400px] mx-auto space-y-8 pb-12">
    <!-- 顶部标题与筛选 -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-2xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
          <Music class="w-6 h-6" />
        </div>
        <div>
          <h2 class="text-2xl font-bold text-white tracking-tight">音频任务监控</h2>
          <p class="text-sm text-slate-400 mt-1">管理系统内所有的 AI 音频生成任务</p>
        </div>
      </div>
      
      <div class="flex items-center gap-4">
        <!-- 用户筛选 -->
        <div class="relative group">
          <div class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
            <User class="w-4 h-4" />
          </div>
          <input 
            v-model.number="filterUserId" 
            type="number" 
            placeholder="按用户 ID 筛选..." 
            class="bg-black/20 border border-white/10 rounded-xl py-2.5 pl-10 pr-4 text-sm text-slate-200 outline-none focus:border-rose-500/50 focus:ring-4 focus:ring-rose-500/5 transition-all w-48"
            @keyup.enter="handleSearch"
          />
        </div>

        <button 
          @click="handleSearch"
          class="p-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white transition-all shadow-lg shadow-rose-600/20"
        >
          <Search class="w-5 h-5" />
        </button>

        <div class="w-px h-8 bg-white/10 mx-2" />

        <button 
          @click="fetchTasks" 
          class="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 transition-all border border-white/10"
        >
          <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
          <span class="text-sm font-bold">刷新</span>
        </button>
      </div>
    </div>

    <!-- 列表区 -->
    <div v-if="loading && tasks.length === 0" class="flex flex-col items-center justify-center py-32 space-y-4">
      <Loader2 class="w-10 h-10 text-rose-500 animate-spin" />
      <p class="text-slate-500 font-medium">正在获取系统任务数据...</p>
    </div>

    <div v-else-if="tasks.length === 0" class="flex flex-col items-center justify-center py-32 space-y-6 glass-panel rounded-3xl border border-white/5 text-center">
      <Music class="w-16 h-16 text-slate-700" />
      <div>
        <p class="text-lg font-bold text-slate-300">未找到相关任务</p>
        <p class="text-sm text-slate-500 mt-1">尝试更换筛选条件或稍后再试</p>
      </div>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <div 
        v-for="task in tasks" 
        :key="task.id"
        class="glass-panel rounded-3xl border border-white/10 overflow-hidden flex flex-col group transition-all hover:border-rose-500/30 hover:shadow-[0_20px_50px_rgba(0,0,0,0.3)]"
      >
        <!-- 卡片头部 -->
        <div class="p-5 border-b border-white/5 bg-white/[0.02] flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-[10px] font-mono font-bold text-slate-500">ID:{{ task.id }}</span>
            <span class="text-[10px] font-mono font-bold text-sky-400">· 用户#{{ task.userId }}</span>
          </div>
          <div 
            class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[10px] font-bold uppercase tracking-wider border"
            :class="getStatusInfo(task.status).cls"
          >
            <component :is="getStatusInfo(task.status).icon" class="w-3 h-3" />
            {{ getStatusInfo(task.status).label }}
          </div>
        </div>

        <!-- 卡片主体 -->
        <div class="p-5 flex-1 space-y-4">
          <div class="flex items-center text-[11px] font-bold text-slate-500">
            <Calendar class="w-3.5 h-3.5 mr-2" />
            {{ formatDate(task.createTime) }}
          </div>

          <div class="space-y-3">
            <div class="grid grid-cols-2 gap-3">
              <div class="bg-black/20 rounded-xl p-3 border border-white/5 text-center">
                <p class="text-[9px] text-slate-500 uppercase font-bold tracking-widest mb-1">倍速</p>
                <div class="text-sm font-bold text-slate-200">{{ task.speed.toFixed(1) }}x</div>
              </div>
              <div class="bg-black/20 rounded-xl p-3 border border-white/5 text-center">
                <p class="text-[9px] text-slate-500 uppercase font-bold tracking-widest mb-1">背景因子</p>
                <div class="text-sm font-bold text-slate-200">{{ task.noiseFactor.toFixed(1) }}</div>
              </div>
            </div>

            <div class="bg-black/20 rounded-xl p-3 border border-white/5 flex items-center justify-between">
              <span class="text-[9px] text-slate-500 uppercase font-bold tracking-widest">噪音类型</span>
              <span class="text-xs font-bold text-rose-300">{{ task.noiseType }}</span>
            </div>

            <!-- 源文本 -->
            <div>
              <button
                @click="openSourceModal(task)"
                class="flex items-center justify-center gap-2 w-full py-2 rounded-xl bg-white/5 hover:bg-white/10 text-slate-400 text-[10px] font-bold transition-all border border-white/10"
              >
                <FileText class="w-3.5 h-3.5" />
                查看文本
              </button>
            </div>
          </div>
        </div>

        <!-- 结果区 -->
        <div class="p-5 pt-0 mt-auto">
          <template v-if="task.status === 2 && task.resultUrl">
            <a
              :href="buildResourceUrl(task.resultUrl)"
              target="_blank"
              class="flex items-center justify-center gap-2 w-full py-2.5 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-bold transition-all border border-white/10"
            >
              预览结果音频
            </a>
          </template>
          
          <template v-else-if="task.status === -1">
            <div class="bg-rose-500/5 border border-rose-500/10 rounded-xl p-3 text-[10px] text-rose-400/70 font-medium line-clamp-2">
              错误: {{ task.errorMsg || '未知故障' }}
            </div>
          </template>

          <template v-else>
            <div class="py-3 text-center text-[10px] text-slate-600 font-bold uppercase tracking-widest italic">
              Processing...
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="flex items-center justify-between pt-8 border-t border-white/5">
      <p class="text-sm text-slate-500">系统内共 {{ total }} 个任务</p>
      <div class="flex items-center gap-2">
        <button 
          @click="currentPage--; fetchTasks()"
          :disabled="currentPage === 1"
          class="p-2 rounded-xl bg-white/5 border border-white/10 text-slate-400 disabled:opacity-30 transition-all hover:bg-white/10"
        >
          <ChevronLeft class="w-5 h-5" />
        </button>
        <div class="flex items-center gap-1">
          <span class="px-3 py-1.5 rounded-lg bg-rose-500/20 text-rose-400 border border-rose-500/30 text-sm font-bold">
            {{ currentPage }}
          </span>
          <span class="text-slate-600 px-1">/</span>
          <span class="text-sm font-bold text-slate-500">{{ Math.ceil(total / pageSize) }}</span>
        </div>
        <button 
          @click="currentPage++; fetchTasks()"
          :disabled="currentPage >= Math.ceil(total / pageSize)"
          class="p-2 rounded-xl bg-white/5 border border-white/10 text-slate-400 disabled:opacity-30 transition-all hover:bg-white/10"
        >
          <ChevronRight class="w-5 h-5" />
        </button>
      </div>
    </div>

    <!-- 源文本弹窗 -->
    <Teleport to="body">
      <Transition name="modal-backdrop">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] bg-black/90 backdrop-blur-md" @click="showSourceModal = false" />
      </Transition>
      <Transition name="modal-panel">
        <div v-if="showSourceModal" class="fixed inset-0 z-[60] flex items-center justify-center px-4 pointer-events-none">
          <div class="glass-panel modal-card relative z-10 max-w-2xl w-full max-h-[80vh] rounded-2xl overflow-hidden flex flex-col pointer-events-auto">
            <div class="flex items-center justify-between px-6 py-4 border-b border-white/10">
              <div class="flex items-center space-x-3">
                <div class="w-8 h-8 rounded-lg bg-rose-500/10 flex items-center justify-center text-rose-400">
                  <FileText class="w-4 h-4" />
                </div>
                <h3 class="text-sm font-bold text-white truncate max-w-[400px]">{{ sourceTitle }}</h3>
              </div>
              <button class="text-slate-500 hover:text-white p-2 rounded-full hover:bg-white/5" @click="showSourceModal = false">
                <X class="w-5 h-5" />
              </button>
            </div>
            <div class="flex-1 overflow-y-auto p-6">
              <pre class="text-sm text-slate-300 font-mono whitespace-pre-wrap break-all">{{ sourceContent }}</pre>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.glass-panel {
  background: rgba(255, 255, 255, 0.02);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.modal-backdrop-enter-active,
.modal-backdrop-leave-active { transition: opacity 0.28s ease; }
.modal-backdrop-enter-from,
.modal-backdrop-leave-to { opacity: 0; }

.modal-panel-enter-active,
.modal-panel-leave-active {
  transition: opacity 0.32s ease, transform 0.42s cubic-bezier(0.25, 1, 0.5, 1);
}
.modal-panel-enter-from,
.modal-panel-leave-to { opacity: 0; transform: translateY(18px) scale(0.96); }
.modal-panel-enter-to,
.modal-panel-leave-from { opacity: 1; transform: translateY(0) scale(1); }

.modal-card {
  transform-origin: center center;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.05);
}
</style>
