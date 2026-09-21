import assert from 'node:assert/strict';
import test from 'node:test';

import * as Y from 'yjs';

import {
  InvalidMergeRequestError,
  migrateYjsNodeIdentity,
  NodeIdentityMigrationError,
  mergeYjsState,
} from '../merge.js';

test('duplicate updates merge to the same Yjs document state', () => {
  const source = new Y.Doc();
  source.getText('content').insert(0, 'duplicate safe');
  const update = Y.encodeStateAsUpdate(source);

  const result = mergeYjsState({
    baseState: null,
    updates: [toBase64(update), toBase64(update)],
  });

  assert.equal(readText(result.mergedState), 'duplicate safe');
});

test('out-of-order updates converge after Yjs applies pending structs', () => {
  const source = new Y.Doc();
  const baseVector = Y.encodeStateVector(source);
  source.getText('content').insert(0, 'first');
  const firstUpdate = Y.encodeStateAsUpdate(source, baseVector);
  const firstVector = Y.encodeStateVector(source);
  source.getText('content').insert(5, ' second');
  const secondUpdate = Y.encodeStateAsUpdate(source, firstVector);

  const result = mergeYjsState({
    baseState: null,
    updates: [toBase64(secondUpdate), toBase64(firstUpdate)],
  });

  assert.equal(readText(result.mergedState), 'first second');
});

test('base snapshot and incremental updates restore the latest document state', () => {
  const source = new Y.Doc();
  source.getText('content').insert(0, 'base');
  const baseState = Y.encodeStateAsUpdate(source);
  const baseVector = Y.encodeStateVector(source);
  source.getText('content').insert(4, ' + update');
  const incrementalUpdate = Y.encodeStateAsUpdate(source, baseVector);

  const result = mergeYjsState({
    baseState: toBase64(baseState),
    updates: [toBase64(incrementalUpdate)],
  });

  assert.equal(readText(result.mergedState), 'base + update');
});

test('independent concurrent client edits converge regardless of flush order', () => {
  const initial = new Y.Doc();
  initial.getText('content').insert(0, 'base');
  const baseState = Y.encodeStateAsUpdate(initial);

  const clientA = new Y.Doc();
  const clientB = new Y.Doc();
  Y.applyUpdate(clientA, baseState);
  Y.applyUpdate(clientB, baseState);
  const baseVector = Y.encodeStateVector(initial);

  clientA.getText('content').insert(4, ' from A');
  clientB.getText('content').insert(4, ' from B');
  const updateA = Y.encodeStateAsUpdate(clientA, baseVector);
  const updateB = Y.encodeStateAsUpdate(clientB, baseVector);

  const flushedAB = mergeYjsState({
    baseState: toBase64(baseState),
    updates: [toBase64(updateA), toBase64(updateB)],
  });
  const flushedBA = mergeYjsState({
    baseState: toBase64(baseState),
    updates: [toBase64(updateB), toBase64(updateA)],
  });

  assert.equal(readText(flushedAB.mergedState), readText(flushedBA.mergedState));
  assert.match(readText(flushedAB.mergedState), /^base/);
  assert.match(readText(flushedAB.mergedState), / from A/);
  assert.match(readText(flushedAB.mergedState), / from B/);
});

test('invalid base64 is rejected before it reaches Yjs', () => {
  assert.throws(
    () => mergeYjsState({ baseState: 'not base64!', updates: [] }),
    InvalidMergeRequestError,
  );
});

test('node identity migration fills registered nodes and preserves unregistered nodes', () => {
  const source = new Y.Doc();
  const content = source.getXmlFragment('content');
  const paragraph = new Y.XmlElement('paragraph');
  paragraph.insert(0, [new Y.XmlText('hello')]);
  const heading = new Y.XmlElement('heading');
  heading.setAttribute('nodeId', '550e8400-e29b-41d4-a716-446655440000');
  setNodeVersion(heading, 3);
  const list = new Y.XmlElement('bulletList');
  list.setAttribute('nodeId', 'not-registered');
  content.insert(0, [paragraph, heading, list]);

  const result = migrateYjsNodeIdentity({
    baseState: toBase64(Y.encodeStateAsUpdate(source)),
    updates: [],
  });

  const migrated = readXml(result.mergedState);
  const migratedParagraph = migrated.getXmlFragment('content').firstChild as Y.XmlElement;
  const migratedHeading = migratedParagraph.nextSibling as Y.XmlElement;
  const migratedList = migratedHeading.nextSibling as Y.XmlElement;
  assert.equal(result.changed, true);
  assert.equal(result.registeredNodeCount, 2);
  assert.match(String(migratedParagraph.getAttribute('nodeId')), UUID_PATTERN);
  assert.equal(migratedParagraph.getAttribute('nodeVersion'), 0);
  assert.equal(migratedHeading.getAttribute('nodeId'), '550e8400-e29b-41d4-a716-446655440000');
  assert.equal(migratedHeading.getAttribute('nodeVersion'), 3);
  assert.equal(migratedList.getAttribute('nodeId'), 'not-registered');
});

test('resource references use refId and receive the same initial version contract', () => {
  const source = new Y.Doc();
  const reference = new Y.XmlElement('resourceReference');
  reference.setAttribute('refId', '550e8400-e29b-41d4-a716-446655440001');
  source.getXmlFragment('content').insert(0, [reference]);

  const result = migrateYjsNodeIdentity({
    baseState: toBase64(Y.encodeStateAsUpdate(source)),
    updates: [],
  });
  const migrated = readXml(result.mergedState).getXmlFragment('content').firstChild as Y.XmlElement;

  assert.equal(migrated.getAttribute('nodeId'), '550e8400-e29b-41d4-a716-446655440001');
  assert.equal(migrated.getAttribute('nodeVersion'), 0);
});

test('node identity migration is idempotent', () => {
  const source = new Y.Doc();
  const paragraph = new Y.XmlElement('paragraph');
  source.getXmlFragment('content').insert(0, [paragraph]);
  const first = migrateYjsNodeIdentity({
    baseState: toBase64(Y.encodeStateAsUpdate(source)),
    updates: [],
  });
  const second = migrateYjsNodeIdentity({ baseState: first.mergedState, updates: [] });

  assert.equal(first.changed, true);
  assert.equal(second.changed, false);
  assert.equal(second.mergedState, first.mergedState);
});

test('resource reference identity mismatch fails without silent overwrite', () => {
  const source = new Y.Doc();
  const reference = new Y.XmlElement('resourceReference');
  reference.setAttribute('refId', '550e8400-e29b-41d4-a716-446655440002');
  reference.setAttribute('nodeId', '550e8400-e29b-41d4-a716-446655440003');
  source.getXmlFragment('content').insert(0, [reference]);

  assert.throws(
    () => migrateYjsNodeIdentity({ baseState: toBase64(Y.encodeStateAsUpdate(source)), updates: [] }),
    NodeIdentityMigrationError,
  );
});

test('resource reference identity accepts UUIDs that differ only by case', () => {
  const source = new Y.Doc();
  const reference = new Y.XmlElement('resourceReference');
  reference.setAttribute('refId', '550E8400-E29B-41D4-A716-446655440004');
  reference.setAttribute('nodeId', '550e8400-e29b-41d4-a716-446655440004');
  setNodeVersion(reference, 2);
  source.getXmlFragment('content').insert(0, [reference]);

  const result = migrateYjsNodeIdentity({
    baseState: toBase64(Y.encodeStateAsUpdate(source)),
    updates: [],
  });

  assert.equal(result.changed, false);
  assert.equal(result.registeredNodeCount, 1);
});

test('duplicate resource references receive a paired identity remap', () => {
  const source = new Y.Doc();
  const first = resourceReference('550e8400-e29b-41d4-a716-446655440005');
  const duplicate = resourceReference('550e8400-e29b-41d4-a716-446655440005');
  duplicate.removeAttribute('nodeVersion');
  source.getXmlFragment('content').insert(0, [first, duplicate]);

  const result = migrateYjsNodeIdentity({
    baseState: toBase64(Y.encodeStateAsUpdate(source)),
    updates: [],
  });
  const content = readXml(result.mergedState).getXmlFragment('content');
  const migratedDuplicate = content.get(1) as Y.XmlElement;

  assert.equal(result.changed, true);
  assert.deepEqual(result.resourceReferenceRemaps.map(remap => remap.previousRefId),
    ['550e8400-e29b-41d4-a716-446655440005']);
  assert.match(result.resourceReferenceRemaps[0].refId, UUID_PATTERN);
  assert.equal(migratedDuplicate.getAttribute('nodeId'), result.resourceReferenceRemaps[0].refId);
  assert.equal(migratedDuplicate.getAttribute('refId'), result.resourceReferenceRemaps[0].refId);
  assert.equal(migratedDuplicate.getAttribute('nodeVersion'), 0);
});

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function toBase64(update: Uint8Array): string {
  return Buffer.from(update).toString('base64');
}

function readText(mergedState: string): string {
  const document = new Y.Doc();
  Y.applyUpdate(document, Buffer.from(mergedState, 'base64'));
  return document.getText('content').toString();
}

function readXml(mergedState: string): Y.Doc {
  const document = new Y.Doc();
  Y.applyUpdate(document, Buffer.from(mergedState, 'base64'));
  return document;
}

function setNodeVersion(node: Y.XmlElement, value: number): void {
  (node as unknown as Y.XmlElement<{ [key: string]: string | number | null }>)
    .setAttribute('nodeVersion', value);
}

function resourceReference(refId: string): Y.XmlElement {
  const reference = new Y.XmlElement('resourceReference');
  reference.setAttribute('nodeId', refId);
  reference.setAttribute('refId', refId);
  setNodeVersion(reference, 0);
  return reference;
}
