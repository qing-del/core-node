import assert from 'node:assert/strict'
import test from 'node:test'
import * as Y from 'yjs'
import { Schema, Slice } from '@tiptap/pm/model'
import {
  createDocumentWsControl,
  decodeDocumentBindingEnvelope,
  decodeDocumentLinkPayload,
  decodeDocumentWsFrame,
  DocumentBindingCommandType,
  DocumentBindingTargetType,
  DOCUMENT_BINDING_ENVELOPE_BODY_BYTES,
  DOCUMENT_LINK_FIXED_PAYLOAD_BYTES,
  DocumentWsFrameType,
  encodeDocumentBindingEnvelope,
  encodeDocumentLinkPayload,
  encodeDocumentWsFrame,
  parseDocumentWsControl,
  parseDocumentWsLinkAccepted
} from '../src/collaboration/documentProtocol.ts'
import { DocumentCollaborationClient } from '../src/collaboration/DocumentCollaborationClient.ts'
import type { DocumentLinkIntent } from '../src/collaboration/documentProtocol.ts'
import {
  countResourceReferences,
  createBindIntent,
  createDocumentReferenceAttributes,
  createUnbindIntent,
  getResourceReferenceIds,
  remapResourceReferenceIds,
  remapResourceReferenceIdsInSlice,
  runDocumentLinkTransaction
} from '../src/editor/documentResourceLink.ts'

const EVENT_ID = '550e8400-e29b-41d4-a716-446655440000'
const REF_ID = '123e4567-e89b-12d3-a456-426614174000'

type SentData = string | ArrayBuffer

class FakeWebSocket {
  static readonly OPEN = 1
  static readonly instances: FakeWebSocket[] = []

  readonly sent: SentData[] = []
  readonly url: string
  readonly protocols: string[]
  readyState = 0
  binaryType = ''
  onopen: (() => void) | null = null
  onmessage: ((event: MessageEvent<string | ArrayBuffer>) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(url: string, protocols: string[]) {
    this.url = url
    this.protocols = protocols
    FakeWebSocket.instances.push(this)
  }

  send(data: SentData): void {
    this.sent.push(data)
  }

  close(): void {
    this.readyState = 3
    this.onclose?.()
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  receive(data: SentData): void {
    this.onmessage?.({ data } as MessageEvent<string | ArrayBuffer>)
  }
}

class FakeTimerScheduler {
  private nextId = 1
  private readonly tasks = new Map<number, () => void>()

  readonly setTimeout = (callback: () => void): number => {
    const id = this.nextId
    this.nextId += 1
    this.tasks.set(id, callback)
    return id
  }

  readonly clearTimeout = (id: number): void => {
    this.tasks.delete(id)
  }

  runNext(): void {
    const next = this.tasks.keys().next()
    assert.equal(next.done, false)
    const id = next.value as number
    const callback = this.tasks.get(id)
    assert.ok(callback)
    this.tasks.delete(id)
    callback()
  }
}

function installFakeBrowser(): {
  scheduler: FakeTimerScheduler
  restore: () => void
} {
  const globalObject = globalThis as typeof globalThis & {
    WebSocket: typeof WebSocket
    window: Window & typeof globalThis
  }
  const originalWebSocket = globalObject.WebSocket
  const originalWindow = globalObject.window
  const scheduler = new FakeTimerScheduler()
  FakeWebSocket.instances.length = 0
  globalObject.WebSocket = FakeWebSocket as unknown as typeof WebSocket
  globalObject.window = {
    location: { protocol: 'http:', host: 'localhost' },
    setTimeout: scheduler.setTimeout,
    clearTimeout: scheduler.clearTimeout
  } as unknown as Window & typeof globalThis

  return {
    scheduler,
    restore: () => {
      globalObject.WebSocket = originalWebSocket
      globalObject.window = originalWindow
      FakeWebSocket.instances.length = 0
    }
  }
}

function createEnvelope(commandType: DocumentBindingCommandType = DocumentBindingCommandType.BIND) {
  return {
    schemaVersion: 1 as const,
    commandType,
    refId: REF_ID,
    targetType: DocumentBindingTargetType.DOCUMENT,
    targetId: 42n
  }
}

function hex(bytes: Uint8Array): string {
  return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('')
}

test('encodes the v0.6 BindingEnvelope and LINK payload with Java-compatible bytes', () => {
  const rawUpdate = new Uint8Array([0x80, 0x00, 0x01, 0xff])
  const payload = encodeDocumentLinkPayload(createEnvelope(), rawUpdate)

  assert.equal(payload.byteLength, DOCUMENT_LINK_FIXED_PAYLOAD_BYTES + rawUpdate.byteLength)
  assert.equal(hex(payload),
    '0000001b'
    + '0101'
    + '123e4567e89b12d3a456426614174000'
    + '01'
    + '000000000000002a'
    + '800001ff')

  const frame = decodeDocumentWsFrame(encodeDocumentWsFrame(DocumentWsFrameType.LINK, EVENT_ID, payload))
  assert.equal(frame.type, DocumentWsFrameType.LINK)
  assert.equal(frame.eventId, EVENT_ID)
  assert.deepEqual(decodeDocumentLinkPayload(frame.payload), {
    envelope: createEnvelope(),
    rawYjsUpdate: rawUpdate
  })
})

test('round trips BIND and UNBIND envelopes and keeps targetId as bigint', () => {
  for (const commandType of [DocumentBindingCommandType.BIND, DocumentBindingCommandType.UNBIND]) {
    const envelope = createEnvelope(commandType)
    const decoded = decodeDocumentBindingEnvelope(encodeDocumentBindingEnvelope(envelope))
    assert.deepEqual(decoded, envelope)
    assert.equal(decoded.targetId, 42n)
  }
  assert.equal(DOCUMENT_BINDING_ENVELOPE_BODY_BYTES, 27)
})

test('rejects malformed LINK payloads and invalid binding fields', () => {
  const valid = encodeDocumentLinkPayload(createEnvelope(), new Uint8Array([7]))

  const wrongLength = valid.slice()
  new DataView(wrongLength.buffer).setUint32(0, 26, false)
  assert.throws(() => decodeDocumentLinkPayload(wrongLength), /envelope length/)

  const wrongSchema = valid.slice()
  wrongSchema[4] = 2
  assert.throws(() => decodeDocumentLinkPayload(wrongSchema), /schema/)

  const zeroRef = createEnvelope()
  zeroRef.refId = '00000000-0000-0000-0000-000000000000'
  assert.throws(() => encodeDocumentBindingEnvelope(zeroRef), /refId/)

  const invalidTarget = createEnvelope()
  invalidTarget.targetId = 0n
  assert.throws(() => encodeDocumentBindingEnvelope(invalidTarget), /targetId/)

  assert.throws(() => encodeDocumentLinkPayload(createEnvelope(), new Uint8Array()), /raw Yjs/)
  assert.throws(() => decodeDocumentLinkPayload(valid.slice(0, -1)), /长度无效/)
})

test('strictly parses LINK_ACCEPTED and checks the current document', () => {
  const control = parseDocumentWsControl(JSON.stringify({
    protocolVersion: 1,
    type: 'LINK_ACCEPTED',
    requestId: EVENT_ID,
    documentId: 42,
    clientUpdateId: EVENT_ID,
    updatesRedisOpId: '1756080000000-0',
    bindingRedisOpId: '1756080000001-0',
    status: 'QUEUED'
  }))
  const accepted = parseDocumentWsLinkAccepted(control, 42)
  assert.equal(accepted.type, 'LINK_ACCEPTED')
  assert.equal(accepted.status, 'QUEUED')
  assert.equal(accepted.bindingRedisOpId, '1756080000001-0')
  assert.throws(() => parseDocumentWsLinkAccepted(control, 43), /不匹配/)

  const invalidStatus = { ...control, status: 'PERSISTED' }
  assert.throws(() => parseDocumentWsLinkAccepted(invalidStatus), /QUEUED/)
})

test('routes a LinkIntent-origin Yjs transaction through a LINK frame', () => {
  const environment = installFakeBrowser()
  const ydoc = new Y.Doc()
  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc,
    canWrite: true
  })
  const intent: DocumentLinkIntent = {
    kind: 'document-link-intent',
    ...createEnvelope()
  }

  try {
    client.connect()
    const socket = FakeWebSocket.instances[0]
    assert.ok(socket)
    socket.open()
    socket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))

    ydoc.transact(() => {
      ydoc.getMap('content').set('linked', true)
    }, intent)

    const frame = findFrame(socket, DocumentWsFrameType.LINK)
    const decoded = decodeDocumentLinkPayload(frame.payload)
    assert.deepEqual(decoded.envelope, createEnvelope())
    assert.ok(decoded.rawYjsUpdate.byteLength > 0)
    assert.equal(countFrames(socket, DocumentWsFrameType.CLIENT_UPDATE), 0)
  } finally {
    client.dispose()
    environment.restore()
  }
})

test('replays a pending LINK unchanged after reconnect and clears it on LINK_ACCEPTED', () => {
  const environment = installFakeBrowser()
  const { scheduler } = environment
  const ydoc = new Y.Doc()
  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc,
    canWrite: true
  })

  try {
    client.connect()
    const firstSocket = FakeWebSocket.instances[0]
    assert.ok(firstSocket)
    firstSocket.open()
    firstSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))

    const intent: DocumentLinkIntent = {
      kind: 'document-link-intent',
      ...createEnvelope(DocumentBindingCommandType.UNBIND)
    }
    ydoc.transact(() => {
      ydoc.getMap('content').set('unbound', true)
    }, intent)
    const original = findFrame(firstSocket, DocumentWsFrameType.LINK)

    firstSocket.onclose?.()
    scheduler.runNext()
    const secondSocket = FakeWebSocket.instances[1]
    assert.ok(secondSocket)
    secondSocket.open()
    secondSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    const replayed = findFrame(secondSocket, DocumentWsFrameType.LINK)
    assert.equal(replayed.eventId, original.eventId)
    assert.deepEqual(replayed.payload, original.payload)

    secondSocket.receive(JSON.stringify(createDocumentWsControl('LINK_ACCEPTED', {
      documentId: 42,
      requestId: original.eventId,
      clientUpdateId: original.eventId,
      updatesRedisOpId: '1756080000000-0',
      bindingRedisOpId: '1756080000001-0',
      status: 'QUEUED'
    })))

    secondSocket.onclose?.()
    scheduler.runNext()
    const thirdSocket = FakeWebSocket.instances[2]
    assert.ok(thirdSocket)
    thirdSocket.open()
    thirdSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    assert.equal(countFrames(thirdSocket, DocumentWsFrameType.LINK), 0)
  } finally {
    client.dispose()
    environment.restore()
  }
})

test('retains pending LINK while read-only and resumes it when write access returns', () => {
  const environment = installFakeBrowser()
  const { scheduler } = environment
  const ydoc = new Y.Doc()
  const client = new DocumentCollaborationClient({
    documentId: 42,
    accessToken: 'test-token',
    ydoc,
    canWrite: true
  })

  try {
    client.connect()
    const firstSocket = FakeWebSocket.instances[0]
    assert.ok(firstSocket)
    firstSocket.open()
    firstSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))

    const intent: DocumentLinkIntent = {
      kind: 'document-link-intent',
      ...createEnvelope()
    }
    ydoc.transact(() => {
      ydoc.getMap('content').set('paused', true)
    }, intent)
    const original = findFrame(firstSocket, DocumentWsFrameType.LINK)

    client.setWriteEnabled(false)
    firstSocket.onclose?.()
    scheduler.runNext()
    const secondSocket = FakeWebSocket.instances[1]
    assert.ok(secondSocket)
    secondSocket.open()
    secondSocket.receive(JSON.stringify(createDocumentWsControl('SYNC_COMPLETE', { documentId: 42 })))
    assert.equal(countFrames(secondSocket, DocumentWsFrameType.LINK), 0)

    client.setWriteEnabled(true)
    const replayed = findFrame(secondSocket, DocumentWsFrameType.LINK)
    assert.equal(replayed.eventId, original.eventId)
    assert.deepEqual(replayed.payload, original.payload)
  } finally {
    client.dispose()
    environment.restore()
  }
})

function findFrame(socket: FakeWebSocket, type: DocumentWsFrameType) {
  const frame = socket.sent
    .filter((data): data is ArrayBuffer => data instanceof ArrayBuffer)
    .map(data => decodeDocumentWsFrame(data))
    .find(value => value.type === type)
  assert.ok(frame)
  return frame
}

function countFrames(socket: FakeWebSocket, type: DocumentWsFrameType): number {
  return socket.sent
    .filter((data): data is ArrayBuffer => data instanceof ArrayBuffer)
    .map(data => decodeDocumentWsFrame(data))
    .filter(value => value.type === type)
    .length
}

function createTestSchema(): Schema {
  return new Schema({
    nodes: {
      doc: { content: 'block+' },
      paragraph: { content: 'inline*', group: 'block' },
      text: { group: 'inline' },
      resourceReference: {
        inline: true,
        group: 'inline',
        atom: true,
        attrs: {
          refId: { default: null },
          resourceType: { default: null },
          resourceId: { default: null },
          displayText: { default: '' },
          alias: { default: null }
        }
      }
    }
  })
}

test('creates DOCUMENT reference attributes and preserves the same refId for rebind', () => {
  const attributes = createDocumentReferenceAttributes(
    42,
    '目标文档',
    new Set([REF_ID]),
    () => '123e4567-e89b-12d3-a456-426614174001'
  )
  assert.deepEqual(attributes, {
    refId: '123e4567-e89b-12d3-a456-426614174001',
    resourceType: 'DOCUMENT',
    resourceId: '42',
    displayText: '目标文档',
    alias: null
  })

  const bind = createBindIntent(REF_ID, 42)
  assert.equal(bind.refId, REF_ID)
  assert.equal(bind.targetId, 42n)
  assert.equal(bind.commandType, DocumentBindingCommandType.BIND)
})

test('creates UNBIND only for complete DOCUMENT references and handles legacy placeholders', () => {
  const unbind = createUnbindIntent({
    refId: REF_ID,
    resourceType: 'DOCUMENT',
    resourceId: '42'
  })
  assert.ok(unbind)
  assert.equal(unbind.commandType, DocumentBindingCommandType.UNBIND)
  assert.equal(unbind.targetId, 42n)

  assert.equal(createUnbindIntent({
    refId: REF_ID,
    resourceType: null,
    resourceId: null
  }), null)
  assert.equal(createUnbindIntent({
    refId: REF_ID,
    resourceType: 'DOCUMENT',
    resourceId: 'not-a-number'
  }), null)
})

test('remaps every pasted resource refId without mutating the source document', () => {
  const schema = createTestSchema()
  const reference = schema.node('resourceReference', {
    refId: REF_ID,
    resourceType: 'DOCUMENT',
    resourceId: '42',
    displayText: '目标文档',
    alias: null
  })
  const source = schema.node('doc', null, [schema.node('paragraph', null, [
    schema.text('前'), reference, schema.text('后')
  ])])
  const nextRefId = '123e4567-e89b-12d3-a456-426614174001'
  const remapped = remapResourceReferenceIds(source, getResourceReferenceIds(source), () => nextRefId)

  assert.equal(getResourceReferenceIds(source).has(REF_ID), true)
  assert.equal(getResourceReferenceIds(remapped).has(REF_ID), false)
  assert.equal(getResourceReferenceIds(remapped).has(nextRefId), true)
  assert.equal(countResourceReferences(remapped), 1)

  const slice = new Slice(source.firstChild!.content, 0, 0)
  const remappedSlice = remapResourceReferenceIdsInSlice(
    slice,
    new Set([nextRefId]),
    () => '123e4567-e89b-12d3-a456-426614174002'
  )
  const remappedParagraph = schema.node('paragraph', null, remappedSlice.content)
  assert.equal(countResourceReferences(remappedParagraph), 1)
})

test('keeps LinkIntent as the Yjs transaction origin for exactly one editor action', () => {
  const ydoc = new Y.Doc()
  const intent = createBindIntent(REF_ID, 42)
  const origins: unknown[] = []
  ydoc.on('update', (_update, origin) => origins.push(origin))

  const result = runDocumentLinkTransaction(ydoc, intent, () => {
    ydoc.getMap('content').set('resourceReference', 'created')
    return true
  })

  assert.equal(result, true)
  assert.deepEqual(origins, [intent])
})
