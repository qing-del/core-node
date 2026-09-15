import request from '@/utils/request'

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface AudioTaskVO {
  id: number
  sourceText: string
  speed: number
  noiseType: string
  noiseFactor: number
  /** 任务状态：0=排队中, 1=合成中, 2=已完成, -1=失败, -2=已重试, -3=已取消 */
  status: number
  resultUrl?: string
  audioSize?: number
  errorMsg?: string
  userId: number
  createTime: string
  completedDate?: string
}

export interface AudioTaskPageQueryDTO {
  userId?: number
  pageNum: number
  pageSize: number
  status?: number
}

export interface AudioTaskStatisticsVO {
  todaySuccessCount: number
  todayFailedCount: number
  pendingCount: number
  processingCount: number
}

export interface AudioTaskSubmitDTO {
  text: string
  speed: number
  noiseType: string
  noiseFactor?: number
}

export interface AudioTaskSubmitVO {
  taskId: number
  status: number
}

export const audioApi = {
  /**
   * 管理端：分页查询音频任务列表
   */
  adminList(params: AudioTaskPageQueryDTO) {
    return request<PageResult<AudioTaskVO>>({
      url: '/admin/audio/list',
      method: 'POST',
      data: params
    })
  },

  /**
   * 管理端：查询今日成功/失败和当前等待/处理中的任务统计
   */
  adminStatistics() {
    return request<AudioTaskStatisticsVO>({
      url: '/admin/audio/statistics',
      method: 'GET'
    })
  },

  /**
   * 管理端：查询任意用户的音频任务详情
   */
  adminDetail(taskId: number | string) {
    return request<AudioTaskVO>({
      url: `/admin/audio/${taskId}`,
      method: 'GET'
    })
  },

  /**
   * 管理端：取消任意用户排队中或合成中的音频任务
   */
  adminCancel(taskId: number | string) {
    return request<boolean>({
      url: `/admin/audio/cancel/${taskId}`,
      method: 'POST'
    })
  },

  /**
   * 管理端：删除任意用户的音频任务及其生成资源
   */
  adminDelete(taskId: number | string) {
    return request<boolean>({
      url: `/admin/audio/${taskId}`,
      method: 'DELETE'
    })
  },

  /**
   * 用户端：提交音频生成任务
   */
  generate(data: AudioTaskSubmitDTO) {
    return request<AudioTaskSubmitVO>({
      url: '/user/audio/generate',
      method: 'POST',
      data
    })
  },

  /**
   * 用户端：分页查询当前用户音频任务列表
   */
  userTasks(data: AudioTaskPageQueryDTO) {
    return request<PageResult<AudioTaskVO>>({
      url: '/user/audio/list',
      method: 'POST',
      data
    })
  },

  /**
   * 用户端：查询音频任务状态
   */
  status(taskId: number | string) {
    return request<AudioTaskVO>({
      url: `/user/audio/status/${taskId}`,
      method: 'GET'
    })
  },

  /**
   * 用户端：重试失败的音频任务
   */
  retry(taskId: number | string) {
    return request<AudioTaskSubmitVO>({
      url: `/user/audio/retry/${taskId}`,
      method: 'POST'
    })
  },

  /**
   * 用户端：取消排队中或合成中的音频任务
   */
  userCancel(taskId: number | string) {
    return request<boolean>({
      url: `/user/audio/cancel/${taskId}`,
      method: 'POST'
    })
  },

  /**
   * 用户端：删除音频任务及其生成资源
   */
  userDelete(taskId: number | string) {
    return request<boolean>({
      url: `/user/audio/${taskId}`,
      method: 'DELETE'
    })
  }
}
