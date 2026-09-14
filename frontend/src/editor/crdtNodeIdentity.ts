import { Extension } from '@tiptap/core'
import { isChangeOrigin } from '@tiptap/extension-collaboration'
import { Fragment, Slice, type Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey, type EditorState, type Transaction } from '@tiptap/pm/state'

/** v0.7 需要持久化稳定身份的节点类型；doc/text 和其他容器不在此集合内。 */
export const CRDT_REGISTERED_NODE_NAMES = [
  'paragraph',
  'heading',
  'listItem',
  'blockquote',
  'codeBlock',
  'resourceReference'
] as const

export type CrdtRegisteredNodeName = typeof CRDT_REGISTERED_NODE_NAMES[number]

/** 注册节点写入 Yjs 的身份字段。 */
export interface CrdtNodeIdentityAttributes {
  nodeId: string
  nodeVersion: number
}

export interface CrdtNodeIdentityOptions {
  /** 可注入确定性的 UUID 生成器，便于协议和复制测试。 */
  generateNodeId?: () => string
}

export const CRDT_NODE_IDENTITY_TRANSACTION_META = 'crdt-node-identity-normalization'
export const RESOURCE_REFERENCE_NODE_NAME = 'resourceReference'

const REGISTERED_NODE_NAME_SET = new Set<string>(CRDT_REGISTERED_NODE_NAMES)
const IDENTITY_ATTRIBUTE_NAMES = new Set(['nodeId', 'nodeVersion'])
const MAX_NODE_ID_ATTEMPTS = 100

/** 校验非零 UUID；节点身份和 resourceReference.refId 共用该格式。 */
export function isValidCrdtNodeId(value: unknown): value is string {
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
    return false
  }
  return value.replace(/-/g, '').toLowerCase() !== '00000000000000000000000000000000'
}

/** 判断一个 ProseMirror 节点是否属于 v0.7 注册集合。 */
export function isCrdtRegisteredNode(node: ProseMirrorNode): boolean {
  return REGISTERED_NODE_NAME_SET.has(node.type.name)
}

/** 返回正文中所有已经存在且格式有效的节点 ID。 */
export function getCrdtNodeIds(document: ProseMirrorNode): Set<string> {
  const ids = new Set<string>()
  visitNodes(document, node => {
    const nodeId = node.attrs.nodeId
    if (isValidCrdtNodeId(nodeId)) ids.add(nodeId.toLowerCase())
  })
  return ids
}

/** 生成不冲突的节点 ID；资源引用会在调用方将同一值写入 refId。 */
export function createUniqueCrdtNodeId(
  usedNodeIds: ReadonlySet<string>,
  generateNodeId: () => string = () => crypto.randomUUID()
): string {
  const used = new Set(Array.from(usedNodeIds, value => value.toLowerCase()))
  for (let attempt = 0; attempt < MAX_NODE_ID_ATTEMPTS; attempt += 1) {
    const candidate = generateNodeId()
    if (isValidCrdtNodeId(candidate) && !used.has(candidate.toLowerCase())) return candidate
  }
  throw new Error('无法生成唯一的 CRDT 节点 ID')
}

/**
 * 复制、粘贴和导入时为所有注册节点生成新的身份。
 * resourceReference 的 refId 与 nodeId 同步重映射，target 属性保持不变。
 */
export function remapCrdtNodeIdentities(
  document: ProseMirrorNode,
  existingNodeIds: ReadonlySet<string> = new Set<string>(),
  generateNodeId: () => string = () => crypto.randomUUID()
): ProseMirrorNode {
  const used = new Set(Array.from(existingNodeIds, value => value.toLowerCase()))
  return remapNode(document, used, generateNodeId)
}

/** 对粘贴 Slice 执行身份重映射，同时保留开放深度。 */
export function remapCrdtNodeIdentitiesInSlice(
  slice: Slice,
  existingNodeIds: ReadonlySet<string> = new Set<string>(),
  generateNodeId: () => string = () => crypto.randomUUID()
): Slice {
  const used = new Set(Array.from(existingNodeIds, value => value.toLowerCase()))
  const content = remapFragment(slice.content, used, generateNodeId)
  return new Slice(content, slice.openStart, slice.openEnd)
}

function remapNode(
  node: ProseMirrorNode,
  usedNodeIds: Set<string>,
  generateNodeId: () => string
): ProseMirrorNode {
  if (node.isText) return node
  let attrs: Record<string, unknown> | null = null
  if (isCrdtRegisteredNode(node)) {
    const nodeId = createUniqueCrdtNodeId(usedNodeIds, generateNodeId)
    usedNodeIds.add(nodeId.toLowerCase())
    attrs = {
      ...node.attrs,
      nodeId,
      nodeVersion: 0
    }
    // 已有 nodeId/refId 不一致时保留原冲突，交给绑定层降级处理，不能在复制时静默修复。
    if (node.type.name === RESOURCE_REFERENCE_NODE_NAME
        && !hasResourceReferenceIdentityMismatch(node.attrs)) {
      attrs.refId = nodeId
    }
  }
  const content = remapFragment(node.content, usedNodeIds, generateNodeId)
  if (!attrs) return node.copy(content)
  return node.type.create(attrs as Record<string, any>, content, node.marks)
}

function remapFragment(
  fragment: Fragment,
  usedNodeIds: Set<string>,
  generateNodeId: () => string
): Fragment {
  return Fragment.fromArray(fragment.content.map(node => remapNode(node, usedNodeIds, generateNodeId)))
}

function visitNodes(document: ProseMirrorNode, visitor: (node: ProseMirrorNode) => void): void {
  if (isCrdtRegisteredNode(document)) visitor(document)
  document.descendants(node => {
    if (isCrdtRegisteredNode(node)) visitor(node)
    return true
  })
}

interface NodeSnapshot {
  node: ProseMirrorNode
  position: number
  nodeVersion: number
  fingerprint: string
}

function readNodeVersion(node: ProseMirrorNode): number | null {
  const value = node.attrs.nodeVersion
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : null
}

function collectSnapshots(document: ProseMirrorNode): Map<string, NodeSnapshot> {
  const snapshots = new Map<string, NodeSnapshot>()
  const visit = (node: ProseMirrorNode, position: number): void => {
    if (!isCrdtRegisteredNode(node)) return
    const nodeId = node.attrs.nodeId
    const nodeVersion = readNodeVersion(node)
    if (!isValidCrdtNodeId(nodeId) || nodeVersion === null) return
    snapshots.set(nodeId.toLowerCase(), {
      node,
      position,
      nodeVersion,
      fingerprint: ownNodeFingerprint(node)
    })
  }

  visit(document, 0)
  document.descendants((node, position) => {
    visit(node, position)
    return true
  })
  return snapshots
}

/**
 * 只序列化节点自身拥有的内容：直接属性、直接文本/格式和非注册直接子结构。
 * 注册子节点被排除，因此子节点变化、移动和重排不会改变祖先版本。
 */
function ownNodeFingerprint(node: ProseMirrorNode): string {
  const attrs = Object.fromEntries(
    Object.entries(node.attrs)
      .filter(([name]) => !IDENTITY_ATTRIBUTE_NAMES.has(name))
      .sort(([left], [right]) => left.localeCompare(right))
  )
  return JSON.stringify({
    type: node.type.name,
    attrs,
    marks: node.marks.map(mark => ({
      type: mark.type.name,
      attrs: mark.attrs
    })),
    directContent: node.content.content
      .filter(child => !isCrdtRegisteredNode(child))
      .map(child => ownContentFingerprint(child))
  })
}

function ownContentFingerprint(node: ProseMirrorNode): unknown {
  if (node.isText) {
    return {
      type: 'text',
      text: node.text,
      marks: node.marks.map(mark => ({ type: mark.type.name, attrs: mark.attrs }))
    }
  }
  return {
    type: node.type.name,
    attrs: Object.fromEntries(
      Object.entries(node.attrs).sort(([left], [right]) => left.localeCompare(right))
    ),
    marks: node.marks.map(mark => ({ type: mark.type.name, attrs: mark.attrs })),
    content: node.content.content
      .filter(child => !isCrdtRegisteredNode(child))
      .map(child => ownContentFingerprint(child))
  }
}

interface IdentityUpdate {
  position: number
  node: ProseMirrorNode
  attrs: Record<string, unknown>
}

/** 为新节点、历史缺失字段和重复 ID 准备单次 appendTransaction 的属性更新。 */
function collectIdentityUpdates(
  document: ProseMirrorNode,
  generateNodeId: () => string
): IdentityUpdate[] {
  const reserved = getCrdtNodeIds(document)
  const used = new Set<string>()
  const updates: IdentityUpdate[] = []
  const visit = (node: ProseMirrorNode, position: number): void => {
    if (!isCrdtRegisteredNode(node)) return
    const attrs: Record<string, unknown> = { ...node.attrs }
    const currentNodeId = attrs.nodeId
    const currentRefId = attrs.refId
    const identityConflict = node.type.name === RESOURCE_REFERENCE_NODE_NAME
      && hasResourceReferenceIdentityMismatch({ nodeId: currentNodeId, refId: currentRefId })
    let nodeId = isValidCrdtNodeId(currentNodeId) ? currentNodeId : null
    if (!identityConflict && (!nodeId || used.has(nodeId.toLowerCase()))) {
      const legacyRefId = node.type.name === RESOURCE_REFERENCE_NODE_NAME && isValidCrdtNodeId(currentRefId)
        ? currentRefId
        : null
      nodeId = legacyRefId && !reserved.has(legacyRefId.toLowerCase())
        ? legacyRefId
        : createUniqueCrdtNodeId(new Set([...reserved, ...used]), generateNodeId)
      attrs.nodeId = nodeId
    }
    if (nodeId) used.add(nodeId.toLowerCase())

    if (readNodeVersion(node) === null) attrs.nodeVersion = 0
    if (!identityConflict && node.type.name === RESOURCE_REFERENCE_NODE_NAME && nodeId
        && !isValidCrdtNodeId(currentRefId)) {
      attrs.refId = nodeId
    }

    const changed = Object.keys(attrs).some(key => attrs[key] !== node.attrs[key])
    if (changed) updates.push({ position, node, attrs })
  }

  visit(document, 0)
  document.descendants((node, position) => {
    visit(node, position)
    return true
  })
  return updates
}

/** 资源引用身份不一致时供绑定层阻断操作；缺失字段不在此处判定为 mismatch。 */
export function hasResourceReferenceIdentityMismatch(
  attributes: { nodeId?: unknown; refId?: unknown }
): boolean {
  const nodeId = typeof attributes.nodeId === 'string' ? attributes.nodeId : ''
  const refId = typeof attributes.refId === 'string' ? attributes.refId : ''
  // 空 nodeId 是历史兼容场景；只要两侧都带了值，格式错误或值不同都属于冲突。
  if (!nodeId || !refId) return false
  return !isValidCrdtNodeId(nodeId)
    || !isValidCrdtNodeId(refId)
    || nodeId.toLowerCase() !== refId.toLowerCase()
}

/** 给六类注册节点补齐身份并按本地事务递增 nodeVersion。 */
export const CrdtNodeIdentity = Extension.create<CrdtNodeIdentityOptions>({
  name: 'crdtNodeIdentity',

  addOptions() {
    return {
      generateNodeId: () => crypto.randomUUID()
    }
  },

  /** 标准 Tiptap 节点使用全局属性，resourceReference 自己声明同名字段以兼容旧 HTML。 */
  addGlobalAttributes() {
    return [{
      types: CRDT_REGISTERED_NODE_NAMES.filter(name => name !== RESOURCE_REFERENCE_NODE_NAME),
      attributes: {
        nodeId: {
          default: null,
          parseHTML: (element: HTMLElement) => element.getAttribute('data-node-id'),
          renderHTML: (attributes: Record<string, unknown>) => ({
            'data-node-id': isValidCrdtNodeId(attributes.nodeId) ? attributes.nodeId : null
          })
        },
        nodeVersion: {
          default: 0,
          parseHTML: (element: HTMLElement) => {
            const value = Number(element.getAttribute('data-node-version'))
            return Number.isSafeInteger(value) && value >= 0 ? value : 0
          },
          renderHTML: (attributes: Record<string, unknown>) => ({
            'data-node-version': readNodeVersionFromAttributes(attributes)
          })
        }
      }
    }]
  },

  addProseMirrorPlugins() {
    const generateNodeId = this.options.generateNodeId ?? (() => crypto.randomUUID())
    return [new Plugin({
      key: new PluginKey('crdtNodeIdentity'),

      appendTransaction: (transactions: readonly Transaction[], oldState: EditorState, newState: EditorState) => {
        const relevantTransactions = transactions.filter(transaction =>
          transaction.docChanged && !transaction.getMeta(CRDT_NODE_IDENTITY_TRANSACTION_META)
        )
        if (relevantTransactions.length === 0) return null

        const updatesByPosition = new Map<number, IdentityUpdate>()
        for (const update of collectIdentityUpdates(newState.doc, generateNodeId)) {
          updatesByPosition.set(update.position, update)
        }

        // Yjs change-origin transactions already carry the authoritative remote version.
        const hasLocalChange = relevantTransactions.some(transaction => !isChangeOrigin(transaction))
        if (hasLocalChange) {
          const before = collectSnapshots(oldState.doc)
          const after = collectSnapshots(newState.doc)
          for (const [nodeId, snapshot] of after) {
            const previous = before.get(nodeId)
            if (!previous || previous.fingerprint === snapshot.fingerprint) continue
            const existing = updatesByPosition.get(snapshot.position)
            updatesByPosition.set(snapshot.position, {
              position: snapshot.position,
              node: snapshot.node,
              attrs: {
                ...(existing?.attrs ?? snapshot.node.attrs),
                nodeVersion: previous.nodeVersion + 1
              }
            })
          }
        }

        if (updatesByPosition.size === 0) return null
        const transaction = newState.tr
          .setMeta(CRDT_NODE_IDENTITY_TRANSACTION_META, true)
          .setMeta('addToHistory', false)
        for (const update of Array.from(updatesByPosition.values()).sort((left, right) => right.position - left.position)) {
          transaction.setNodeMarkup(
            update.position,
            update.node.type,
            update.attrs as Record<string, any>,
            update.node.marks
          )
        }
        return transaction.docChanged ? transaction : null
      }
    })]
  }
})

function readNodeVersionFromAttributes(attributes: Record<string, unknown>): number {
  const value = attributes.nodeVersion
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 ? value : 0
}
