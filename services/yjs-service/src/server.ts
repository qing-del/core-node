import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

import { mergeYjsState } from './merge.js';
import { migrateYjsNodeIdentity, NodeIdentityMigrationError } from './node-identity.js';
import { InvalidMergeRequestError, type YjsMergeRequest } from './state.js';

const MERGE_PATH = '/internal/yjs/merge';
const NODE_IDENTITY_MIGRATION_PATH = '/internal/yjs/node-identity/migrate';
const MAX_BODY_BYTES = 16 * 1024 * 1024;

/** 创建提供内部 Yjs 状态操作接口的无状态 HTTP 服务。 */
export function createYjsServer(): Server {
  return createServer(async (request, response) => {
    try {
      await handleRequest(request, response);
    } catch (error) {
      writeJson(response, 500, { error: 'internal merge service error' });
      console.error('Unexpected Yjs service error', error);
    }
  });
}

/** 只接受固定 POST 路径，并将请求校验错误转换为 400。 */
async function handleRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (request.method !== 'POST'
    || (request.url !== MERGE_PATH && request.url !== NODE_IDENTITY_MIGRATION_PATH)) {
    writeJson(response, 404, { error: 'not found' });
    return;
  }

  try {
    const body = await readJsonBody(request);
    const result = request.url === NODE_IDENTITY_MIGRATION_PATH
      ? migrateYjsNodeIdentity(body)
      : mergeYjsState(body);
    writeJson(response, 200, result);
  } catch (error) {
    if (error instanceof InvalidMergeRequestError) {
      writeJson(response, 400, { error: error.message });
      return;
    }
    if (error instanceof NodeIdentityMigrationError) {
      writeJson(response, 422, { error: error.message });
      return;
    }
    throw error;
  }
}

/** 分块读取 JSON 请求并在累积超过上限前中止。 */
async function readJsonBody(request: IncomingMessage): Promise<YjsMergeRequest> {
  const chunks: Buffer[] = [];
  let receivedBytes = 0;

  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    receivedBytes += buffer.length;
    if (receivedBytes > MAX_BODY_BYTES) {
      throw new InvalidMergeRequestError(`request body exceeds ${MAX_BODY_BYTES} bytes`);
    }
    chunks.push(buffer);
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as YjsMergeRequest;
  } catch {
    throw new InvalidMergeRequestError('request body must be valid JSON');
  }
}

/** 统一设置 JSON 响应头并写出服务结果。 */
function writeJson(response: ServerResponse, statusCode: number, body: unknown): void {
  response.writeHead(statusCode, { 'content-type': 'application/json; charset=utf-8' });
  response.end(JSON.stringify(body));
}

// 直接运行该模块时启动监听；被测试导入时只暴露 createYjsServer。
if (process.argv[1] !== undefined && import.meta.url === new URL(`file://${process.argv[1]}`).href) {
  const server = createYjsServer();
  server.listen(3100, '0.0.0.0', () => {
    console.info('yjs-service listening on port 3100');
  });
}
