export type ShareLinkPermission = 'READ' | 'WRITE'

export const MAX_DOCUMENT_SHARE_LINK_VALID_FOR_SECONDS = 31536000
export const MAX_DOCUMENT_SHARE_LINK_USES = 100000

export interface ShareLink {
  shareLinkId: number
  documentId: number
  permission: ShareLinkPermission
  shareUrl: string | null
  expiresAt: string
  maxUses: number
  usedCount: number
  enabled: boolean
  revokedAt: string | null
  createTime: string
  updateTime: string
}

export interface ShareLinkRedeemResponse {
  documentId: number
  permission: ShareLinkPermission
  owner: boolean
}

export interface ShareLinkCreateInput {
  permission: ShareLinkPermission
  validForSeconds: number
  maxUses: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function assertPositiveId(value: unknown, label: string): asserts value is number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0) {
    throw new Error(`${label}无效`)
  }
}

function assertSharePermission(value: unknown): asserts value is ShareLinkPermission {
  if (value !== 'READ' && value !== 'WRITE') throw new Error('分享权限无效')
}

function normalizeDateTime(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim() || !Number.isFinite(Date.parse(value))) {
    throw new Error(`${label}无效`)
  }
  return value
}

function normalizeOptionalDateTime(value: unknown, label: string): string | null {
  // Jackson NON_NULL omits optional null fields such as revokedAt.
  if (value === null || value === undefined) return null
  return normalizeDateTime(value, label)
}

function normalizeShareUrl(value: unknown, required: boolean): string | null {
  // Historical list responses intentionally omit shareUrl under NON_NULL.
  if (value === null || value === undefined) {
    if (required) throw new Error('分享链接无效')
    return null
  }
  if (typeof value !== 'string' || !value.trim()) throw new Error('分享链接无效')

  try {
    const url = new URL(value)
    if ((url.protocol !== 'http:' && url.protocol !== 'https:')
        || url.search || url.hash || !/^\/s\/[A-Za-z0-9_-]{43}$/.test(url.pathname)) {
      throw new Error('分享链接无效')
    }
  } catch {
    throw new Error('分享链接无效')
  }
  return value
}

/** 严格校验短链记录；任何关键字段异常都会阻止页面继续处理。 */
export function normalizeShareLink(value: unknown, options?: { requireShareUrl?: boolean }): ShareLink {
  if (!isRecord(value)) throw new Error('文档分享短链数据无效')

  const shareLinkId = value.shareLinkId
  const documentId = value.documentId
  const maxUses = value.maxUses
  const usedCount = value.usedCount
  assertPositiveId(shareLinkId, '分享短链 ID')
  assertPositiveId(documentId, '文档 ID')
  assertSharePermission(value.permission)
  if (typeof maxUses !== 'number' || !Number.isSafeInteger(maxUses) || maxUses <= 0
      || maxUses > MAX_DOCUMENT_SHARE_LINK_USES
      || typeof usedCount !== 'number' || !Number.isSafeInteger(usedCount)
      || usedCount < 0 || usedCount > maxUses
      || typeof value.enabled !== 'boolean') {
    throw new Error('文档分享短链数据无效')
  }

  return {
    shareLinkId,
    documentId,
    permission: value.permission,
    shareUrl: normalizeShareUrl(value.shareUrl, options?.requireShareUrl === true),
    expiresAt: normalizeDateTime(value.expiresAt, '分享短链有效期'),
    maxUses,
    usedCount,
    enabled: value.enabled,
    revokedAt: normalizeOptionalDateTime(value.revokedAt, '分享短链撤销时间'),
    createTime: normalizeDateTime(value.createTime, '分享短链创建时间'),
    updateTime: normalizeDateTime(value.updateTime, '分享短链更新时间')
  }
}

/** 校验列表响应，任一非法记录都按失败处理。 */
export function normalizeShareLinks(value: unknown): ShareLink[] {
  if (!Array.isArray(value)) throw new Error('文档分享短链列表无效')
  return value.map(item => normalizeShareLink(item))
}

/** 严格校验兑换响应，不能由前端猜测文档 ID 或权限。 */
export function normalizeShareLinkRedeemResponse(value: unknown): ShareLinkRedeemResponse {
  if (!isRecord(value)) throw new Error('分享链接兑换响应无效')
  assertPositiveId(value.documentId, '文档 ID')
  assertSharePermission(value.permission)
  if (typeof value.owner !== 'boolean') throw new Error('分享链接兑换响应无效')
  return {
    documentId: value.documentId,
    permission: value.permission,
    owner: value.owner
  }
}

/** 校验创建参数；客户端不能依赖服务端对非法值做隐式修正。 */
export function validateShareLinkCreateInput(data: ShareLinkCreateInput): void {
  assertSharePermission(data.permission)
  if (typeof data.validForSeconds !== 'number' || !Number.isSafeInteger(data.validForSeconds)
      || data.validForSeconds <= 0 || data.validForSeconds > MAX_DOCUMENT_SHARE_LINK_VALID_FOR_SECONDS) {
    throw new Error('分享短链有效时长无效')
  }
  if (typeof data.maxUses !== 'number' || !Number.isSafeInteger(data.maxUses)
      || data.maxUses <= 0 || data.maxUses > MAX_DOCUMENT_SHARE_LINK_USES) {
    throw new Error('分享短链最大兑换次数无效')
  }
}

