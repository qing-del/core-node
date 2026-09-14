import * as Y from 'yjs'
import { Extension } from '@tiptap/core'
import { Fragment, Slice, type Node as ProseMirrorNode } from '@tiptap/pm/model'
import { NodeSelection, Plugin, PluginKey, type EditorState, type Transaction } from '@tiptap/pm/state'
import { dropPoint } from '@tiptap/pm/transform'
import type { EditorView } from '@tiptap/pm/view'
import {
  DocumentBindingCommandType,
  DocumentBindingTargetType,
  isDocumentLinkIntent,
  type DocumentLinkIntent
} from '../collaboration/documentProtocol.ts'
import type { FileResourceType } from '@/api/files'
import type { ResourceReferenceAttributes } from './ResourceReference.ts'
import {
  hasResourceReferenceIdentityMismatch,
  isValidCrdtNodeId,
  remapCrdtNodeIdentitiesInSlice
} from './crdtNodeIdentity.ts'

export const DOCUMENT_RESOURCE_TYPE: FileResourceType = 'DOCUMENT'
export const RESOURCE_REFERENCE_NODE_NAME = 'resourceReference'
export const DOCUMENT_LINK_TRANSACTION_META = 'document-link-transaction'

const MAX_SIGNED_BIGINT = 0x7fffffffffffffffn
const MAX_REF_ID_ATTEMPTS = 100

/** 将现有 API 的 safe integer 文档 ID 转为协议使用的 MySQL BIGINT。 */
export function documentIdToTargetId(documentId: number): bigint {
  if (!Number.isSafeInteger(documentId) || documentId <= 0) {
    throw new Error('文档 ID 无效')
  }
  return BigInt(documentId)
}

/** 将资源引用属性中的十进制文档 ID 解析为协议 targetId。 */
export function resourceIdToTargetId(resourceId: string | null): bigint | null {
  if (typeof resourceId !== 'string' || !/^[1-9]\d*$/.test(resourceId)) return null
  try {
    const targetId = BigInt(resourceId)
    return targetId <= MAX_SIGNED_BIGINT ? targetId : null
  } catch {
    return null
  }
}

/** 将正文资源类型映射为线上 targetType；未知历史值不能进入 LINK。 */
export function resourceTypeToTargetType(
  resourceType: FileResourceType | null
): DocumentBindingTargetType | null {
  if (resourceType === 'DOCUMENT') return DocumentBindingTargetType.DOCUMENT
  if (resourceType === 'NOTE') return DocumentBindingTargetType.NOTE
  if (resourceType === 'IMAGE') return DocumentBindingTargetType.IMAGE
  return null
}

/** 创建一个已经带有固定 kind 的资源绑定意图，供 Yjs transaction origin 使用。 */
export function createResourceLinkIntent(
  commandType: DocumentBindingCommandType,
  refId: string,
  targetType: DocumentBindingTargetType,
  targetId: bigint
): DocumentLinkIntent {
  const intent: DocumentLinkIntent = {
    kind: 'document-link-intent',
    schemaVersion: 1,
    commandType,
    refId,
    targetType,
    targetId
  }
  if (!isDocumentLinkIntent(intent)) throw new Error('资源 LinkIntent 无效')
  return intent
}

/** 兼容旧 DOCUMENT 调用方的固定目标 wrapper。 */
export function createDocumentLinkIntent(
  commandType: DocumentBindingCommandType,
  refId: string,
  targetId: bigint
): DocumentLinkIntent {
  return createResourceLinkIntent(commandType, refId, DocumentBindingTargetType.DOCUMENT, targetId)
}

/** 创建任意 file resource 的 BIND 意图；resourceId 仍以字符串承载。 */
export function createResourceBindIntent(
  refId: string,
  resourceType: FileResourceType,
  resourceId: string | bigint
): DocumentLinkIntent {
  const targetType = resourceTypeToTargetType(resourceType)
  const targetId = typeof resourceId === 'bigint' ? resourceId : resourceIdToTargetId(resourceId)
  if (targetType === null || targetId === null) throw new Error('资源绑定目标无效')
  return createResourceLinkIntent(DocumentBindingCommandType.BIND, refId, targetType, targetId)
}

/** 创建 DOCUMENT BIND 意图；保留旧 API 以兼容现有调用方和历史测试。 */
export function createBindIntent(refId: string, documentId: number | bigint): DocumentLinkIntent {
  return createResourceBindIntent(
    refId,
    DOCUMENT_RESOURCE_TYPE,
    typeof documentId === 'bigint' ? documentId : documentIdToTargetId(documentId)
  )
}

/**
 * 从正文节点属性创建 UNBIND 意图。
 *
 * 历史占位节点没有完整目标信息时返回 null，调用方应让它走普通删除，不能发非法 LINK。
 */
export function createUnbindIntent(
  attributes: Pick<ResourceReferenceAttributes, 'refId' | 'resourceType' | 'resourceId'>
    & { nodeId?: unknown }
): DocumentLinkIntent | null {
  const hasNodeId = typeof attributes.nodeId === 'string' && attributes.nodeId.length > 0
  if (!attributes.refId
      || (hasNodeId && (!isValidCrdtNodeId(attributes.nodeId)
        || attributes.nodeId.toLowerCase() !== attributes.refId.toLowerCase()))
      || hasResourceReferenceIdentityMismatch(attributes)) return null
  const targetType = resourceTypeToTargetType(attributes.resourceType)
  if (targetType === null) return null
  const targetId = resourceIdToTargetId(attributes.resourceId)
  if (targetId === null) return null
  try {
    return createResourceLinkIntent(DocumentBindingCommandType.UNBIND, attributes.refId, targetType, targetId)
  } catch {
    return null
  }
}

/** 在一个 Yjs transaction 中执行唯一一个带 LinkIntent 的编辑器命令。 */
export function runDocumentLinkTransaction(
  ydoc: Y.Doc,
  intent: DocumentLinkIntent,
  command: () => boolean
): boolean {
  let result = false
  ydoc.transact(() => {
    result = command()
  }, intent)
  return result
}

/** 创建新资源引用属性；nodeId 和 refId 共用同一个全新 UUID。 */
export function createResourceReferenceAttributes(
  resourceType: FileResourceType,
  resourceId: string,
  displayText: string,
  existingNodeIds: ReadonlySet<string>,
  generateNodeId: () => string = () => crypto.randomUUID()
): ResourceReferenceAttributes {
  if (resourceTypeToTargetType(resourceType) === null || resourceIdToTargetId(resourceId) === null) {
    throw new Error('资源引用目标无效')
  }
  const nodeId = createUniqueRefId(existingNodeIds, generateNodeId)
  return {
    nodeId,
    nodeVersion: 0,
    refId: nodeId,
    resourceType,
    resourceId,
    displayText,
    alias: null
  }
}

/** 创建新 DOCUMENT 引用属性；保留旧 helper 的 number 入参。 */
export function createDocumentReferenceAttributes(
  documentId: number,
  displayText: string,
  existingRefIds: ReadonlySet<string>,
  generateRefId: () => string = () => crypto.randomUUID()
): ResourceReferenceAttributes {
  return createResourceReferenceAttributes(
    DOCUMENT_RESOURCE_TYPE,
    documentIdToTargetId(documentId).toString(),
    displayText,
    existingRefIds,
    generateRefId
  )
}

/** 生成不为空且不与正文已有引用冲突的 UUID。 */
export function createUniqueRefId(
  existingRefIds: ReadonlySet<string>,
  generateRefId: () => string = () => crypto.randomUUID()
): string {
  const used = new Set(Array.from(existingRefIds, normalizeRefId))
  for (let attempt = 0; attempt < MAX_REF_ID_ATTEMPTS; attempt += 1) {
    const candidate = generateRefId()
    if (isNonZeroUuid(candidate) && !used.has(normalizeRefId(candidate))) return candidate
  }
  throw new Error('无法生成唯一的资源引用 ID')
}

/** 收集正文中已经存在的 resourceReference refId；旧调用方继续使用该窄集合。 */
export function getResourceReferenceIds(document: ProseMirrorNode): Set<string> {
  const ids = new Set<string>()
  document.descendants(node => {
    if (node.type.name === RESOURCE_REFERENCE_NODE_NAME && typeof node.attrs.refId === 'string') {
      ids.add(node.attrs.refId)
    }
    return true
  })
  return ids
}

/** 统计一个节点树或粘贴片段中的资源引用数量。 */
export function countResourceReferences(document: ProseMirrorNode): number {
  let count = 0
  document.descendants(node => {
    if (node.type.name === RESOURCE_REFERENCE_NODE_NAME) count += 1
    return true
  })
  return count
}

/** 收集片段内的 resourceReference 属性；用于粘贴策略和绑定目标校验。 */
export function getResourceReferenceAttributesInFragment(fragment: Fragment): ResourceReferenceAttributes[] {
  const references: ResourceReferenceAttributes[] = []
  fragment.forEach(node => collectResourceReferenceAttributes(node, references))
  return references
}

/** 将引用节点降级为可读普通文本，不产生绑定动作。 */
export function flattenResourceReferencesToText(slice: Slice): Slice {
  const content = mapResourceReferenceFragment(slice.content, node => {
    if (node.type.name !== RESOURCE_REFERENCE_NODE_NAME) return node
    return node.type.schema.text(formatResourceReferenceText(node.attrs))
  })
  return new Slice(content, slice.openStart, slice.openEnd)
}

/** 兼容旧调用方，仅重映射 resourceReference；编辑器粘贴路径使用全量 helper。 */
export function remapResourceReferenceIds(
  document: ProseMirrorNode,
  existingRefIds: ReadonlySet<string>,
  generateRefId: () => string = () => crypto.randomUUID()
): ProseMirrorNode {
  const ids = new Set(existingRefIds)
  return mapResourceReferenceNodes(document, node => {
    if (node.type.name !== RESOURCE_REFERENCE_NODE_NAME) return node
    const refId = createUniqueRefId(ids, generateRefId)
    ids.add(refId)
    const attrs: Record<string, unknown> = { ...node.attrs, nodeId: refId, nodeVersion: 0 }
    if (!hasResourceReferenceIdentityMismatch(node.attrs)) attrs.refId = refId
    return node.type.create(attrs, node.content, node.marks)
  })
}

/** 兼容旧调用方，仅重映射 resourceReference；编辑器粘贴路径使用全量 helper。 */
export function remapResourceReferenceIdsInSlice(
  slice: Slice,
  existingRefIds: ReadonlySet<string>,
  generateRefId: () => string = () => crypto.randomUUID()
): Slice {
  const ids = new Set(existingRefIds)
  const content = mapResourceReferenceFragment(slice.content, node => {
    if (node.type.name !== RESOURCE_REFERENCE_NODE_NAME) return node
    const refId = createUniqueRefId(ids, generateRefId)
    ids.add(refId)
    const attrs: Record<string, unknown> = { ...node.attrs, nodeId: refId, nodeVersion: 0 }
    if (!hasResourceReferenceIdentityMismatch(node.attrs)) attrs.refId = refId
    return node.type.create(attrs, node.content, node.marks)
  })
  return new Slice(content, slice.openStart, slice.openEnd)
}

/** 对节点树递归复制并映射 resourceReference；源节点和源文档不会被修改。 */
function mapResourceReferenceNodes(
  document: ProseMirrorNode,
  mapper: (node: ProseMirrorNode) => ProseMirrorNode
): ProseMirrorNode {
  if (document.content.size === 0) return mapper(document)
  const content = mapResourceReferenceFragment(document.content, mapper)
  return mapper(document.copy(content))
}

function mapResourceReferenceFragment(
  fragment: Fragment,
  mapper: (node: ProseMirrorNode) => ProseMirrorNode
): Fragment {
  return Fragment.fromArray(fragment.content.map(child => mapResourceReferenceNodes(child, mapper)))
}

function collectResourceReferenceAttributes(
  node: ProseMirrorNode,
  references: ResourceReferenceAttributes[]
): void {
  if (node.type.name === RESOURCE_REFERENCE_NODE_NAME) {
    references.push(toResourceReferenceAttributes(node.attrs))
  }
  node.content.forEach(child => collectResourceReferenceAttributes(child, references))
}

function toResourceReferenceAttributes(attrs: Record<string, unknown>): ResourceReferenceAttributes {
  return {
    nodeId: typeof attrs.nodeId === 'string' ? attrs.nodeId : '',
    nodeVersion: typeof attrs.nodeVersion === 'number' && Number.isSafeInteger(attrs.nodeVersion)
      && attrs.nodeVersion >= 0 ? attrs.nodeVersion : 0,
    refId: typeof attrs.refId === 'string' ? attrs.refId : '',
    resourceType: attrs.resourceType === 'NOTE' || attrs.resourceType === 'IMAGE'
      || attrs.resourceType === 'DOCUMENT' ? attrs.resourceType : null,
    resourceId: typeof attrs.resourceId === 'string' ? attrs.resourceId : null,
    displayText: typeof attrs.displayText === 'string' ? attrs.displayText : '',
    alias: typeof attrs.alias === 'string' ? attrs.alias : null
  }
}

function formatResourceReferenceText(attrs: Record<string, unknown>): string {
  const alias = typeof attrs.alias === 'string' ? attrs.alias.trim() : ''
  const displayText = typeof attrs.displayText === 'string' ? attrs.displayText.trim() : ''
  return alias || displayText || '未命名引用'
}

/** 判断引用是否拥有可以发送给关系投影器的完整资源目标信息。 */
function isBoundResourceReference(attributes: ResourceReferenceAttributes): boolean {
  return resourceTypeToTargetType(attributes.resourceType) !== null
    && isNonZeroUuid(attributes.refId)
    && (!attributes.nodeId
      || (isValidCrdtNodeId(attributes.nodeId)
        && attributes.nodeId.toLowerCase() === attributes.refId.toLowerCase()))
    && !hasResourceReferenceIdentityMismatch(attributes)
    && resourceIdToTargetId(attributes.resourceId) !== null
}

interface DocumentDeletionTarget {
  from: number
  to: number
  attributes: ResourceReferenceAttributes
}

/** 找出一次删除/替换范围内的完整绑定引用；历史占位节点故意忽略。 */
function getBoundReferencesInRange(
  state: EditorState,
  from: number,
  to: number
): DocumentDeletionTarget[] {
  const targets: DocumentDeletionTarget[] = []
  state.doc.nodesBetween(from, to, (node, position) => {
    if (node.type.name !== RESOURCE_REFERENCE_NODE_NAME) return
    const attributes = toResourceReferenceAttributes(node.attrs)
    if (isBoundResourceReference(attributes)) {
      targets.push({ from: position, to: position + node.nodeSize, attributes })
    }
  })
  return targets
}

/** 根据按键方向取得光标相邻的引用，或取得普通文本选择范围内的引用。 */
function getDeletionTargets(state: EditorState, key: string): DocumentDeletionTarget[] {
  const { selection } = state
  if (selection instanceof NodeSelection && selection.node.type.name === RESOURCE_REFERENCE_NODE_NAME) {
    const attributes = toResourceReferenceAttributes(selection.node.attrs)
    return isBoundResourceReference(attributes)
      ? [{ from: selection.from, to: selection.to, attributes }]
      : []
  }

  if (!selection.empty) return getBoundReferencesInRange(state, selection.from, selection.to)

  const $from = selection.$from
  const adjacent = key === 'Backspace' ? $from.nodeBefore : $from.nodeAfter
  if (!adjacent || adjacent.type.name !== RESOURCE_REFERENCE_NODE_NAME) return []
  const attributes = toResourceReferenceAttributes(adjacent.attrs)
  if (!isBoundResourceReference(attributes)) return []
  const from = key === 'Backspace' ? selection.from - adjacent.nodeSize : selection.from
  return [{ from, to: from + adjacent.nodeSize, attributes }]
}

export interface DocumentLinkTriggerRange {
  from: number
  to: number
  query: string
}

export interface DocumentLinkTrigger extends DocumentLinkTriggerRange {
  mode: 'bind' | 'rebind'
  left: number
  top: number
  refId?: string
}

export interface DocumentResourceLinkExtensionOptions {
  ydoc: Y.Doc
  isEditable: () => boolean
  getExistingNodeIds: () => ReadonlySet<string>
  onTriggerChange: (trigger: DocumentLinkTrigger | null) => void
  onTriggerKeyDown: (event: KeyboardEvent, trigger: DocumentLinkTriggerRange) => boolean
  onResourceReferenceSelectionChange: (attributes: ResourceReferenceAttributes | null) => void
  onActionBlocked: (message: string) => void
}

/** 只基于当前文本光标识别严格的 `[[关键词` 入口，不扫描 Yjs 原始更新。 */
export function findDocumentLinkTrigger(state: EditorState): DocumentLinkTriggerRange | null {
  const { selection } = state
  if (!selection.empty || !selection.$from.parent.isTextblock) return null
  const textBefore = selection.$from.parent.textBetween(0, selection.$from.parentOffset, '\n', '\ufffc')
  const match = /\[\[([^\[\]\n]*)$/.exec(textBefore)
  if (!match || match.index === undefined) return null
  return {
    from: selection.$from.start() + match.index,
    to: selection.from,
    query: match[1].trim()
  }
}

/**
 * 编辑器侧 Link 交互插件：候选入口、单引用粘贴和逐个解绑都在同一 Yjs
 * transaction 中完成。协议客户端只根据 transaction origin 分类发送帧。
 */
export function createDocumentResourceLinkExtension(
  options: DocumentResourceLinkExtensionOptions
): Extension {
  return Extension.create({
    name: 'documentResourceLink',

    addProseMirrorPlugins() {
      return [new Plugin({
        key: new PluginKey('documentResourceLink'),
        view: view => {
          let lastTriggerSignature = ''
          let lastSelectionSignature = ''

          const syncUiState = (currentView: EditorView): void => {
            const range = options.isEditable() ? findDocumentLinkTrigger(currentView.state) : null
            const trigger = range
              ? (() => {
                const coords = currentView.coordsAtPos(range.to)
                return { ...range, mode: 'bind' as const, left: coords.left, top: coords.bottom }
              })()
              : null
            const triggerSignature = trigger
              ? `${trigger.mode}:${trigger.from}:${trigger.to}:${trigger.query}:${trigger.left}:${trigger.top}`
              : 'none'
            if (triggerSignature !== lastTriggerSignature) {
              lastTriggerSignature = triggerSignature
              options.onTriggerChange(trigger)
            }

            const selection = currentView.state.selection
            const selectedNode = selection instanceof NodeSelection
              && selection.node.type.name === RESOURCE_REFERENCE_NODE_NAME
              ? selection.node
              : null
            const attributes = selectedNode ? toResourceReferenceAttributes(selectedNode.attrs) : null
            const selectionSignature = attributes
              ? `${attributes.refId}:${attributes.resourceType}:${attributes.resourceId}:${attributes.displayText}:${attributes.alias}`
              : 'none'
            if (selectionSignature !== lastSelectionSignature) {
              lastSelectionSignature = selectionSignature
              options.onResourceReferenceSelectionChange(attributes)
            }
          }

          syncUiState(view)
          return {
            update: currentView => syncUiState(currentView),
            destroy: () => {
              options.onTriggerChange(null)
              options.onResourceReferenceSelectionChange(null)
            }
          }
        },
        props: {
          handleKeyDown: (view, event) => {
            const trigger = options.isEditable() ? findDocumentLinkTrigger(view.state) : null
            if (trigger && options.onTriggerKeyDown(event, trigger)) {
              event.preventDefault()
              return true
            }
            if (!options.isEditable() || (event.key !== 'Backspace' && event.key !== 'Delete')) return false

            const targets = getDeletionTargets(view.state, event.key)
            if (targets.length === 0) return false
            const deletionFrom = view.state.selection.empty ? targets[0].from : view.state.selection.from
            const deletionTo = view.state.selection.empty ? targets[0].to : view.state.selection.to
            return dispatchReferenceReplacement(
              view,
              options,
              targets,
              () => view.state.tr.delete(deletionFrom, deletionTo),
              '一次只能处理一个文档引用，请逐个删除或解绑。'
            )
          },

          /** 选中原子引用后直接输入文本会替换节点，必须同步生成 UNBIND。 */
          handleTextInput: (view, from, to, _text, deflt) => {
            if (!options.isEditable()) return false
            return dispatchReferenceReplacement(
              view,
              options,
              getBoundReferencesInRange(view.state, from, to),
              deflt,
              '输入会同时删除多个文档引用，请先逐个解绑。'
            )
          },

          /** 接管剪切的删除事务，同时保留 ProseMirror 原有的 HTML/纯文本剪贴板内容。 */
          handleDOMEvents: {
            cut: (view, event: ClipboardEvent) => {
              if (!options.isEditable() || view.state.selection.empty) return false
              const targets = getBoundReferencesInRange(view.state,
                view.state.selection.from, view.state.selection.to)
              if (targets.length === 0) return false
              if (targets.length > 1) {
                event.preventDefault()
                options.onActionBlocked('剪切会同时删除多个文档引用，请先逐个解绑。')
                return true
              }
              // 当前浏览器均提供 clipboardData；不可写时交给默认剪切，避免损坏剪贴板行为。
              if (!event.clipboardData) return false
              const { dom, text } = view.serializeForClipboard(view.state.selection.content())
              event.preventDefault()
              event.clipboardData.clearData()
              event.clipboardData.setData('text/html', dom.innerHTML)
              event.clipboardData.setData('text/plain', text)
              return dispatchReferenceReplacement(
                view,
                options,
                targets,
                () => view.state.tr.deleteSelection(),
                '剪切会同时删除多个文档引用，请先逐个解绑。'
              )
            }
          },

          handlePaste: (view, event, slice) => {
            if (!options.isEditable()) return false

            const targets = selectionBoundReferences(view.state)
            if (targets.length > 1) {
              event.preventDefault()
              options.onActionBlocked('粘贴会同时删除多个已绑定引用，请先逐个解绑。')
              return true
            }

            const references = getResourceReferenceAttributesInFragment(slice.content)
            const remapped = remapCrdtNodeIdentitiesInSlice(slice, options.getExistingNodeIds())
            if (references.length > 1) {
              event.preventDefault()
              options.onActionBlocked('一次只能粘贴一个文档引用，多个引用已转为普通文本。')
              const plainSlice = flattenResourceReferencesToText(remapped)
              return dispatchPaste(view, plainSlice, options, targets)
            }

            if (references.length === 1) {
              if (targets.length === 1) {
                event.preventDefault()
                options.onActionBlocked('请先解绑已有引用，再粘贴新的文档引用。')
                return true
              }
              const [attributes] = getResourceReferenceAttributesInFragment(remapped.content)
              const targetId = attributes ? resourceIdToTargetId(attributes.resourceId) : null
              if (attributes
                  && resourceTypeToTargetType(attributes.resourceType) !== null
                  && targetId !== null
                  && attributes.refId
                  && isBoundResourceReference(attributes)) {
                event.preventDefault()
                try {
                  const intent = createResourceBindIntent(attributes.refId, attributes.resourceType!, targetId)
                  return dispatchPaste(view, remapped, options, [], intent)
                } catch {
                  // 非法导入节点降级为普通文本，不发送 malformed LINK。
                }
              }
              event.preventDefault()
              options.onActionBlocked('粘贴的资源引用无有效目标，已转为普通文本。')
              return dispatchPaste(view, flattenResourceReferencesToText(remapped), options, [])
            }

            if (targets.length === 1) {
              event.preventDefault()
              return dispatchPaste(view, remapped, options, targets)
            }
            // 普通节点复制/粘贴也必须获得新身份；文档内移动由 handleDrop 的 moved 分支保留原 ID。
            event.preventDefault()
            return dispatchPlainPaste(view, remapped)
          },

          /** 外部拖入引用按粘贴策略重映射并 BIND；文档内移动保留原 refId 和关系。 */
          handleDrop: (view, event, slice, moved) => {
            if (!options.isEditable() || moved) return false
            event.preventDefault()
            const remapped = remapCrdtNodeIdentitiesInSlice(slice, options.getExistingNodeIds())
            const references = getResourceReferenceAttributesInFragment(slice.content)
            if (references.length > 1) {
              options.onActionBlocked('一次只能拖入一个文档引用，多个引用已转为普通文本。')
              return dispatchDrop(view, event, flattenResourceReferencesToText(remapped), options)
            }
            if (references.length === 0) return dispatchDrop(view, event, remapped, options)
            const [attributes] = getResourceReferenceAttributesInFragment(remapped.content)
            const targetId = attributes ? resourceIdToTargetId(attributes.resourceId) : null
            if (attributes && resourceTypeToTargetType(attributes.resourceType) !== null
                && targetId !== null && attributes.refId && isBoundResourceReference(attributes)) {
              try {
                return dispatchDrop(
                  view,
                  event,
                  remapped,
                  options,
                  createResourceBindIntent(attributes.refId, attributes.resourceType!, targetId)
                )
              } catch {
                // 降级为普通文本，不能让格式错误的拖入内容生成 malformed LINK。
              }
            }
            options.onActionBlocked('拖入的资源引用无有效目标，已转为普通文本。')
            return dispatchDrop(view, event, flattenResourceReferencesToText(remapped), options)
          }
        }
      })]
    }
  })
}

/** 返回当前选区中会被替换的有效文档引用。 */
function selectionBoundReferences(state: EditorState): DocumentDeletionTarget[] {
  return state.selection.empty
    ? []
    : getBoundReferencesInRange(state, state.selection.from, state.selection.to)
}

/** 将一个单引用替换事务包装为 UNBIND LINK；多引用则保持正文不变。 */
function dispatchReferenceReplacement(
  view: EditorView,
  options: DocumentResourceLinkExtensionOptions,
  targets: DocumentDeletionTarget[],
  createTransaction: () => Transaction,
  blockedMessage: string
): boolean {
  if (targets.length === 0) return false
  if (targets.length > 1) {
    options.onActionBlocked(blockedMessage)
    return true
  }
  const intent = createUnbindIntent(targets[0].attributes)
  // 历史空属性占位节点按普通 CLIENT_UPDATE 处理。
  if (!intent) return false
  const transaction = createTransaction().setMeta(DOCUMENT_LINK_TRANSACTION_META, true)
  options.ydoc.transact(() => view.dispatch(transaction), intent)
  return true
}

/** 对粘贴内容执行替换；若原选区含一个引用，则以 UNBIND 包装整个替换事务。 */
function dispatchPaste(
  view: EditorView,
  slice: Slice,
  options: DocumentResourceLinkExtensionOptions,
  targets: DocumentDeletionTarget[],
  intent?: DocumentLinkIntent
): boolean {
  if (intent) {
    const transaction = view.state.tr
      .replaceSelection(slice)
      .setMeta(DOCUMENT_LINK_TRANSACTION_META, true)
    options.ydoc.transact(() => view.dispatch(transaction), intent)
    return true
  }
  return dispatchReferenceReplacement(
    view,
    options,
    targets,
    () => view.state.tr.replaceSelection(slice),
    '粘贴会同时删除多个已绑定引用，请先逐个解绑。'
  ) || dispatchPlainPaste(view, slice)
}

/** 不会删除有效引用时走普通粘贴事务。 */
function dispatchPlainPaste(view: EditorView, slice: Slice): boolean {
  view.dispatch(view.state.tr.replaceSelection(slice).setMeta(DOCUMENT_LINK_TRANSACTION_META, false))
  return true
}

/** 在鼠标落点插入拖入 Slice；dropPoint 与 ProseMirror 默认拖放保持同一合法位置策略。 */
function dispatchDrop(
  view: EditorView,
  event: DragEvent,
  slice: Slice,
  options: DocumentResourceLinkExtensionOptions,
  intent?: DocumentLinkIntent
): boolean {
  const coordinates = view.posAtCoords({ left: event.clientX, top: event.clientY })
  if (!coordinates) return false
  const position = dropPoint(view.state.doc, coordinates.pos, slice)
  if (position === null) return false
  const transaction = view.state.tr
    .replaceRange(position, position, slice)
    .setMeta(DOCUMENT_LINK_TRANSACTION_META, Boolean(intent))
  if (intent) {
    options.ydoc.transact(() => view.dispatch(transaction), intent)
  } else {
    view.dispatch(transaction)
  }
  return true
}

function normalizeRefId(value: string): string {
  return value.toLowerCase()
}

function isNonZeroUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    && normalizeRefId(value).replace(/-/g, '') !== '00000000000000000000000000000000'
}
