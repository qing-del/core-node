import * as Y from 'yjs'
import { Fragment, Slice, type Node as ProseMirrorNode } from '@tiptap/pm/model'
import {
  DocumentBindingCommandType,
  DocumentBindingTargetType,
  isDocumentLinkIntent,
  type DocumentLinkIntent
} from '../collaboration/documentProtocol.ts'
import type { ResourceReferenceAttributes } from './ResourceReference.ts'

export const DOCUMENT_RESOURCE_TYPE = 'DOCUMENT'
export const RESOURCE_REFERENCE_NODE_NAME = 'resourceReference'

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

/** 创建一个已经带有固定 kind 的绑定意图，供 Yjs transaction origin 使用。 */
export function createDocumentLinkIntent(
  commandType: DocumentBindingCommandType,
  refId: string,
  targetId: bigint
): DocumentLinkIntent {
  const intent: DocumentLinkIntent = {
    kind: 'document-link-intent',
    schemaVersion: 1,
    commandType,
    refId,
    targetType: DocumentBindingTargetType.DOCUMENT,
    targetId
  }
  if (!isDocumentLinkIntent(intent)) throw new Error('文档 LinkIntent 无效')
  return intent
}

/** 创建 BIND 意图；rebind 也复用该函数并保留传入的 refId。 */
export function createBindIntent(refId: string, documentId: number | bigint): DocumentLinkIntent {
  return createDocumentLinkIntent(
    DocumentBindingCommandType.BIND,
    refId,
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
): DocumentLinkIntent | null {
  if (attributes.resourceType !== DOCUMENT_RESOURCE_TYPE || !attributes.refId) return null
  const targetId = resourceIdToTargetId(attributes.resourceId)
  if (targetId === null) return null
  try {
    return createDocumentLinkIntent(DocumentBindingCommandType.UNBIND, attributes.refId, targetId)
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

/** 创建新 DOCUMENT 引用属性；每次新建都生成新的 refId。 */
export function createDocumentReferenceAttributes(
  documentId: number,
  displayText: string,
  existingRefIds: ReadonlySet<string>,
  generateRefId: () => string = () => crypto.randomUUID()
): ResourceReferenceAttributes {
  const refId = createUniqueRefId(existingRefIds, generateRefId)
  return {
    refId,
    resourceType: DOCUMENT_RESOURCE_TYPE,
    resourceId: documentIdToTargetId(documentId).toString(),
    displayText,
    alias: null
  }
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

/** 收集正文中已经存在的 resourceReference refId。 */
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

/** 为复制/粘贴/导入的每个引用分配全新的 refId，并保留其 target 属性。 */
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
    return node.type.create({ ...node.attrs, refId }, node.content, node.marks)
  })
}

/** 对 ProseMirror 粘贴 Slice 执行 refId 重映射，保留 openStart/openEnd。 */
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
    return node.type.create({ ...node.attrs, refId }, node.content, node.marks)
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

function normalizeRefId(value: string): string {
  return value.toLowerCase()
}

function isNonZeroUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    && normalizeRefId(value).replace(/-/g, '') !== '00000000000000000000000000000000'
}
