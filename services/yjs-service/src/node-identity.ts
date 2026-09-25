import * as Y from 'yjs';
import { randomUUID } from 'node:crypto';

import { applyRequest, encodeMergedState, type YjsMergeRequest, type YjsMergeResponse } from './state.js';

export interface YjsNodeIdentityMigrationResponse extends YjsMergeResponse {
  changed: boolean;
  registeredNodeCount: number;
  resourceReferenceRemaps: ResourceReferenceIdentityRemap[];
}

/** 重复资源引用迁移后的旧、新 refId 对；二者均为规范化小写 UUID。 */
export interface ResourceReferenceIdentityRemap {
  previousRefId: string;
  refId: string;
}

export class NodeIdentityMigrationError extends Error {
  /** 创建可返回给调用方的节点身份迁移异常。 */
  constructor(message: string) {
    super(message);
    this.name = 'NodeIdentityMigrationError';
  }
}

type XmlAttributeValue = string | number | null;
type IdentityXmlElement = Y.XmlElement<{ [key: string]: XmlAttributeValue }>;

/** 合并状态后仅补齐注册节点身份属性，不改变已有值或非注册节点。 */
export function migrateYjsNodeIdentity(
  request: YjsMergeRequest,
): YjsNodeIdentityMigrationResponse {
  const document = applyRequest(request);
  const migration = normalizeNodeIdentity(document);

  return {
    ...encodeMergedState(document),
    changed: migration.changed,
    registeredNodeCount: migration.registeredNodeCount,
    resourceReferenceRemaps: migration.resourceReferenceRemaps,
  };
}

/** 遍历 content 根节点并补齐 v0.7 注册节点的身份属性。 */
function normalizeNodeIdentity(document: Y.Doc): {
  changed: boolean;
  registeredNodeCount: number;
  resourceReferenceRemaps: ResourceReferenceIdentityRemap[];
} {
  const content = document.getXmlFragment('content');
  let changed = false;
  let registeredNodeCount = 0;
  const usedNodeIds = new Set<string>();
  const resourceReferenceRemaps: ResourceReferenceIdentityRemap[] = [];

  for (const candidate of content.createTreeWalker((node) => node instanceof Y.XmlElement)) {
    if (!(candidate instanceof Y.XmlElement) || !REGISTERED_NODE_NAMES.has(candidate.nodeName)) {
      continue;
    }

    registeredNodeCount += 1;
    const node = candidate as IdentityXmlElement;
    const path = nodePath(node);
    if (node.nodeName === 'resourceReference') {
      const normalization = normalizeResourceReference(node, path, usedNodeIds);
      changed = normalization.changed || changed;
      if (normalization.remap) resourceReferenceRemaps.push(normalization.remap);
    } else {
      changed = normalizeTextNode(node, path) || changed;
      const nodeId = node.getAttribute('nodeId');
      if (typeof nodeId === 'string') usedNodeIds.add(normalizeNodeId(nodeId));
    }
  }

  return { changed, registeredNodeCount, resourceReferenceRemaps };
}

/** v0.7 首发注册的纯文本节点与资源引用节点。 */
const REGISTERED_NODE_NAMES = new Set([
  'paragraph',
  'heading',
  'listItem',
  'blockquote',
  'codeBlock',
  'resourceReference',
]);

/** 补齐普通纯文本节点的 UUID 和数值版本。 */
function normalizeTextNode(node: IdentityXmlElement, path: string): boolean {
  let changed = false;
  const nodeId = node.getAttribute('nodeId');
  if (isMissing(nodeId)) {
    node.setAttribute('nodeId', randomUUID());
    changed = true;
  } else {
    validateNodeId(nodeId, path);
  }

  const nodeVersion = node.getAttribute('nodeVersion');
  if (isMissing(nodeVersion)) {
    node.setAttribute('nodeVersion', 0);
    changed = true;
  } else {
    validateNodeVersion(nodeVersion, path);
  }

  return changed;
}

/** 按 refId 补齐资源引用身份，并沿用 nodeVersion 语义。 */
function normalizeResourceReference(node: IdentityXmlElement, path: string, usedNodeIds: Set<string>): {
  changed: boolean;
  remap: ResourceReferenceIdentityRemap | null;
} {
  const refId = node.getAttribute('refId');
  if (typeof refId !== 'string' || refId.trim() === '') {
    throw new NodeIdentityMigrationError(`${path}: resourceReference.refId is required`);
  }
  validateNodeId(refId, `${path}.refId`);

  let changed = false;
  const nodeId = node.getAttribute('nodeId');
  if (isMissing(nodeId)) {
    node.setAttribute('nodeId', refId);
    changed = true;
  } else {
    validateNodeId(nodeId, path);
    if (nodeId.toLowerCase() !== refId.toLowerCase()) {
      throw new NodeIdentityMigrationError(`${path}: nodeId must equal refId`);
    }
  }

  const nodeVersion = node.getAttribute('nodeVersion');
  if (isMissing(nodeVersion)) {
    node.setAttribute('nodeVersion', 0);
    changed = true;
  } else {
    validateNodeVersion(nodeVersion, path);
  }

  const normalizedRefId = normalizeNodeId(refId);
  if (usedNodeIds.has(normalizedRefId)) {
    const replacementRefId = createUniqueNodeId(usedNodeIds);
    node.setAttribute('nodeId', replacementRefId);
    node.setAttribute('refId', replacementRefId);
    changed = true;
    usedNodeIds.add(replacementRefId);
    return {
      changed,
      remap: { previousRefId: normalizedRefId, refId: replacementRefId },
    };
  }
  usedNodeIds.add(normalizedRefId);

  return { changed, remap: null };
}

/** 校验已有节点身份；已有非法值不被迁移任务静默覆盖。 */
function validateNodeId(value: unknown, path: string): asserts value is string {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    throw new NodeIdentityMigrationError(`${path}: nodeId must be a UUID`);
  }
}

/** 校验已有节点版本必须是非负安全整数。 */
function validateNodeVersion(value: unknown, path: string): asserts value is number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) {
    throw new NodeIdentityMigrationError(`${path}: nodeVersion must be a non-negative integer`);
  }
}

/** 迁移阶段把 null、undefined 和空字符串视为缺失属性。 */
function isMissing(value: unknown): value is null | undefined | '' {
  return value === null || value === undefined || value === '';
}

/** UUID v4/v1 等标准 UUID 字符串校验。 */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/** 统一 UUID 大小写，使 Yjs 属性与 Java UUID/关系表的键语义一致。 */
function normalizeNodeId(value: string): string {
  return value.toLowerCase();
}

/** 生成未被本次正文遍历占用的新 UUID。 */
function createUniqueNodeId(usedNodeIds: ReadonlySet<string>): string {
  let nodeId = randomUUID();
  while (usedNodeIds.has(normalizeNodeId(nodeId))) {
    nodeId = randomUUID();
  }
  return nodeId;
}

/** 返回当前节点在 content 树中的稳定可读路径，便于结构化错误定位。 */
function nodePath(node: IdentityXmlElement): string {
  const ancestors: string[] = [node.nodeName];
  let current = node.parent;
  while (current instanceof Y.XmlElement) {
    ancestors.unshift(current.nodeName);
    current = current.parent;
  }
  return `content/${ancestors.join('/')}`;
}
