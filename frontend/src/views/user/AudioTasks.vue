<script setup lang="ts">
import { ref, watch } from 'vue'
import { audioApi, type AudioTaskVO } from '@/api/audio'
import { useAuthStore } from '@/stores/auth'
import { buildResourceUrl } from '@/utils/resourceUrl'
import {
  Mic, Clock, CheckCircle2, XCircle, Play,
  RotateCcw, Calendar, Gauge, Waves, ChevronLeft, ChevronRight, Loader2, Music, FileText, X
} from 'lucide-vue-next'

const authStore = useAuthStore()
const loading = ref(true)
const tasks = ref<AudioTaskVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const showSourceModal = ref(false)
const sourceTitle = ref('')
const sourceContent = ref('')

function openSourceModal(task: AudioTaskVO) {
  sourceTitle.value = '任务 #' + task.id + ' 源文本'
  sourceContent.value = task.sourceText || ''
  showSourceModal.value = true
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = await audioApi.userTasks({
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    tasks.value = res.records
    total.value = res.total
  } catch (error) {
    console.error('Fetch audio tasks failed:', error)
  } finally {
    loading.value = false
  }
}

async function handleRefreshTask(task: AudioTaskVO) {
  try {
    const updated = await audioApi.status(task.id)
    const index = tasks.value.findIndex(t => t.id === task.id)
    if (index !== -1) {
      tasks.value[index] = updated
    }
  } catch (error) {
    console.error('Refresh task failed:', error)
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

watch(
  () => authStore.user?.id,
  () => {
    fetchTasks()
  },
  { immediate: true }
)
</script>

<template>
  <div class="max-w-[1400px] mx-auto space-y-8 pb-12">
    <!-- 顶部标题 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
          <Music class="w-6 h-6" />
        </div>
        <div>
          <h2 class="text-2xl font-bold text-white tracking-tight">音频生成记录</h2>
          <p class="text-sm text-slate-400 mt-1">查看和下载你生成的 AI 语音作品</p>
        </div>
      </div>
      
      <button 
        @click="fetchTasks" 
        class="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 transition-all border border-white/10"
      >
        <RotateCcw class="w-4 h-4" :class="{ 'animate-spin': loading }" />
        <span class="text-sm font-bold">刷新列表</span>
      </button>
    </div>

    <!-- 列表区 -->
    <div v-if="loading && tasks.length === 0" class="flex flex-col items-center justify-center py-32 space-y-4">
      <Loader2 class="w-10 h-10 text-indigo-500 animate-spin" />
      <p class="text-slate-500 font-medium">正在加载任务列表...</p>
    </div>

    <div v-else-if="tasks.length === 0" class="flex flex-col items-center justify-center py-32 space-y-6 glass-panel rounded-3xl border border-white/5">
      <div class="w-20 h-20 rounded-full bg-white/5 flex items-center justify-center text-slate-600">
        <Music class="w-10 h-10" />
      </div>
      <div class="text-center">
        <p class="text-lg font-bold text-slate-300">暂无音频任务</p>
        <p class="text-sm text-slate-500 mt-1">去笔记页面通过音频助手提交你的第一个任务吧！</p>
      </div>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      <div 
        v-for="task in tasks" 
        :key="task.id"
        class="glass-panel rounded-3xl border border-white/10 overflow-hidden flex flex-col group transition-all hover:border-indigo-500/30 hover:shadow-[0_20px_50px_rgba(0,0,0,0.3)]"
      >
        <!-- 卡片头部 -->
        <div class="p-5 border-b border-white/5 bg-white/[0.02] flex items-center justify-between">
          <div class="flex items-center gap-2">
            <span class="text-[10px] font-mono font-bold text-slate-500">ID:{{ task.id }}</span>
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
          <div class="flex items-center justify-between text-[11px] font-bold text-slate-500">
            <div class="flex items-center gap-1.5">
              <Calendar class="w-3.5 h-3.5" />
              {{ formatDate(task.createTime) }}
            </div>
          </div>

          <div class="space-y-3">
            <div class="grid grid-cols-2 gap-3">
              <div class="bg-black/20 rounded-xl p-3 border border-white/5">
                <p class="text-[9px] text-slate-500 uppercase font-bold tracking-widest mb-1">播放语速</p>
                <div class="flex items-center gap-2 text-slate-200">
                  <Gauge class="w-3.5 h-3.5 text-amber-400" />
                  <span class="text-sm font-bold">{{ task.speed.toFixed(1) }}x</span>
                </div>
              </div>
              <div class="bg-black/20 rounded-xl p-3 border border-white/5">
                <p class="text-[9px] text-slate-500 uppercase font-bold tracking-widest mb-1">音量因子</p>
                <div class="flex items-center gap-2 text-slate-200">
                  <Waves class="w-3.5 h-3.5 text-blue-400" />
                  <span class="text-sm font-bold">{{ task.noiseFactor.toFixed(1) }}</span>
                </div>
              </div>
            </div>

            <div class="bg-black/20 rounded-xl p-3 border border-white/5">
              <p class="text-[9px] text-slate-500 uppercase font-bold tracking-widest mb-1">背景音类型</p>
              <div class="flex items-center gap-2 text-indigo-300">
                <Mic class="w-3.5 h-3.5" />
                <span class="text-xs font-bold">{{ task.noiseType }}</span>
              </div>
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

        <!-- 播放器/操作区 -->
        <div class="p-5 pt-0 mt-auto">
          <template v-if="task.status === 2 && task.resultUrl">
            <audio controls :src="buildResourceUrl(task.resultUrl)" class="w-full h-10 rounded-lg filter invert hue-rotate-180 opacity-70 hover:opacity-100 transition-opacity"></audio>
            <a
              :href="buildResourceUrl(task.resultUrl)"
              download
              target="_blank"
              class="mt-3 flex items-center justify-center gap-2 w-full py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition-all shadow-lg shadow-indigo-600/20"
            >
              <Play class="w-3.5 h-3.5 fill-current" />
              下载音频文件
            </a>
          </template>
          
          <template v-else-if="task.status === -1">
            <div class="bg-rose-500/10 border border-rose-500/20 rounded-xl p-3 text-[11px] text-rose-400 font-medium">
              失败原因: {{ task.errorMsg || '系统异常' }}
            </div>
          </template>

          <template v-else>
            <button 
              @click="handleRefreshTask(task)"
              class="flex items-center justify-center gap-2 w-full py-3 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300 text-xs font-bold transition-all border border-white/10"
            >
              <RotateCcw class="w-3.5 h-3.5" />
              查询最新状态
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="flex items-center justify-between pt-8 border-t border-white/5">
      <p class="text-sm text-slate-500">共 {{ total }} 个音频任务</p>
      <div class="flex items-center gap-2">
        <button 
          @click="currentPage--; fetchTasks()"
          :disabled="currentPage === 1"
          class="p-2 rounded-xl bg-white/5 border border-white/10 text-slate-400 disabled:opacity-30 transition-all hover:bg-white/10"
        >
          <ChevronLeft class="w-5 h-5" />
        </button>
        <div class="flex items-center gap-1">
          <span class="px-3 py-1.5 rounded-lg bg-indigo-500/20 text-indigo-400 border border-indigo-500/30 text-sm font-bold">
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
                <div class="w-8 h-8 rounded-lg bg-indigo-500/10 flex items-center justify-center text-indigo-400">
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

/* 简单的音频播放器样式微调，Vite 环境下可能需要更复杂的样式覆盖 */
audio::-webkit-media-controls-panel {
  background-color: rgba(15, 23, 42, 0.9);
}
audio::-webkit-media-controls-play-button,
audio::-webkit-media-controls-current-time-display,
audio::-webkit-media-controls-time-remaining-display,
audio::-webkit-media-controls-timeline,
audio::-webkit-media-controls-mute-button,
audio::-webkit-media-controls-volume-slider {
  filter: invert(1);
}
</style>
