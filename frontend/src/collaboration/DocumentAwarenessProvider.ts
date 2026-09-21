import * as Y from 'yjs'
import type { Awareness } from 'y-protocols/awareness'

import type { DocumentAwarenessSessionMetadata } from './DocumentCollaborationClient'

/**
 * CollaborationCaret 只需要一个带 Awareness 的 provider。
 *
 * 这里故意只提供本地 Awareness 适配，不承担连接、重连或网络同步职责；
 * 网络事实源仍然是 DocumentCollaborationClient 当前使用的自定义 WebSocket。
 */
export interface DocumentAwarenessProviderSource {
  readonly awareness: Awareness
  getAwarenessSessions(): ReadonlyMap<number, DocumentAwarenessSessionMetadata>
  updateLocalAwareness(patch: Record<string, unknown>): void
}

export interface DocumentAwarenessProvider {
  readonly awareness: DocumentAwarenessProviderAdapter
}

/**
 * 将客户端的 Awareness 暴露为 Tiptap 扩展所需的最小 provider façade。
 */
export class DocumentAwarenessProviderAdapter {
  constructor(private readonly source: DocumentAwarenessProviderSource) {}

  get clientID(): number {
    return this.source.awareness.clientID
  }

  /**
   * CollaborationCaret 会优先读取 states，因此这里每次按服务端元数据生成
   * 视图，避免把未经服务端确认的二进制 user 身份暴露给渲染层。
   */
  get states(): Map<number, Record<string, unknown>> {
    return this.buildStates(true)
  }

  getStates(): Map<number, Record<string, unknown>> {
    // y-tiptap 的默认 awareness 过滤器使用 Y.Doc.clientID 排除本地状态。
    // 本客户端的 Awareness ID 是连接级的、刻意独立于 Y.Doc.clientID，因此
    // 给光标插件的 getStates 只暴露远端状态，避免把本地光标误渲染成协作者。
    return this.buildStates(false)
  }

  private buildStates(includeLocal: boolean): Map<number, Record<string, unknown>> {
    const states = new Map<number, Record<string, unknown>>()
    const sessions = this.source.getAwarenessSessions()

    for (const [clientId, state] of this.source.awareness.getStates()) {
      if (!isObjectRecord(state)) {
        continue
      }

      // 本地状态由 CollaborationCaret 自己维护，原样返回。
      if (clientId === this.source.awareness.clientID) {
        if (includeLocal) states.set(clientId, state)
        continue
      }

      const metadata = sessions.get(clientId)
      if (!metadata) {
        // 没有服务端元数据的状态不能安全确定身份，不渲染。
        continue
      }

      states.set(clientId, normalizeRemoteAwarenessState(state, metadata))
    }

    return states
  }

  getLocalState(): Record<string, unknown> | null {
    return this.source.awareness.getLocalState()
  }

  setLocalState(state: Record<string, unknown> | null): void {
    if (state === null) {
      this.source.awareness.setLocalState(null)
      return
    }

    this.source.updateLocalAwareness(state)
  }

  setLocalStateField(field: string, value: unknown): void {
    this.source.updateLocalAwareness({ [field]: value })
  }

  on(name: string, listener: (...args: any[]) => void): void {
    this.source.awareness.on(name, listener)
  }

  off(name: string, listener: (...args: any[]) => void): void {
    this.source.awareness.off(name, listener)
  }
}

export function createDocumentAwarenessProvider(
  source: DocumentAwarenessProviderSource,
): DocumentAwarenessProvider {
  return {
    awareness: new DocumentAwarenessProviderAdapter(source),
  }
}

/**
 * 只在渲染边界把 v0.5 规范及旧字段转换为 yCursorPlugin 所需的 anchor/head。
 * 不把转换后的字段写回 Awareness，避免线上同时维护两套协议形态。
 */
function normalizeRemoteAwarenessState(
  state: Record<string, unknown>,
  metadata: DocumentAwarenessSessionMetadata,
): Record<string, unknown> {
  const normalized: Record<string, unknown> = {
    ...state,
    user: {
      userId: metadata.userId,
      name: metadata.name,
      color: metadata.color,
      sessionId: metadata.sessionId,
      awarenessClientId: metadata.awarenessClientId,
    },
  }

  delete normalized.cursor
  delete normalized.selection

  const cursor = normalizeCursor(state)
  if (cursor) {
    normalized.cursor = cursor
  }

  return normalized
}

function normalizeCursor(
  state: Record<string, unknown>,
): { anchor: Record<string, unknown>; head: Record<string, unknown> } | null {
  const cursor = isObjectRecord(state.cursor) ? state.cursor : null
  const anchor = cursor?.anchor
  const head = cursor?.head

  // v0.5 正式字段：cursor.anchor/head。
  if (isRelativePosition(anchor) && isRelativePosition(head)) {
    return { anchor, head }
  }

  // 兼容旧的 selection.fromRelativePosition/toRelativePosition。
  const selection = isObjectRecord(state.selection) ? state.selection : null
  const from = selection?.fromRelativePosition
  const to = selection?.toRelativePosition
  if (isRelativePosition(from) && isRelativePosition(to)) {
    return { anchor: from, head: to }
  }

  // 兼容只有一个 cursor.relativePosition 的旧光标形态。
  const relativePosition = cursor?.relativePosition
  if (isRelativePosition(relativePosition)) {
    return { anchor: relativePosition, head: relativePosition }
  }

  return null
}

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/**
 * 先验证 RelativePosition 的 JSON 形状，再让 Yjs 做一次严格解析。
 * 无效位置直接跳过装饰，不能退化为普通 offset。
 */
function isRelativePosition(value: unknown): value is Record<string, unknown> {
  if (!isObjectRecord(value)) {
    return false
  }

  const type = value.type
  const item = value.item
  const tname = value.tname
  const assoc = value.assoc

  const validType = type == null || isYjsId(type)
  const validItem = item == null || isYjsId(item)
  const validTname = tname == null || typeof tname === 'string'
  const validAssoc = assoc == null || Number.isSafeInteger(assoc)
  const hasTarget =
    isYjsId(type) || (typeof tname === 'string' && tname.trim().length > 0)

  if (!validType || !validItem || !validTname || !validAssoc || !hasTarget) {
    return false
  }

  try {
    Y.createRelativePositionFromJSON(value)
    return true
  } catch {
    return false
  }
}

function isYjsId(value: unknown): value is { client: number; clock: number } {
  if (!isObjectRecord(value)) {
    return false
  }

  return (
    Number.isSafeInteger(value.client) &&
    (value.client as number) >= 0 &&
    Number.isSafeInteger(value.clock) &&
    (value.clock as number) >= 0
  )
}
