import request from '@/utils/request'
import {
  normalizeFileCompletionItems,
  type FileCompletionItem
} from './fileCompletionContract'

export { normalizeFileCompletionItem, normalizeFileCompletionItems } from './fileCompletionContract'
export type { FileCompletionItem, FileResourceType } from './fileCompletionContract'

export const fileApi = {
  /** 查询当前用户可见、未删除资源的文件名前缀候选。 */
  async complete(keyword: string, limit = 10): Promise<FileCompletionItem[]> {
    if (typeof keyword !== 'string') throw new Error('文件补全关键词无效')
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 20) {
      throw new Error('文件补全数量无效')
    }
    const value = await request.get<unknown>('/user/file/completion', {
      params: { keyword, limit },
      _silentErrorToast: true
    })
    return normalizeFileCompletionItems(value)
  }
}
