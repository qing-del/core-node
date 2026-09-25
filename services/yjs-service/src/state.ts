import * as Y from 'yjs';

export interface YjsMergeRequest {
  baseState: string | null;
  updates: string[];
}

export interface YjsMergeResponse {
  mergedState: string;
}

export class InvalidMergeRequestError extends Error {
  /** 创建可返回给调用方的请求校验异常。 */
  constructor(message: string) {
    super(message);
    this.name = 'InvalidMergeRequestError';
  }
}

/** 将请求中的基础状态和增量应用到一个新的 Y.Doc。 */
export function applyRequest(request: YjsMergeRequest): Y.Doc {
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
export function encodeMergedState(document: Y.Doc): YjsMergeResponse {
  return {
    mergedState: Buffer.from(Y.encodeStateAsUpdate(document)).toString('base64'),
  };
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
