import { applyRequest, encodeMergedState, type YjsMergeRequest, type YjsMergeResponse } from './state.js';

/** 使用官方 Yjs 在内存中按顺序应用状态和更新，并返回新的完整状态。 */
export function mergeYjsState(request: YjsMergeRequest): YjsMergeResponse {
  return encodeMergedState(applyRequest(request));
}
