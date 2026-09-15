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
  /** 任务状态：0=排队中, 1=合成中, 2=已完成, -1=失败 */
  status: number
  resultUrl?: string
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
  }
}
