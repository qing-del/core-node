import type {
  AdminDocumentTreeNode,
  AdminSnapshotHistoryClearResponse
} from '../api/admin.ts'

/** 按所有者或文档字段过滤服务端返回的目录树，保持原始顺序和层级。 */
export function filterAdminDocumentTree(
  nodes: readonly AdminDocumentTreeNode[],
  query: string
): AdminDocumentTreeNode[] {
  const keyword = query.trim().toLocaleLowerCase()
  if (!keyword) return [...nodes]

  return nodes.flatMap((node) => {
    const ownerMatches = [node.userId, node.username, node.nickname]
      .some(value => String(value ?? '').toLocaleLowerCase().includes(keyword))
    const documents = ownerMatches
      ? node.documents
      : node.documents.filter(document => [document.documentId, document.title]
        .some(value => String(value).toLocaleLowerCase().includes(keyword)))
    return documents.length > 0 ? [{ ...node, documents }] : []
  })
}

/** 搜索期间应展开所有命中所有者；空搜索不会主动展开目录。 */
export function expandedOwnerIdsForSearch(
  nodes: readonly AdminDocumentTreeNode[],
  query: string
): number[] {
  return query.trim() ? filterAdminDocumentTree(nodes, query).map(node => node.userId) : []
}

/** 合并多组选中项，忽略无效值并保证请求 ID 的稳定去重顺序。 */
export function uniqueDocumentIds(ids: Iterable<number>): number[] {
  const unique = new Set<number>()
  for (const id of ids) {
    if (Number.isSafeInteger(id) && id > 0) unique.add(id)
  }
  return [...unique]
}

/** 生成可用于结果弹窗的准确汇总，零删除不是失败。 */
export function buildSnapshotCleanupSummary(result: AdminSnapshotHistoryClearResponse): string {
  const failed = result.documents.filter(item => !item.success)
  const prefix = `已删除 ${result.deletedObjectCount} 个历史快照，释放 ${formatBytes(result.releasedBytes)}。`
  if (failed.length === 0) return `${prefix} ${result.documents.length} 篇文档均已完成清理。`

  const failures = failed
    .map(item => `文档 #${item.documentId}：${item.failureMessage || '清理失败，可重试'}`)
    .join('\n')
  return `${prefix}\n${failed.length} 篇文档未完整清理：\n${failures}`
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`
}
