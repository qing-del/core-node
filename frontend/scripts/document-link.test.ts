import assert from 'node:assert/strict'
import test from 'node:test'
import {
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

const EVENT_ID = '550e8400-e29b-41d4-a716-446655440000'
const REF_ID = '123e4567-e89b-12d3-a456-426614174000'

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
