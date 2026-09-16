import * as Y from 'yjs'
import {
  Awareness,
  applyAwarenessUpdate,
  encodeAwarenessUpdate,
  removeAwarenessStates
} from 'y-protocols/awareness'
import {
  DocumentWsFrameType,
  createDocumentWsControl,
  createDocumentWsRequestId,
  decodeDocumentWsFrame,
  encodeDocumentLinkPayload,
  encodeDocumentWsFrame,
  isDocumentLinkIntent,
  parseDocumentWsAwarenessMeta,
  parseDocumentWsControl,
  parseDocumentWsLinkAccepted,
  type DocumentBindingEnvelope,
  type DocumentLinkIntent,
  type DocumentWsAwarenessMeta,
  type DocumentWsControlMessage,
  type DocumentWsLinkAcceptedMessage
} from './documentProtocol.ts'
import {
  createDocumentAwarenessProvider,
  type DocumentAwarenessProvider
} from './DocumentAwarenessProvider.ts'

export type DocumentConnectionState = 'connecting' | 'synchronizing' | 'synced' | 'reconnecting' | 'closed' | 'error'

/** 文档 WebSocket 错误的结构化信息；页面层据此区分资源拒绝和普通协议错误。 */
export interface DocumentCollaborationError {
  /** 服务端机器可判断的错误编码；example: {@code 'DOCUMENT_FORBIDDEN'} */
  code: string | null
  /** 面向用户的错误说明；example: {@code '文档不存在或无权访问'} */
  message: string | null
  /** 与控制帧关联的请求 ID；example: {@code '550e8400-e29b-41d4-a716-446655440000'} */
  requestId: string | null
  /** 服务端关联的文档 ID；全局错误时为空。 */
  documentId: number | null
}

/** 自动重连前刷新元数据的结果；网络错误可重试，权限错误必须终止重连。 */
export type DocumentReconnectAccessResult =
  | { status: 'ready'; canWrite: boolean }
  | { status: 'denied'; code: string; message?: string }
  | { status: 'retry' }

/** 服务端下发的协作者 Session 身份；展示时不能改用 Awareness payload 内的 user。 */
export interface DocumentAwarenessSessionMetadata {
  /** Yjs Awareness 状态索引；与当前 JOIN 的 awarenessClientId 一致。 */
  awarenessClientId: number
  /** 服务端 WebSocket Session ID；同一用户的多个窗口各不相同。 */
  sessionId: string
  /** 认证用户 ID。 */
  userId: number
  /** 认证主体的展示名称。 */
  name: string
  /** 服务端按活跃 Session 分配的颜色。 */
  color: string
}

/** Session 元数据的生命周期事件，供页面和后续光标渲染订阅。 */
export type DocumentAwarenessSessionEvent =
  | { action: 'UPSERT'; metadata: DocumentAwarenessSessionMetadata }
  | { action: 'REMOVE'; awarenessClientId: number; sessionId: string }

/** 协作 Agent 对外公布的有限状态；不包含提示词、思考过程或模型输出。 */
export type DocumentAgentStatus = 'reading' | 'thinking' | 'editing' | 'idle'

/** Yjs Relative Position JSON 中的客户端时钟标识。 */
export interface DocumentAwarenessRelativePositionId {
  client: number
  clock: number
}

/**
 * Awareness 中可传输的 Yjs Relative Position 结构。
 *
 * 该类型与 Y.relativePositionToJSON 的结果兼容，也能接收 Y.RelativePosition
 * 实例的结构；位置始终保持相对语义，不允许退化成普通文本 offset。
 */
export interface DocumentAwarenessRelativePosition {
  type?: DocumentAwarenessRelativePositionId | null
  tname?: string | null
  item?: DocumentAwarenessRelativePositionId | null
  assoc?: number
}

/** Agent 当前正在读取或编辑的文档相对位置范围。 */
export interface DocumentAgentWorkingRange {
  from: DocumentAwarenessRelativePosition
  to: DocumentAwarenessRelativePosition
}

/** Agent Awareness 的完整状态；没有活动操作时可使用 idle 和空范围。 */
export interface DocumentAgentAwarenessState {
  operationId: string | null
  status: DocumentAgentStatus
  workingRange: DocumentAgentWorkingRange | null
}

/** Agent Awareness 的字段级更新；未提供的字段保持已有值。 */
export type DocumentAgentAwarenessPatch = Partial<DocumentAgentAwarenessState>

export interface DocumentCollaborationClientOptions {
  /** 要加入协作 Room 的文档 ID；example: {@code 42} */
  documentId: number
  /** 用于 WebSocket bearer 子协议的访问令牌；example: {@code 'eyJhbGciOi...'} */
  accessToken: string
  /** 承载文档正文和 Yjs 状态的本地文档对象；example: {@code new Y.Doc()} */
  ydoc: Y.Doc
  /** 当前调用方是否拥有文档正文写权限；缺省策略由页面层显式传入。 */
  canWrite: boolean
  /** 返回当前本地编辑器是否仍处于 IME composition。 */
  isComposing?: () => boolean
  /** 连接状态变化回调；example: {@code (state) => console.log(state)} */
  onStateChange?: (state: DocumentConnectionState, message?: string) => void
  /** 在线协作者数量变化回调；example: {@code (count) => collaboratorCount.value = count} */
  onAwarenessChange?: (count: number) => void
  /** 服务端 Session 元数据变化回调；第二个参数是不可修改的当前快照。 */
  onAwarenessSessionChange?: (
    event: DocumentAwarenessSessionEvent,
    sessions: ReadonlyMap<number, DocumentAwarenessSessionMetadata>
  ) => void
  /** LINK 已原子进入 Redis 双 Stream 后的确认回调；不代表 MySQL 投影已完成。 */
  onLinkAccepted?: (message: DocumentWsLinkAcceptedMessage) => void
  /** 收到文档拒绝或不存在错误时通知页面层。 */
  onAccessError?: (error: DocumentCollaborationError) => void
  /** 自动重连前重新读取文档元数据，刷新当前页面的资源级权限。 */
  onBeforeReconnect?: () => Promise<DocumentReconnectAccessResult>
}

type PendingUpdate =
  | {
      /** 客户端为普通更新生成的关联 UUID。 */
      id: string
      /** 重连时必须保留的原始 frame type。 */
      frameType: DocumentWsFrameType.CLIENT_UPDATE
      /** 尚未收到 UPDATE_ACCEPTED 的 raw Yjs update。 */
      payload: Uint8Array
    }
  | {
      /** LINK 外层 eventId，同时也是服务端 ACK 的 clientUpdateId。 */
      id: string
      /** 重连时必须保留的原始 frame type。 */
      frameType: DocumentWsFrameType.LINK
      /** 尚未收到 LINK_ACCEPTED 的 raw Yjs update。 */
      payload: Uint8Array
      /** 与 raw Yjs 同一 transaction 关联的固定 Envelope。 */
      envelope: DocumentBindingEnvelope
    }

interface PendingBootstrapFrame {
  /** Bootstrap 帧类型；当前只允许 SNAPSHOT_STATE 或 BOOTSTRAP_UPDATE。 */
  type: DocumentWsFrameType.SNAPSHOT_STATE | DocumentWsFrameType.BOOTSTRAP_UPDATE
  /** 从服务端收到的不可变 Yjs 二进制负载。 */
  payload: Uint8Array
}

interface InitialSyncWaiter {
  resolve: () => void
  reject: (reason?: unknown) => void
}

/** 标记服务端下发的 Yjs 更新，避免写回客户端更新队列。 */
const REMOTE_UPDATE_ORIGIN = Symbol('document-remote-update')
/** 标记服务端下发的 awareness 更新，避免再次广播给服务端。 */
const REMOTE_AWARENESS_ORIGIN = Symbol('document-remote-awareness')
/** 标记本地 awareness 清理动作，避免将离线状态当作普通变更发送。 */
const LOCAL_AWARENESS_ORIGIN = Symbol('document-local-awareness')
/** 重连退避等待时间上限；示例：`10000` 毫秒。 */
const MAX_RECONNECT_DELAY_MS = 10_000
/** 本地 Awareness 更新的最小发送间隔；示例：`50` 毫秒。 */
const AWARENESS_THROTTLE_MS = 50
/** 允许按字段合并的 Awareness 顶层对象，避免更新一个分支覆盖其他协同状态。 */
const AWARENESS_OBJECT_FIELDS = new Set(['user', 'cursor', 'selection', 'agent'])

/**
 * 将项目的文档 WebSocket 协议连接到一个 Y.Doc。
 *
 * 此处刻意不使用 y-websocket、Hocuspocus 或托管 Tiptap 服务：Spring 端点仍是唯一的网络事实来源。
 */
export class DocumentCollaborationClient {
  /** 当前 Yjs 文档对应的 awareness 状态集合。 */
  readonly awareness: Awareness
  /** 适配 Tiptap CollaborationCaret 的本地 provider；不负责网络连接。 */
  private readonly awarenessProvider: DocumentAwarenessProvider

  /** 要加入协作 Room 的文档 ID；example: {@code 42} */
  private readonly documentId: number
  /** 用于 WebSocket bearer 子协议的访问令牌。 */
  private readonly accessToken: string
  /** 承载文档正文、快照和本地更新的 Yjs 文档。 */
  private readonly ydoc: Y.Doc
  /** 连接状态变化回调，由页面层更新同步提示。 */
  private readonly onStateChange?: (state: DocumentConnectionState, message?: string) => void
  /** awareness 在线人数变化回调，由页面层更新协作者数量。 */
  private readonly onAwarenessChange?: (count: number) => void
  /** 服务端 Session 元数据变化回调，由后续光标/选区渲染层消费。 */
  private readonly onAwarenessSessionChange?: (
    event: DocumentAwarenessSessionEvent,
    sessions: ReadonlyMap<number, DocumentAwarenessSessionMetadata>
  ) => void
  /** LINK 已原子进入 Redis 双 Stream 后的确认回调；不代表 MySQL 投影已完成。 */
  private readonly onLinkAccepted?: (message: DocumentWsLinkAcceptedMessage) => void
  /** 文档资源拒绝回调，由页面层切换到不可用状态。 */
  private readonly onAccessError?: (error: DocumentCollaborationError) => void
  /** 自动重连前的权限刷新回调。 */
  private readonly onBeforeReconnect?: () => Promise<DocumentReconnectAccessResult>
  /** 当前会话是否允许产生和发送正文更新；Awareness 不受此状态影响。 */
  private writable: boolean
  /** 当前本地编辑器是否处于 IME composition；未提供时视为普通输入。 */
  private readonly isComposing?: () => boolean
  /** 尚未收到 UPDATE_ACCEPTED 的本地更新，按客户端更新 UUID 索引。 */
  private readonly pendingUpdates = new Map<string, PendingUpdate>()
  /** composition 期间暂存的 raw Yjs 更新；提交时合并为一个普通客户端更新。 */
  private readonly pendingCompositionUpdates: Uint8Array[] = []
  /** 当前同步尝试尚未完成最终构建的 Snapshot/Bootstrap 帧。 */
  private readonly pendingBootstrapFrames: PendingBootstrapFrame[] = []
  /** 当前同步尝试期间收到的远端 CRDT_UPDATE，不能在 Bootstrap 完成前直接应用。 */
  private readonly pendingRemoteUpdates: Uint8Array[] = []
  /** 当前 WebSocket 实例；未连接或连接已关闭时为 `null`。 */
  private socket: WebSocket | null = null
  /** 当前唯一的重连定时器；没有待重连任务时为 `null`。 */
  private reconnectTimer: ReturnType<typeof window.setTimeout> | null = null
  /** 高频本地 Awareness 更新的 trailing 定时器。 */
  private awarenessSendTimer: ReturnType<typeof window.setTimeout> | null = null
  /** compositionend 后等待 ProseMirror 完成最终 DOM flush 的定时器。 */
  private compositionFlushTimer: ReturnType<typeof window.setTimeout> | null = null
  /** 最近一次成功发送本地 Awareness 的时间戳。 */
  private awarenessLastSentAt: number | null = null
  /** 已连续发起的重连次数，用于计算指数退避时长。 */
  private reconnectAttempts = 0
  /** 客户端是否已经释放，不再允许创建连接或处理事件。 */
  private disposed = false
  /** 服务端是否已经发送 SYNC_COMPLETE，可以发送本地更新。 */
  private synchronized = false
  /** 是否已经完成本客户端生命周期内的首轮 bootstrap。 */
  private initialSyncCompleted = false
  /** 等待首轮 bootstrap 的页面初始化调用方。 */
  private readonly initialSyncWaiters: InitialSyncWaiter[] = []
  /** 当前对外公布的连接状态；初始值为 `closed`。 */
  private currentState: DocumentConnectionState = 'closed'
  /** 服务端确认的 Session 元数据；key 是 Yjs Awareness client ID。 */
  private readonly awarenessSessions = new Map<number, DocumentAwarenessSessionMetadata>()

  /** 创建 Y.Doc 与 WebSocket/awareness 事件之间的桥接客户端。 */
  constructor(options: DocumentCollaborationClientOptions) {
    this.documentId = options.documentId
    this.accessToken = options.accessToken
    this.ydoc = options.ydoc
    this.onStateChange = options.onStateChange
    this.onAwarenessChange = options.onAwarenessChange
    this.onAwarenessSessionChange = options.onAwarenessSessionChange
    this.onLinkAccepted = options.onLinkAccepted
    this.onAccessError = options.onAccessError
    this.onBeforeReconnect = options.onBeforeReconnect
    this.writable = options.canWrite
    this.isComposing = options.isComposing
    this.awareness = new Awareness(this.ydoc)
    this.awarenessProvider = createDocumentAwarenessProvider({
      awareness: this.awareness,
      getAwarenessSessions: () => this.getAwarenessSessions(),
      updateLocalAwareness: patch => this.updateLocalAwareness(patch)
    })
    this.ydoc.on('update', this.handleDocumentUpdate)
    this.awareness.on('update', this.handleAwarenessUpdate)
    this.awareness.on('change', this.handleAwarenessChange)
  }

  /** 刷新正文写权限；降权时保留未确认更新并暂停发送，恢复后继续重放。 */
  setWriteEnabled(enabled: boolean): void {
    const wasWritable = this.writable
    this.writable = enabled
    if (!wasWritable && enabled && this.synchronized) {
      this.pendingUpdates.forEach(update => this.sendPendingUpdate(update))
    }
  }

  /** 等待首轮 bootstrap 应用完成；普通断线会继续等待后续重连。 */
  waitForInitialSync(): Promise<void> {
    if (this.initialSyncCompleted) return Promise.resolve()
    if (this.disposed) return Promise.reject(new Error('文档协作客户端已释放'))
    if (this.currentState === 'error') return Promise.reject(new Error('文档同步失败'))
    return new Promise<void>((resolve, reject) => {
      this.initialSyncWaiters.push({ resolve, reject })
    })
  }

  /** compositionend 后延迟刷新，给 ProseMirror 留出完成最终 DOM 事务的时间。 */
  notifyCompositionEnd(): void {
    if (this.disposed) return
    this.clearCompositionFlushTimer()
    this.compositionFlushTimer = window.setTimeout(() => {
      this.compositionFlushTimer = null
      if (this.isLocalCompositionActive()) return
      this.flushPendingCompositionUpdates()
    }, 0)
  }

  /** 建立 bearer 子协议连接并发送 JOIN；已连接或已销毁时保持幂等。 */
  connect(): void {
    if (this.disposed || this.socket) return
    this.setState(this.reconnectAttempts > 0 ? 'reconnecting' : 'connecting')
    // Y.Doc 的 clientID 只负责正文 CRDT；每个 WebSocket 连接使用独立的
    // Awareness ID，避免旧连接尚未清理时与新连接发生身份冲突。
    this.rotateAwarenessClientId()

    /** 本次连接尝试创建的 WebSocket；回调通过它判断事件是否属于当前连接。 */
    const socket = new WebSocket(documentWebSocketUrl(), [`bearer.${this.accessToken}`])
    socket.binaryType = 'arraybuffer'
    socket.onopen = () => {
      if (this.socket !== socket || this.disposed) return
      this.reconnectAttempts = 0
      this.synchronized = false
      // 每次 JOIN 都重新收集 Bootstrap；本地 pendingUpdates 需要跨重连保留，
      // 而旧连接尚未完成的接收缓存由新一轮 Bootstrap 重新覆盖。
      this.pendingBootstrapFrames.length = 0
      this.pendingRemoteUpdates.length = 0
      this.setState('synchronizing')
      this.sendControl(createDocumentWsControl('JOIN_DOCUMENT', {
        requestId: createDocumentWsRequestId(),
        documentId: this.documentId,
        awarenessClientId: this.awareness.clientID
      }))
    }
    socket.onmessage = event => this.handleSocketMessage(socket, event)
    socket.onerror = () => {
      // 下方 close 回调会承担重试职责，同时保留浏览器提供的原始错误细节。
    }
    socket.onclose = () => this.handleSocketClosed(socket)
    this.socket = socket
  }

  /** 按顶层字段合并本地 Awareness；网络发送由 Awareness update 监听统一处理。 */
  updateLocalAwareness(patch: Record<string, unknown>): void {
    const current = this.awareness.getLocalState() as Record<string, unknown> | null
    this.awareness.setLocalState(mergeAwarenessState(current, patch))
  }

  /**
   * 按字段更新本地 Agent Awareness。
   *
   * Agent 只同步 operationId、有限 status 和相对位置范围；默认状态保证
   * 第一次只更新一个字段时，agent 分支仍然保持完整且可被后续消费者读取。
   */
  updateLocalAgentAwareness(patch: DocumentAgentAwarenessPatch): void {
    const current = this.awareness.getLocalState() as Record<string, unknown> | null
    const currentAgent = isObjectRecord(current?.agent)
      ? current.agent as Partial<DocumentAgentAwarenessState>
      : {}
    const agent: DocumentAgentAwarenessState = {
      operationId: currentAgent.operationId ?? null,
      status: currentAgent.status ?? 'idle',
      workingRange: currentAgent.workingRange ?? null
    }

    // 逐字段判断 undefined，避免部分更新把已有 Agent 字段覆盖成空值。
    if (patch.operationId !== undefined) agent.operationId = patch.operationId
    if (patch.status !== undefined) agent.status = patch.status
    if (patch.workingRange !== undefined) agent.workingRange = patch.workingRange

    this.updateLocalAwareness({ agent })
  }

  /** 清除整个本地 Agent Awareness 分支；其他用户和光标字段继续保留。 */
  clearLocalAgentAwareness(): void {
    this.updateLocalAwareness({ agent: null })
  }

  /** 保留旧入口，兼容既有页面调用；实际行为已经改为字段级合并。 */
  setLocalAwareness(patch: Record<string, unknown>): void {
    this.updateLocalAwareness(patch)
  }

  /** 返回服务端 Session 元数据的只读副本，避免调用方修改内部生命周期索引。 */
  getAwarenessSessions(): ReadonlyMap<number, DocumentAwarenessSessionMetadata> {
    return new Map(this.awarenessSessions)
  }

  /** 返回只承担 Awareness 适配的本地 façade，网络生命周期仍由当前客户端负责。 */
  getAwarenessProvider(): DocumentAwarenessProvider {
    return this.awarenessProvider
  }

  /** 停止重连、通知服务端离开并解除 Yjs/awareness 监听。 */
  dispose(): void {
    if (this.disposed) return
    this.disposed = true
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.clearAwarenessSendTimer()
    this.clearCompositionFlushTimer()
    this.rejectInitialSync(new Error('文档协作客户端已释放'))
    this.sendControl(createDocumentWsControl('LEAVE_DOCUMENT', {
      requestId: createDocumentWsRequestId(),
      documentId: this.documentId
    }))
    this.socket?.close(1000, 'document editor disposed')
    this.socket = null
    this.ydoc.off('update', this.handleDocumentUpdate)
    this.awareness.off('update', this.handleAwarenessUpdate)
    this.awareness.off('change', this.handleAwarenessChange)
    this.pendingBootstrapFrames.length = 0
    this.pendingRemoteUpdates.length = 0
    this.pendingCompositionUpdates.length = 0
    this.clearAwarenessSessions(false)
    removeAwarenessStates(this.awareness, [this.awareness.clientID], LOCAL_AWARENESS_ORIGIN)
    this.awareness.destroy()
    this.setState('closed')
  }

  /** 将本地 Yjs 更新放入待确认队列，直到服务端返回对应 ACK。 */
  private readonly handleDocumentUpdate = (update: Uint8Array, origin: unknown): void => {
    if (origin === REMOTE_UPDATE_ORIGIN || this.disposed || !this.writable) return
    const linkIntent = isDocumentLinkIntent(origin) ? origin : null
    // LINK 的 Envelope 只描述这一笔绑定动作，不能把 composition 或其他正文更新
    // 合并进同一个 LINK frame；普通更新则可以和此前缓存的 composition 一起发送。
    if (linkIntent) {
      this.flushPendingCompositionUpdates()
      this.enqueuePendingUpdate(update, linkIntent)
      return
    }

    if (this.isLocalCompositionActive()) {
      // composition 中间状态只留在当前 Y.Doc；最终事务到达后会和它们一起合并。
      this.pendingCompositionUpdates.push(update.slice())
      return
    }

    const payload = this.takeCompositionUpdate(update)
    if (!payload) return
    this.enqueuePendingUpdate(payload, null)
  }

  /** 判断当前本地 Yjs 更新是否来自仍在组字的编辑器。 */
  private isLocalCompositionActive(): boolean {
    return this.isComposing?.() === true
  }

  /** 取出 composition 缓存，并可将最终普通更新一并合并。 */
  private takeCompositionUpdate(additional?: Uint8Array): Uint8Array | null {
    if (this.pendingCompositionUpdates.length === 0) return additional?.slice() ?? null
    const updates = this.pendingCompositionUpdates.splice(0)
    if (additional) updates.push(additional.slice())
    return Y.mergeUpdates(updates)
  }

  /** 把已经结束的 composition 缓存作为普通 CLIENT_UPDATE 放入现有确认队列。 */
  private flushPendingCompositionUpdates(): void {
    const payload = this.takeCompositionUpdate()
    if (payload) this.enqueuePendingUpdate(payload, null)
  }

  /** 为一笔普通更新或 LINK 更新创建唯一 ACK 记录，并按当前同步状态发送。 */
  private enqueuePendingUpdate(update: Uint8Array, linkIntent: DocumentLinkIntent | null): void {
    const id = createDocumentWsRequestId()
    const pending: PendingUpdate = linkIntent
      ? {
          id,
          frameType: DocumentWsFrameType.LINK,
          payload: update.slice(),
          envelope: {
            schemaVersion: linkIntent.schemaVersion,
            commandType: linkIntent.commandType,
            refId: linkIntent.refId,
            targetType: linkIntent.targetType,
            targetId: linkIntent.targetId
          }
        }
      : {
          id,
          frameType: DocumentWsFrameType.CLIENT_UPDATE,
          payload: update.slice()
        }
    // 每个已提交的本地 Yjs 变更都会保留到服务端 ACK；因此 bootstrap 期间的变更
    // 能在首次 SYNC_COMPLETE 和后续重连后继续发送。
    this.pendingUpdates.set(id, pending)
    if (this.synchronized) this.sendPendingUpdate(pending)
  }

  /** 仅在本地状态变化且连接已同步时请求发送 Awareness。 */
  private readonly handleAwarenessUpdate = (_changes: unknown, origin: unknown): void => {
    if (origin !== REMOTE_AWARENESS_ORIGIN && this.synchronized && !this.disposed) {
      this.requestLocalAwarenessSend()
    }
  }

  /** 把 awareness 状态数量通知页面层更新协作者显示。 */
  private readonly handleAwarenessChange = (): void => {
    this.onAwarenessChange?.(this.awarenessParticipantCount())
  }

  /** 区分文本控制帧和二进制帧，并把协议错误转为连接 error 状态。 */
  private handleSocketMessage(socket: WebSocket, event: MessageEvent<string | ArrayBuffer>): void {
    if (this.socket !== socket || this.disposed) return
    try {
      if (typeof event.data === 'string') {
        this.handleControl(parseDocumentWsControl(event.data))
        return
      }
      if (event.data instanceof ArrayBuffer) this.handleBinary(decodeDocumentWsFrame(event.data))
    } catch (error) {
      /** 面向页面层展示的协议处理错误文本。 */
      const message = error instanceof Error ? error.message : '文档同步协议处理失败'
      this.fail(message)
      // 元数据解析失败后不能继续使用半可信的 Room 状态；关闭当前连接并等待上层决定是否重试。
      this.socket?.close(1002, 'document protocol error')
    }
  }

  /** 处理同步完成、更新确认、心跳和服务端错误控制帧。 */
  private handleControl(control: DocumentWsControlMessage): void {
    switch (control.type) {
      case 'AWARENESS_META':
        this.handleAwarenessMeta(parseDocumentWsAwarenessMeta(control, this.documentId))
        break
      case 'SYNC_COMPLETE':
        // SYNC_COMPLETE 是服务端已经排完全部 Bootstrap 帧的结束边界；先完成最终
        // Y.Doc 构建，再允许本地编辑进入服务端，避免把同步中的半成品暴露给发送链路。
        this.applyPendingBootstrap()
        this.synchronized = true
        this.setState('synced')
        // 最终构建完成后才发送本地队列中的变更，从而让它们在服务端快照、历史更新
        // 和同步期间收到的远端更新之后应用。
        this.pendingUpdates.forEach(update => this.sendPendingUpdate(update))
        this.clearAwarenessSendTimer()
        this.sendLocalAwareness()
        this.resolveInitialSync()
        break
      case 'UPDATE_ACCEPTED':
        if (control.clientUpdateId) this.pendingUpdates.delete(control.clientUpdateId)
        break
      case 'LINK_ACCEPTED': {
        const accepted = parseDocumentWsLinkAccepted(control, this.documentId)
        this.pendingUpdates.delete(accepted.clientUpdateId)
        this.onLinkAccepted?.(accepted)
        break
      }
      case 'PING':
        this.sendControl(createDocumentWsControl('PONG', {
          requestId: control.requestId,
          documentId: this.documentId
        }))
        break
      case 'ERROR':
        if (isTerminalAccessCode(control.code)) {
          this.setWriteEnabled(false)
          this.onAccessError?.({
            code: control.code,
            message: control.message,
            requestId: control.requestId,
            documentId: control.documentId
          })
        }
        this.fail(control.message || control.code || '文档服务拒绝了当前连接')
        this.socket?.close(1008, 'document protocol error')
        break
      default:
        break
    }
  }

  /** 把服务端二进制帧按 Bootstrap 和 Remote Queue 顺序应用到 Y.Doc。 */
  private applyPendingBootstrap(): void {
    for (const frame of this.pendingBootstrapFrames) {
      // Snapshot 与 Bootstrap Update 都来自服务端，统一使用远端 origin，避免再次生成 CLIENT_UPDATE。
      Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
    }
    for (const update of this.pendingRemoteUpdates) {
      // Remote Queue 允许与 Snapshot/OpLog/Redis 重复，重复合并交给 Yjs 处理。
      Y.applyUpdate(this.ydoc, update, REMOTE_UPDATE_ORIGIN)
    }
    this.pendingBootstrapFrames.length = 0
    this.pendingRemoteUpdates.length = 0
  }

  /** 处理服务端可信的 Session 元数据，不把身份字段合并回不透明的 Awareness payload。 */
  private handleAwarenessMeta(metadata: DocumentWsAwarenessMeta): void {
    if (metadata.action === 'UPSERT') {
      const session: DocumentAwarenessSessionMetadata = {
        awarenessClientId: metadata.awarenessClientId,
        sessionId: metadata.sessionId,
        userId: metadata.userId,
        name: metadata.name,
        color: metadata.color
      }
      const previous = this.awarenessSessions.get(session.awarenessClientId)
      if (previous && previous.sessionId !== session.sessionId
          && session.awarenessClientId !== this.awareness.clientID) {
        // 同一个 Awareness ID 重新绑定到新 Session 时，先撤掉旧光标，等待新帧重新建立状态。
        removeAwarenessStates(this.awareness, [session.awarenessClientId], REMOTE_AWARENESS_ORIGIN)
      }
      const metadataChanged = !previous
        || previous.sessionId !== session.sessionId
        || previous.userId !== session.userId
        || previous.name !== session.name
        || previous.color !== session.color
      this.awarenessSessions.set(session.awarenessClientId, session)
      this.emitAwarenessSessionEvent({ action: 'UPSERT', metadata: session })
      if (metadataChanged) {
        // 元数据不在 Awareness 二进制帧内；用远端 origin 触发 CollaborationCaret 重读 façade，
        // 不经过 handleAwarenessUpdate 回写网络。
        this.notifyAwarenessMetadataChange({
          added: previous ? [] : [session.awarenessClientId],
          updated: previous ? [session.awarenessClientId] : [],
          removed: []
        })
      }
      return
    }

    const existing = this.awarenessSessions.get(metadata.awarenessClientId)
    if (existing && existing.sessionId !== metadata.sessionId) {
      // 重连可能让旧 Session 的 REMOVE 晚于新 Session 的 UPSERT 到达，不能误删新身份。
      return
    }
    const hadAwarenessState = this.awareness.getStates().has(metadata.awarenessClientId)
    this.awarenessSessions.delete(metadata.awarenessClientId)
    if (metadata.awarenessClientId !== this.awareness.clientID) {
      // 服务端 REMOVE 只描述生命周期；实际 Awareness 状态必须按 client ID 显式移除。
      removeAwarenessStates(this.awareness, [metadata.awarenessClientId], REMOTE_AWARENESS_ORIGIN)
    }
    this.emitAwarenessSessionEvent({
      action: 'REMOVE',
      awarenessClientId: metadata.awarenessClientId,
      sessionId: metadata.sessionId
    })
    if (!hadAwarenessState || metadata.awarenessClientId === this.awareness.clientID) {
      // 没有原始 Awareness 状态，或清理本地 Session 元数据时，需要手动刷新 façade。
      this.notifyAwarenessMetadataChange({
        added: [],
        updated: [],
        removed: [metadata.awarenessClientId]
      })
    }
  }

  /** 清理旧连接残留的 Session 元数据和远端 Awareness，保留当前本地状态。 */
  private clearAwarenessSessions(notifySessionEvents = true): void {
    const sessions = Array.from(this.awarenessSessions.values())
    this.awarenessSessions.clear()
    if (notifySessionEvents && !this.disposed) {
      for (const session of sessions) {
        this.emitAwarenessSessionEvent({
          action: 'REMOVE',
          awarenessClientId: session.awarenessClientId,
          sessionId: session.sessionId
        })
      }
    }

    const remoteClientIds = Array.from(this.awareness.getStates().keys())
      .filter(clientId => clientId !== this.awareness.clientID)
    const remoteStateIds = new Set(remoteClientIds)
    if (remoteClientIds.length > 0) {
      // 使用远端 origin，避免重连清理被误编码并再次发送给新连接。
      removeAwarenessStates(this.awareness, remoteClientIds, REMOTE_AWARENESS_ORIGIN)
    }
    const metadataOnlyIds = sessions
      .map(session => session.awarenessClientId)
      .filter(clientId => !remoteStateIds.has(clientId))
    if (metadataOnlyIds.length > 0) {
      this.notifyAwarenessMetadataChange({
        added: [],
        updated: [],
        removed: metadataOnlyIds
      })
    }
  }

  /** 向页面提供当前元数据快照；Map 副本保证页面不能反向修改生命周期状态。 */
  private emitAwarenessSessionEvent(event: DocumentAwarenessSessionEvent): void {
    this.onAwarenessSessionChange?.(event, this.getAwarenessSessions())
  }

  /** 用服务端元数据和本地 Awareness 状态计算协作者数量。 */
  private awarenessParticipantCount(): number {
    return this.awarenessSessions.size + (this.awarenessSessions.has(this.awareness.clientID) ? 0 : 1)
  }

  /**
   * 让依赖 Awareness change/update 的本地插件感知服务端元数据变化。
   * origin 必须是远端标记，避免伪造的元数据事件再次进入网络发送链路。
   */
  private notifyAwarenessMetadataChange(changes: {
    added: number[]
    updated: number[]
    removed: number[]
  }): void {
    if (this.disposed) return
    this.awareness.emit('change', [changes, REMOTE_AWARENESS_ORIGIN])
    this.awareness.emit('update', [changes, REMOTE_AWARENESS_ORIGIN])
  }

  /** 把服务端快照、历史、协作者更新接入同步缓存或 Y.Doc。 */
  private handleBinary(frame: ReturnType<typeof decodeDocumentWsFrame>): void {
    switch (frame.type) {
      case DocumentWsFrameType.SNAPSHOT_STATE:
      case DocumentWsFrameType.BOOTSTRAP_UPDATE:
        if (!this.synchronized) {
          // Snapshot 与 Bootstrap Update 必须等到结束边界后再参与最终构建。
          this.pendingBootstrapFrames.push({ type: frame.type, payload: frame.payload.slice() })
          break
        }
        // 正常 ACTIVE 阶段不应再收到 Bootstrap 帧；即使收到，也保持 Yjs 远端合并语义。
        Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
        break
      case DocumentWsFrameType.CRDT_UPDATE:
        if (!this.synchronized) {
          // 这是 Tlive 之后到达的远端更新，先保存；它可能与 Redis 或 OpLog 重复，不能丢弃。
          this.pendingRemoteUpdates.push(frame.payload.slice())
          break
        }
        // ACTIVE 阶段的实时更新可以直接应用；仍使用远端 origin，避免回写服务端。
        Y.applyUpdate(this.ydoc, frame.payload, REMOTE_UPDATE_ORIGIN)
        break
      case DocumentWsFrameType.AWARENESS:
        applyAwarenessUpdate(this.awareness, frame.payload, REMOTE_AWARENESS_ORIGIN)
        break
      default:
        break
    }
  }

  /** 连接关闭后清除同步标志，并进入受控的指数退避重连。 */
  private handleSocketClosed(socket: WebSocket): void {
    if (this.socket !== socket) return
    this.socket = null
    this.clearAwarenessSendTimer()
    // 断线期间不会再收到旧 Room 的 REMOVE；先清除缓存，防止重连后保留幽灵协作者。
    this.clearAwarenessSessions()
    this.synchronized = false
    if (this.disposed || this.currentState === 'error') return
    this.scheduleReconnect()
  }

  /** 安排唯一的指数退避定时器，避免并发创建多个重连连接。 */
  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null || this.disposed) return
    // 每次失败连接都会增加等待时间，上限十秒；同一时刻只保留一个重试定时器。
    /** 本次重连等待时间，按指数退避计算并限制在十秒以内。 */
    const delay = Math.min(500 * 2 ** this.reconnectAttempts, MAX_RECONNECT_DELAY_MS)
    this.reconnectAttempts += 1
    this.setState('reconnecting')
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      void this.refreshAccessBeforeReconnect()
    }, delay)
  }

  /** 重连前刷新资源权限；权限拒绝终止重连，网络异常沿用指数退避。 */
  private async refreshAccessBeforeReconnect(): Promise<void> {
    if (this.disposed) return
    if (this.onBeforeReconnect) {
      let result: DocumentReconnectAccessResult
      try {
        result = await this.onBeforeReconnect()
      } catch {
        result = { status: 'retry' }
      }
      if (this.disposed) return
      if (result.status === 'denied') {
        this.setWriteEnabled(false)
        this.onAccessError?.({
          code: result.code,
          message: result.message || '文档不存在或无权访问',
          requestId: null,
          documentId: this.documentId
        })
        this.fail(result.message || '文档不存在或无权访问')
        return
      }
      if (result.status === 'retry') {
        this.scheduleReconnect()
        return
      }
      this.setWriteEnabled(result.canWrite)
    }
    this.connect()
  }

  /** 发送一条仍在等待服务端确认的客户端更新。 */
  private sendPendingUpdate(update: PendingUpdate): void {
    if (!this.writable || !this.synchronized || !this.isSocketOpen()) return
    if (update.frameType === DocumentWsFrameType.LINK) {
      this.socket!.send(encodeDocumentWsFrame(
        DocumentWsFrameType.LINK,
        update.id,
        encodeDocumentLinkPayload(update.envelope, update.payload)
      ))
      return
    }
    this.socket!.send(encodeDocumentWsFrame(DocumentWsFrameType.CLIENT_UPDATE, update.id, update.payload))
  }

  /** 请求发送当前本地 Awareness；首帧立即发送，窗口内只保留最后状态。 */
  private requestLocalAwarenessSend(): void {
    if (!this.synchronized || this.disposed || !this.isSocketOpen()) return

    const now = Date.now()
    const elapsed = this.awarenessLastSentAt === null
      ? AWARENESS_THROTTLE_MS
      : now - this.awarenessLastSentAt
    if (elapsed >= AWARENESS_THROTTLE_MS) {
      this.clearAwarenessSendTimer()
      this.sendLocalAwareness()
      return
    }

    if (this.awarenessSendTimer !== null) return
    this.awarenessSendTimer = window.setTimeout(() => {
      this.awarenessSendTimer = null
      this.sendLocalAwareness()
    }, Math.max(0, AWARENESS_THROTTLE_MS - elapsed))
  }

  /** 清理尚未触发的本地 Awareness 节流任务。 */
  private clearAwarenessSendTimer(): void {
    if (this.awarenessSendTimer === null) return
    window.clearTimeout(this.awarenessSendTimer)
    this.awarenessSendTimer = null
  }

  /** 清理尚未触发的 composition flush 任务。 */
  private clearCompositionFlushTimer(): void {
    if (this.compositionFlushTimer === null) return
    window.clearTimeout(this.compositionFlushTimer)
    this.compositionFlushTimer = null
  }

  /** 完成所有等待中的首轮同步调用方。 */
  private resolveInitialSync(): void {
    if (this.initialSyncCompleted) return
    this.initialSyncCompleted = true
    const waiters = this.initialSyncWaiters.splice(0)
    for (const waiter of waiters) waiter.resolve()
  }

  /** 让首轮同步调用方在终端失败或释放时退出等待。 */
  private rejectInitialSync(reason: unknown): void {
    if (this.initialSyncCompleted) return
    const waiters = this.initialSyncWaiters.splice(0)
    for (const waiter of waiters) waiter.reject(reason)
  }

  /** 发送当前客户端的 awareness 状态，不写入持久化更新队列。 */
  private sendLocalAwareness(): void {
    if (!this.synchronized || !this.isSocketOpen()) return
    /** 当前用户 awareness 的二进制编码，不进入 Yjs 持久化更新队列。 */
    const payload = encodeAwarenessUpdate(this.awareness, [this.awareness.clientID])
    this.socket!.send(encodeDocumentWsFrame(
      DocumentWsFrameType.AWARENESS,
      createDocumentWsRequestId(),
      payload
    ))
    this.awarenessLastSentAt = Date.now()
  }

  /**
   * 为下一次 WebSocket 连接切换到新的 Awareness 身份，同时保留本地状态。
   *
   * Awareness.clientID 是独立于 Y.Doc.clientID 的状态索引；只修改后者会让
   * Awareness 的 states/meta 不一致，并在编码本地状态时找不到对应时钟。
   */
  private rotateAwarenessClientId(): void {
    const localState = this.awareness.getLocalState()
    const usedClientIds = new Set<number>([
      this.ydoc.clientID,
      ...this.awareness.getStates().keys(),
      ...this.awareness.meta.keys(),
      ...this.awarenessSessions.keys()
    ])
    const nextClientId = generateAwarenessClientId(usedClientIds)

    // 先以本地 origin 移除旧键下的状态，再切换键并恢复原状态；连接尚未同步，
    // 因此这两个本地事件不会被发送到服务端，也不会进入 pendingUpdates。
    this.awareness.setLocalState(null)
    this.awareness.clientID = nextClientId
    this.awareness.setLocalState(localState)
  }

  /** 发送控制 JSON；连接未打开时静默等待后续重连。 */
  private sendControl(control: DocumentWsControlMessage): void {
    if (!this.isSocketOpen()) return
    this.socket!.send(JSON.stringify(control))
  }

  /** 判断当前 WebSocket 是否处于可发送状态。 */
  private isSocketOpen(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }

  /** 记录不可恢复的协议错误并通知页面层。 */
  private fail(message: string): void {
    this.rejectInitialSync(new Error(message))
    this.setState('error', message)
  }

  /** 更新连接状态并向页面层发出状态变化通知。 */
  private setState(state: DocumentConnectionState, message?: string): void {
    this.currentState = state
    this.onStateChange?.(state, message)
  }
}

/** 按字段合并本地 Awareness；相对位置等复杂值作为一个完整字段替换。 */
function mergeAwarenessState(
  current: Record<string, unknown> | null,
  patch: Record<string, unknown>
): Record<string, unknown> {
  const next: Record<string, unknown> = { ...(current || {}) }
  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined) continue
    if (value === null) {
      next[key] = null
      continue
    }
    const currentValue = next[key]
    if (AWARENESS_OBJECT_FIELDS.has(key) && isObjectRecord(currentValue) && isObjectRecord(value)) {
      // 对嵌套字段同样忽略 undefined，避免 Agent 等部分更新写入空字段。
      const mergedValue: Record<string, unknown> = { ...currentValue }
      for (const [nestedKey, nestedValue] of Object.entries(value)) {
        if (nestedValue !== undefined) mergedValue[nestedKey] = nestedValue
      }
      next[key] = mergedValue
      continue
    }
    next[key] = value
  }
  return next
}

/** 只把普通对象当作可合并的 Awareness 分支，避免破坏数组或 Yjs 二进制值。 */
function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** 生成服务端可接受且不复用本地历史 Awareness 身份的正整数。 */
function generateAwarenessClientId(usedClientIds: ReadonlySet<number>): number {
  const value = new Uint32Array(1)
  do {
    globalThis.crypto.getRandomValues(value)
  } while (value[0] === 0 || usedClientIds.has(value[0]))
  return value[0]
}

/** 这些错误代表文档资源不可访问，客户端不能把它们当成可重试网络错误。 */
function isTerminalAccessCode(code: string | null): boolean {
  return code === 'DOCUMENT_FORBIDDEN' || code === 'DOCUMENT_NOT_FOUND'
}

/** 按环境变量或当前页面 host 解析文档 WebSocket 地址。 */
function documentWebSocketUrl(): string {
  /** 环境变量中配置的 WebSocket 地址；未配置时回退到当前页面 host。 */
  const configured = import.meta.env?.VITE_DOCUMENT_WS_URL?.trim()
  if (configured) return configured
  /** 根据当前页面协议选择 ws 或 wss。 */
  const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${scheme}//${window.location.host}/ws/document`
}
