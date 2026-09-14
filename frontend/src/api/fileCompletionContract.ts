/** file 索引中可用于正文引用的资源类型。 */
export type FileResourceType = 'NOTE' | 'IMAGE' | 'DOCUMENT'

/** `/user/file/completion` 的最小响应字段；权限和删除过滤由后端完成。 */
export interface FileCompletionItem {
  fileName: string
  resourceType: FileResourceType
  resourceId: string
  resourceUrl: string | null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function normalizeResourceType(value: unknown): FileResourceType {
  if (value === 'NOTE' || value === 'IMAGE' || value === 'DOCUMENT') return value
  throw new Error('文件资源类型无效')
}

/** 严格归一化单条 file completion，避免异常资源 ID 进入 BIGINT 协议。 */
export function normalizeFileCompletionItem(value: unknown): FileCompletionItem {
  if (!isRecord(value)
      || typeof value.fileName !== 'string'
      || !value.fileName.trim()
      || typeof value.resourceId !== 'string'
      || !/^[1-9]\d*$/.test(value.resourceId)) {
    throw new Error('文件补全数据无效')
  }
  const resourceType = normalizeResourceType(value.resourceType)
  if (value.resourceUrl !== null && value.resourceUrl !== undefined && typeof value.resourceUrl !== 'string') {
    throw new Error('文件资源 URL 无效')
  }
  return {
    fileName: value.fileName,
    resourceType,
    resourceId: value.resourceId,
    // 后端只向 IMAGE 暴露 URL；前端再次收敛，避免旧服务返回值污染正文候选。
    resourceUrl: resourceType === 'IMAGE' && typeof value.resourceUrl === 'string'
      ? value.resourceUrl
      : null
  }
}

/** 严格归一化 file completion 列表；任一非法条目都会阻止绑定。 */
export function normalizeFileCompletionItems(value: unknown): FileCompletionItem[] {
  if (!Array.isArray(value)) throw new Error('文件补全列表无效')
  return value.map(normalizeFileCompletionItem)
}
