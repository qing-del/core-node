import assert from 'node:assert/strict'
import test from 'node:test'
import { ySyncPluginKey } from '@tiptap/y-tiptap'
import { Fragment, Schema, Slice } from '@tiptap/pm/model'
import { EditorState } from '@tiptap/pm/state'
import {
  CRDT_REGISTERED_NODE_NAMES,
  CrdtNodeIdentity,
  getCrdtNodeIds,
  hasResourceReferenceIdentityMismatch,
  isValidCrdtNodeId,
  remapCrdtNodeIdentities,
  remapCrdtNodeIdentitiesInSlice
} from '../src/editor/crdtNodeIdentity.ts'

const PARAGRAPH_ID = '123e4567-e89b-12d3-a456-426614174010'
const HEADING_ID = '123e4567-e89b-12d3-a456-426614174011'
const REFERENCE_ID = '123e4567-e89b-12d3-a456-426614174012'

const identityAttrs = {
  nodeId: { default: null },
  nodeVersion: { default: 0 }
}

function createSchema(): Schema {
  return new Schema({
    nodes: {
      doc: { content: 'block+' },
      paragraph: { content: 'inline*', group: 'block', attrs: identityAttrs },
      heading: { content: 'inline*', group: 'block', attrs: { ...identityAttrs, level: { default: 1 } } },
      listItem: { content: 'block+', group: 'block', attrs: identityAttrs },
      blockquote: { content: 'block+', group: 'block', attrs: identityAttrs },
      codeBlock: { content: 'text*', group: 'block', attrs: { ...identityAttrs, language: { default: null } } },
      text: { group: 'inline' },
      resourceReference: {
        inline: true,
        group: 'inline',
        atom: true,
        attrs: {
          ...identityAttrs,
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

function createIdentityPlugin(generateNodeId: () => string) {
  const extension = CrdtNodeIdentity.configure({ generateNodeId })
  return extension.config.addProseMirrorPlugins!.call(extension)[0]
}

function apply(state: EditorState, transaction: ReturnType<EditorState['tr']['insertText']>): EditorState {
  return state.applyTransaction(transaction).state
}

function resourceReference(schema: Schema, nodeId = REFERENCE_ID, refId = nodeId) {
  return schema.node('resourceReference', {
    nodeId,
    nodeVersion: 0,
    refId,
    resourceType: 'NOTE',
    resourceId: '42',
    displayText: '笔记',
    alias: null
  })
}

test('normalizes all six registered nodes and keeps resourceReference identity equal', () => {
  const schema = createSchema()
  const generated = [
    '123e4567-e89b-12d3-a456-426614174020',
    '123e4567-e89b-12d3-a456-426614174021',
    '123e4567-e89b-12d3-a456-426614174022',
    '123e4567-e89b-12d3-a456-426614174023',
    '123e4567-e89b-12d3-a456-426614174024',
    '123e4567-e89b-12d3-a456-426614174025',
    '123e4567-e89b-12d3-a456-426614174026',
    '123e4567-e89b-12d3-a456-426614174027'
  ]
  const reference = resourceReference(schema, '', REFERENCE_ID)
  const doc = schema.node('doc', null, [
    schema.node('paragraph', null, [schema.text('paragraph')]),
    schema.node('heading', { level: 2 }, [schema.text('heading')]),
    schema.node('listItem', null, [schema.node('paragraph', null, [schema.text('item')])]),
    schema.node('blockquote', null, [schema.node('paragraph', null, [schema.text('quote')])]),
    schema.node('codeBlock', { language: null }, [schema.text('code')]),
    schema.node('paragraph', null, [reference])
  ])
  let state = EditorState.create({ schema, doc, plugins: [createIdentityPlugin(() => generated.shift()!)] })

  state = apply(state, state.tr.insertText('!', 2))
  const identities = new Map<string, { nodeId: unknown; nodeVersion: unknown; refId?: unknown }>()
  state.doc.descendants(node => {
    if (CRDT_REGISTERED_NODE_NAMES.includes(node.type.name as typeof CRDT_REGISTERED_NODE_NAMES[number])) {
      identities.set(node.type.name + identities.size, {
        nodeId: node.attrs.nodeId,
        nodeVersion: node.attrs.nodeVersion,
        refId: node.type.name === 'resourceReference' ? node.attrs.refId : undefined
      })
    }
    return true
  })

  assert.equal(identities.size, 9)
  for (const identity of identities.values()) {
    assert.equal(isValidCrdtNodeId(identity.nodeId), true)
    assert.equal(identity.nodeVersion, 0)
  }
  const references = [] as Array<{ nodeId: string; refId: string }>
  state.doc.descendants(node => {
    if (node.type.name === 'resourceReference') references.push(node.attrs)
    return true
  })
  assert.deepEqual(references.map(value => ({ ...value })), [{
    nodeId: REFERENCE_ID,
    nodeVersion: 0,
    refId: REFERENCE_ID,
    resourceType: 'NOTE',
    resourceId: '42',
    displayText: '笔记',
    alias: null
  }])
})

test('increments only the changed node and only once per local transaction', () => {
  const schema = createSchema()
  const doc = schema.node('doc', null, [
    schema.node('heading', { nodeId: HEADING_ID, nodeVersion: 0, level: 1 }, [schema.text('标题')]),
    schema.node('paragraph', { nodeId: PARAGRAPH_ID, nodeVersion: 0 }, [schema.text('正文'), resourceReference(schema)])
  ])
  let state = EditorState.create({ schema, doc, plugins: [createIdentityPlugin(() => crypto.randomUUID())] })

  const heading = state.doc.child(0)
  const transaction = state.tr
    .insertText('!', 2)
    .setNodeMarkup(0, undefined, { ...heading.attrs, level: 2 })
  state = apply(state, transaction)

  assert.equal(state.doc.child(0).attrs.nodeVersion, 1)
  assert.equal(state.doc.child(1).attrs.nodeVersion, 0)

  const paragraphPosition = state.doc.child(0).nodeSize
  const referencePosition = paragraphPosition + 1 + '正文'.length
  const reference = state.doc.nodeAt(referencePosition)
  assert.ok(reference)
  state = apply(state, state.tr.setNodeMarkup(referencePosition, undefined, {
    ...reference.attrs,
    displayText: '更新后的笔记'
  }))
  assert.equal(state.doc.child(1).attrs.nodeVersion, 0)
  assert.equal(state.doc.nodeAt(referencePosition)?.attrs.nodeVersion, 1)
})

test('preserves remote nodeVersion when a remote transaction changes node content', () => {
  const schema = createSchema()
  const doc = schema.node('doc', null, [
    schema.node('paragraph', { nodeId: PARAGRAPH_ID, nodeVersion: 7 }, [schema.text('远端前')])
  ])
  let state = EditorState.create({ schema, doc, plugins: [createIdentityPlugin(() => crypto.randomUUID())] })
  const remoteTransaction = state.tr
    .insertText('更新', 2)
    .setMeta(ySyncPluginKey, { isChangeOrigin: true })
  state = apply(state, remoteTransaction)

  assert.equal(state.doc.firstChild?.attrs.nodeVersion, 7)
})

test('leaves duplicate resource reference identities paired for server-side migration', () => {
  const schema = createSchema()
  const first = resourceReference(schema, REFERENCE_ID, REFERENCE_ID)
  const duplicate = resourceReference(schema, REFERENCE_ID, REFERENCE_ID)
  const doc = schema.node('doc', null, [
    schema.node('paragraph', { nodeId: PARAGRAPH_ID, nodeVersion: 0 }, [first, duplicate])
  ])
  let state = EditorState.create({ schema, doc, plugins: [createIdentityPlugin(() => crypto.randomUUID())] })

  state = apply(state, state.tr.insertText('!', 1))
  const references: Array<{ nodeId: string; refId: string }> = []
  state.doc.descendants(node => {
    if (node.type.name === 'resourceReference') references.push({
      nodeId: node.attrs.nodeId,
      refId: node.attrs.refId
    })
    return true
  })

  assert.deepEqual(references, [
    { nodeId: REFERENCE_ID, refId: REFERENCE_ID },
    { nodeId: REFERENCE_ID, refId: REFERENCE_ID }
  ])
})

test('remaps every registered node for copy, paste and import without mutating the source', () => {
  const schema = createSchema()
  const source = schema.node('doc', null, [
    schema.node('paragraph', { nodeId: PARAGRAPH_ID, nodeVersion: 4 }, [
      schema.text('前'),
      resourceReference(schema),
      schema.text('后')
    ])
  ])
  const generated = [
    '123e4567-e89b-12d3-a456-426614174030',
    '123e4567-e89b-12d3-a456-426614174031'
  ]
  const remapped = remapCrdtNodeIdentities(source, getCrdtNodeIds(source), () => generated.shift()!)
  const remappedParagraph = remapped.firstChild!
  const remappedReference = remappedParagraph.child(1)

  assert.equal(source.firstChild?.attrs.nodeId, PARAGRAPH_ID)
  assert.equal(source.firstChild?.child(1).attrs.nodeId, REFERENCE_ID)
  assert.equal(remappedParagraph.attrs.nodeId, '123e4567-e89b-12d3-a456-426614174030')
  assert.equal(remappedParagraph.attrs.nodeVersion, 0)
  assert.equal(remappedReference.attrs.nodeId, '123e4567-e89b-12d3-a456-426614174031')
  assert.equal(remappedReference.attrs.refId, remappedReference.attrs.nodeId)
  assert.equal(remappedReference.attrs.nodeVersion, 0)

  const slice = new Slice(Fragment.from(source.firstChild!), 0, 0)
  const remappedSlice = remapCrdtNodeIdentitiesInSlice(
    slice,
    new Set(['123e4567-e89b-12d3-a456-426614174040']),
    (() => {
      const ids = [
        '123e4567-e89b-12d3-a456-426614174041',
        '123e4567-e89b-12d3-a456-426614174042'
      ]
      return () => ids.shift()!
    })()
  )
  assert.equal(remappedSlice.content.firstChild?.attrs.nodeId, '123e4567-e89b-12d3-a456-426614174041')
  assert.equal(remappedSlice.content.firstChild?.child(1).attrs.refId, '123e4567-e89b-12d3-a456-426614174042')
})

test('does not silently repair a resourceReference nodeId/refId mismatch', () => {
  assert.equal(hasResourceReferenceIdentityMismatch({ nodeId: PARAGRAPH_ID, refId: REFERENCE_ID }), true)
  assert.equal(hasResourceReferenceIdentityMismatch({ nodeId: REFERENCE_ID, refId: REFERENCE_ID }), false)
  assert.equal(hasResourceReferenceIdentityMismatch({ nodeId: null, refId: REFERENCE_ID }), false)
  assert.equal(hasResourceReferenceIdentityMismatch({ nodeId: 'not-a-uuid', refId: REFERENCE_ID }), true)

  const schema = createSchema()
  const source = schema.node('doc', null, [schema.node('paragraph', { nodeId: PARAGRAPH_ID, nodeVersion: 2 }, [
    resourceReference(schema, PARAGRAPH_ID, REFERENCE_ID)
  ])])
  const generated = [
    '123e4567-e89b-12d3-a456-426614174030',
    '123e4567-e89b-12d3-a456-426614174031'
  ]
  const remapped = remapCrdtNodeIdentities(source, new Set(), () => generated.shift()!)
  const remappedReference = remapped.firstChild!.firstChild!
  assert.notEqual(remappedReference.attrs.nodeId, PARAGRAPH_ID)
  assert.equal(remappedReference.attrs.refId, REFERENCE_ID)
  assert.equal(hasResourceReferenceIdentityMismatch(remappedReference.attrs), true)
})
