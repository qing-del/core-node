import assert from 'node:assert/strict';
import { once } from 'node:events';
import test from 'node:test';

import * as Y from 'yjs';

import { createMergeServer } from '../server.js';

test('POST /internal/yjs/merge returns a merged base64 Yjs state', async () => {
  await withServer(async (baseUrl) => {
    const source = new Y.Doc();
    source.getText('content').insert(0, 'service result');
    const update = Buffer.from(Y.encodeStateAsUpdate(source)).toString('base64');

    const response = await fetch(`${baseUrl}/internal/yjs/merge`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ baseState: null, updates: [update] }),
    });

    assert.equal(response.status, 200);
    const body = await response.json() as { mergedState: string };
    const result = new Y.Doc();
    Y.applyUpdate(result, Buffer.from(body.mergedState, 'base64'));
    assert.equal(result.getText('content').toString(), 'service result');
  });
});

test('POST /internal/yjs/node-identity/migrate fills node identity attributes', async () => {
  await withServer(async (baseUrl) => {
    const source = new Y.Doc();
    source.getXmlFragment('content').insert(0, [new Y.XmlElement('paragraph')]);
    const update = Buffer.from(Y.encodeStateAsUpdate(source)).toString('base64');

    const response = await fetch(`${baseUrl}/internal/yjs/node-identity/migrate`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ baseState: update, updates: [] }),
    });

    assert.equal(response.status, 200);
    const body = await response.json() as {
      mergedState: string;
      changed: boolean;
      registeredNodeCount: number;
    };
    assert.equal(body.changed, true);
    assert.equal(body.registeredNodeCount, 1);
  });
});

test('merge endpoint rejects invalid requests and does not expose extra routes', async () => {
  await withServer(async (baseUrl) => {
    const invalid = await fetch(`${baseUrl}/internal/yjs/merge`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ baseState: null, updates: ['not base64!'] }),
    });
    assert.equal(invalid.status, 400);

    const mismatchSource = new Y.Doc();
    const reference = new Y.XmlElement('resourceReference');
    reference.setAttribute('refId', '550e8400-e29b-41d4-a716-446655440004');
    reference.setAttribute('nodeId', '550e8400-e29b-41d4-a716-446655440005');
    mismatchSource.getXmlFragment('content').insert(0, [reference]);
    const mismatch = await fetch(`${baseUrl}/internal/yjs/node-identity/migrate`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        baseState: Buffer.from(Y.encodeStateAsUpdate(mismatchSource)).toString('base64'),
        updates: [],
      }),
    });
    assert.equal(mismatch.status, 422);

    const unknown = await fetch(`${baseUrl}/health`);
    assert.equal(unknown.status, 404);
  });
});

async function withServer(action: (baseUrl: string) => Promise<void>): Promise<void> {
  const server = createMergeServer();
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');

  const address = server.address();
  assert.ok(address !== null && typeof address !== 'string');

  try {
    await action(`http://127.0.0.1:${address.port}`);
  } finally {
    server.close();
    await once(server, 'close');
  }
}
