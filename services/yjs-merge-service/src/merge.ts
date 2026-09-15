import * as Y from 'yjs';
import { randomUUID } from 'node:crypto';

export interface YjsMergeRequest {
  baseState: string | null;
  updates: string[];
}

export interface YjsMergeResponse {
  mergedState: string;
}

export interface YjsNodeIdentityMigrationResponse extends YjsMergeResponse {
  changed: boolean;
  registeredNodeCount: number;
}

export class InvalidMergeRequestError extends Error {
  /** 创建可返回给调用方的请求校验异常。 */
  constructor(message: string) {
    super(message);
    this.name = 'InvalidMergeRequestError';
  }
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

/** 使用官方 Yjs 在内存中按顺序应用状态和更新，并返回新的完整状态。 */
export function mergeYjsState(request: YjsMergeRequest): YjsMergeResponse {
  return encodeMergedState(applyRequest(request));
}

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
  };
}

/** 将请求中的基础状态和增量应用到一个新的 Y.Doc。 */
function applyRequest(request: YjsMergeRequest): Y.Doc {
  validateRequest(request);

  const document = new Y.Doc();
  // Java 侧只负责 Base64 传输；正文的解码、应用和重新编码全部由 Yjs 完成。
  if (request.baseState !== null) {
    Y.applyUpdate(document, decodeBase64(request.baseState, 'baseState'));
  }

  for (const [index, update] of request.updates.entries()) {
    Y.applyUpdate(document, decodeBase64(update, `updates[${index}]`));
  }

  return document;
}

/** 将 Y.Doc 编码成当前服务约定的完整 Base64 状态。 */
function encodeMergedState(document: Y.Doc): YjsMergeResponse {
  return {
    mergedState: Buffer.from(Y.encodeStateAsUpdate(document)).toString('base64'),
  };
}

/** 遍历 content 根节点并补齐 v0.7 注册节点的身份属性。 */
function normalizeNodeIdentity(document: Y.Doc): {
  changed: boolean;
  registeredNodeCount: number;
} {
  const content = document.getXmlFragment('content');
  let changed = false;
  let registeredNodeCount = 0;

  for (const candidate of content.createTreeWalker((node) => node instanceof Y.XmlElement)) {
    if (!(candidate instanceof Y.XmlElement) || !REGISTERED_NODE_NAMES.has(candidate.nodeName)) {
      continue;
    }

    registeredNodeCount += 1;
    const node = candidate as IdentityXmlElement;
    const path = nodePath(node);
    if (node.nodeName === 'resourceReference') {
      changed = normalizeResourceReference(node, path) || changed;
    } else {
      changed = normalizeTextNode(node, path) || changed;
    }
  }

  return { changed, registeredNodeCount };
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
function normalizeResourceReference(node: IdentityXmlElement, path: string): boolean {
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

  return changed;
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

/** 校验请求形状，确保基础状态可为空而 updates 始终是字符串数组。 */
function validateRequest(request: YjsMergeRequest): void {
  if (request === null || typeof request !== 'object') {
    throw new InvalidMergeRequestError('request body must be a JSON object');
  }
  if (request.baseState !== null && typeof request.baseState !== 'string') {
    throw new InvalidMergeRequestError('baseState must be a base64 string or null');
  }
  if (!Array.isArray(request.updates) || request.updates.some((update) => typeof update !== 'string')) {
    throw new InvalidMergeRequestError('updates must be an array of base64 strings');
  }
}

/** 校验并解码单个 Base64 字段，拒绝非规范编码。 */
function decodeBase64(value: string, field: string): Uint8Array {
  if (!isCanonicalBase64(value)) {
    throw new InvalidMergeRequestError(`${field} must be valid base64`);
  }

  return new Uint8Array(Buffer.from(value, 'base64'));
}

/** 通过重新编码确认输入 Base64 没有隐藏非法字符或填充差异。 */
function isCanonicalBase64(value: string): boolean {
  if (value.length === 0) {
    return true;
  }
  if (value.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) {
    return false;
  }

  return Buffer.from(value, 'base64').toString('base64') === value;
}
