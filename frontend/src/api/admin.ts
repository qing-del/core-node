import request from '@/utils/request'
import type { NoteBacklinkVO, TagBacklinkVO, ImageBacklinkVO } from '@/api/notes'
export type { NoteBacklinkVO, TagBacklinkVO, ImageBacklinkVO } from '@/api/notes'

// ── Shared types ──────────────────────────────────
export interface PageResult<T> {
  total: number
  records: T[]
}

// ── Topics ────────────────────────────────────────
export interface AdminTopicItem {
  id: number
  userId?: number
  topicName: string
  parentId?: number | null
  sortOrder: number
  noteCount: number
  createTime: string
  updateTime: string
}

export interface TopicQueryParams {
  userId?: number
  keyword?: string
  sortBy?: string
  pageNum?: number
  pageSize?: number
}

// ── Tags ──────────────────────────────────────────
export interface AdminTagItem {
  id: number
  userId?: number
  tagName: string
  auditStatus: number
  createTime: string
}

export interface TagQueryParams {
  userId?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

// ── Images ────────────────────────────────────────
export interface AdminImageItem {
  id: number
  filename: string
  ossUrl: string
  fileSize: number
  userId: number
  isPublic: number
  auditStatus: number
  storageType: number
  uploadTime: string
  createTime: string
}

export interface ImageQueryParams {
  userId?: number
  topicId?: number
  filename?: string
  storageType?: number
  isPublic?: number
  auditStatus?: number
  pageNum?: number
  pageSize?: number
}

// ── Users ─────────────────────────────────────────
export interface AdminUserItem {
  id: number
  username: string
  nickname: string
  email: string
  roleId: number
  status: number
  maxStorageBytes: number
  usedStorageBytes: number
  noteCount?: number
  createTime: string
}

export interface UserQueryParams {
  id?: number
  username?: string
  status?: number
  roleId?: number
  pageNum?: number
  pageSize?: number
}

export interface UserCreateParams {
  username: string
  password: string
  roleId: number
  nickname?: string
  email?: string
  status?: number
  maxStorageBytes?: number
}

export interface UserModifyParams {
  id: number
  username?: string
  nickname?: string
  email?: string
  roleId?: number
  status?: number
  maxStorageBytes?: number
  newPassword?: string
  confirmPassword?: string
  password?: string
}

export interface UserStatusParams {
  id?: number
  targetUserId?: number
}

// ── Collaborative documents ──────────────────────
/** 管理员文档树中的文档叶子；不包含内部快照对象键。 */
export interface AdminDocumentTreeItem {
  documentId: number
  title: string
  /** Unix 毫秒时间戳 */
  lastModifyTime: number
  lastModifyUserId: number | null
  hasSnapshot: boolean
}

/** 以文档所有者为根节点的管理员协作文档树。 */
export interface AdminDocumentTreeNode {
  userId: number
  username: string | null
  nickname: string | null
  documents: AdminDocumentTreeItem[]
}

export interface AdminSnapshotHistoryClearItem {
  documentId: number
  deletedObjectCount: number
  releasedBytes: number
  success: boolean
  failureMessage: string | null
}

export interface AdminSnapshotHistoryClearResponse {
  documents: AdminSnapshotHistoryClearItem[]
  deletedObjectCount: number
  releasedBytes: number
}

// ── Audit types ──────────────────────────────────
export interface AuditNoteItem {
  /** Audit record ID */
  id: number
  /** Reviewed note ID */
  noteId: number
  noteTitle: string
  applicantUsername?: string
  title?: string
  applicantUserId: number
  nickname?: string
  status: number
  updateTime: string
}

export interface AuditMetaItem {
  /** Audit record ID */
  id: number
  applyType: number // 2: Tag
  targetId?: number
  targetName: string
  applicantUserId: number
  applicantUsername?: string
  nickname?: string
  status: number
  updateTime: string
}

export interface AuditImageItem {
  /** Audit record ID */
  id: number
  /** Reviewed image ID */
  imageId?: number
  filename: string
  ossUrl: string
  applicantUserId: number
  applicantUsername?: string
  nickname?: string
  status: number
  updateTime: string
}

export interface AuditQueryParams {
  status?: number
  applicantUserId?: number
  pageNum?: number
  pageSize?: number
}

export interface MetaAuditQueryParams extends AuditQueryParams {
  /** Kept for compatibility; backend treats meta audit as tag audit only. */
  applyType?: number
}

export interface AuditBatchReviewDTO {
  ids: number[]
  status: number
  rejectReason?: string
}

// ── API methods ───────────────────────────────────
export const adminApi = {
  // === Collaborative documents ===
  getDocumentTree(): Promise<AdminDocumentTreeNode[]> {
    return request.get('/admin/document')
  },
  clearDocumentSnapshotHistory(documentIds: number[]): Promise<AdminSnapshotHistoryClearResponse> {
    return request.post('/admin/document/snapshot-history/clear', { documentIds })
  },

  // === Audit ===
  getNoteAuditList(params: AuditQueryParams): Promise<PageResult<AuditNoteItem>> {
    return request.post('/admin/audit/note/list', params)
  },
  getMetaAuditList(params: MetaAuditQueryParams): Promise<PageResult<AuditMetaItem>> {
    return request.post('/admin/audit/meta/list', params)
  },
  getImageAuditList(params: AuditQueryParams): Promise<PageResult<AuditImageItem>> {
    return request.post('/admin/audit/image/list', params)
  },
  batchReviewNotes(data: AuditBatchReviewDTO): Promise<number> {
    return request.put('/admin/audit/note/review/batch', data)
  },
  batchReviewMetas(data: AuditBatchReviewDTO): Promise<number> {
    return request.put('/admin/audit/meta/review/batch', data)
  },
  batchReviewImages(data: AuditBatchReviewDTO): Promise<number> {
    return request.put('/admin/audit/image/review/batch', data)
  },

  // === Topics ===
  getTopicList(params: TopicQueryParams): Promise<PageResult<AdminTopicItem>> {
    return request.post('/admin/topic/list', params)
  },
  getTopicChildren(params: { userId: number; parentId?: number | null }): Promise<AdminTopicItem[]> {
    const query: Record<string, number> = { userId: params.userId }
    if (params.parentId != null) query.parentId = params.parentId
    return request.get('/admin/topic/children', { params: query })
  },
  deleteTopics(ids: number[]): Promise<string> {
    return request.delete('/admin/topic/delete', {
      params: { ids: ids.join(',') }
    })
  },

  // === Tags ===
  getTagList(params: TagQueryParams): Promise<PageResult<AdminTagItem>> {
    return request.post('/admin/tag/list', params)
  },
  deleteTags(ids: number[]): Promise<string> {
    return request.delete('/admin/tag/delete', {
      params: { ids: ids.join(',') }
    })
  },
  modifyTag(data: { id: number; tagName: string }): Promise<string> {
    return request.put('/admin/tag/modify', data)
  },

  // === Images ===
  getImageList(params: ImageQueryParams): Promise<PageResult<AdminImageItem>> {
    return request.post('/admin/image/list', params)
  },
  deleteImages(ids: number[]): Promise<string> {
    return request.delete('/admin/image/delete', {
      params: { ids: ids.join(',') }
    })
  },
  modifyImageInfo(data: { id: number; topicId?: number }): Promise<string> {
    return request.put('/admin/image/modify-info', data)
  },
  setImagePublic(data: { id: number }, isPublic: number): Promise<string> {
    return request.post(`/admin/image/public/${isPublic}`, data)
  },
  transferToCloud(ids: number[]): Promise<string> {
    return request.put('/admin/image/transfer-to-cloud', null, {
      params: { ids: ids.join(',') }
    })
  },

  // === Users ===
  getUserList(params: UserQueryParams): Promise<PageResult<AdminUserItem>> {
    return request.post('/admin/user/list', params)
  },
  getUserDetail(id: number): Promise<AdminUserItem> {
    return request.get('/admin/user/user', { params: { id } })
  },
  createUser(data: UserCreateParams): Promise<string> {
    return request.post('/admin/user/user', data)
  },
  modifyUser(data: UserModifyParams): Promise<string> {
    return request.put('/admin/user/user', data)
  },
  deleteUsers(ids: number[]): Promise<string> {
    return request.delete('/admin/user/user', { params: { ids: ids.join(',') } })
  },
  setUserStatus(status: number, data: UserStatusParams): Promise<string> {
    return request.post(`/admin/user/status/${status}`, data)
  },

  // === Notes ===
  getNoteList(params: AdminNoteQueryParams): Promise<PageResult<AdminNoteItem>> {
    return request.post('/admin/note/list', params)
  },
  /** 查询笔记详情（含关联和转换内容） */
  getNoteInfo(noteId: number): Promise<AdminNoteItem> {
    return request.get(`/admin/note/info/${noteId}`)
  },
  /** 获取笔记 Markdown 源内容 */
  getNoteSource(noteId: number): Promise<string> {
    return request.get(`/admin/note/source/${noteId}`)
  },
  /** 查看笔记已转换的 HTML（管理端打开任意笔记） */
  openNoteHtml(noteId: number): Promise<{ meta: { title: string; tags: string[]; createTime: string }; tocHtml: string; bodyHtml: string }> {
    return request.get(`/admin/note/open/${noteId}`)
  },
  /** 转换笔记为 HTML */
  convertNote(noteId: number): Promise<Record<string, never>> {
    return request.post(`/admin/note/convert/${noteId}`)
  },
  /** 删除笔记转换缓存 */
  deleteNoteConvert(noteId: number): Promise<string> {
    return request.delete(`/admin/note/convert/${noteId}`)
  },
  /** 修改笔记元信息 */
  modifyNoteInfo(data: { id: number; topicId?: number; description?: string }): Promise<string> {
    return request.put('/admin/note/info', data)
  },
  /** 批量删除笔记 */
  deleteNotes(ids: number[]): Promise<string> {
    return request.delete('/admin/note/delete', { params: { ids: ids.join(',') } })
  },
  /** 查询反向引用笔记 (Admin) — 哪些笔记引用了 noteId */
  getNoteBacklinks(noteId: number): Promise<NoteBacklinkVO[]> {
    return request.get(`/admin/note/relation/backlinks/${noteId}`)
  },

  /** 查询标签反向引用笔记 (Admin) */
  getTagBacklinks(tagId: number): Promise<TagBacklinkVO[]> {
    return request.get(`/admin/note/relation/backlinks/tag/${tagId}`)
  },

  /** 查询图片反向引用笔记 (Admin) */
  getImageBacklinks(imageId: number): Promise<ImageBacklinkVO[]> {
    return request.get(`/admin/note/relation/backlinks/image/${imageId}`)
  }
}

// ── Admin Note types ────────────────────────────
export interface AdminNoteItem {
  id: number
  userId: number
  topicId: number | null
  topicName: string | null
  title: string
  description: string
  storageType: number
  /** NoteStatus code */
  status: number
  missingInfoMask: number
  missingCount: number
  mdFileSize: number
  createTime: string
  updateTime: string
  tags?: string[]
  images?: AdminNoteImageVO[]
  eachNotes?: AdminNoteEachVO[]
  converted?: { meta: { title: string; tags: string[]; createTime: string }; tocHtml: string; bodyHtml: string }
}

export interface AdminNoteImageVO {
  imageId: number; noteId: number; parsedImageName: string; filename: string
  ossUrl: string; isPublic: number; status: number; isCrossUser: number; isMissing: number; createTime: string
}

export interface AdminNoteEachVO {
  targetNoteId: number; targetNoteTitle: string; parsedNoteName: string
  anchor: string; nickname: string; isMissing: number
}

export interface AdminNoteQueryParams {
  userId?: number
  topicId?: number | null
  unclassified?: boolean
  title?: string
  status?: number
  pageNum?: number
  pageSize?: number
}
