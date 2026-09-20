import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildSnapshotCleanupSummary,
  expandedOwnerIdsForSearch,
  filterAdminDocumentTree,
  uniqueDocumentIds
} from '../src/utils/adminDocument.ts'
import type { AdminDocumentTreeNode, AdminSnapshotHistoryClearResponse } from '../src/api/admin.ts'

const tree: AdminDocumentTreeNode[] = [
  {
    userId: 7,
    username: 'alice',
    nickname: '阿丽丝',
    documents: [
      { documentId: 11, title: '课程笔记', lastModifyTime: 1, lastModifyUserId: 7, hasSnapshot: true },
      { documentId: 12, title: '项目方案', lastModifyTime: 2, lastModifyUserId: null, hasSnapshot: false }
    ]
  },
  {
    userId: 8,
    username: 'bob',
    nickname: null,
    documents: [{ documentId: 21, title: '周报', lastModifyTime: 3, lastModifyUserId: 8, hasSnapshot: true }]
  }
]

test('filters the owner tree by owner fields or document fields without flattening it', () => {
  assert.deepEqual(filterAdminDocumentTree(tree, 'alice'), [tree[0]])
  assert.deepEqual(filterAdminDocumentTree(tree, '12'), [{ ...tree[0], documents: [tree[0].documents[1]] }])
  assert.deepEqual(filterAdminDocumentTree(tree, '周报'), [tree[1]])
  assert.deepEqual(filterAdminDocumentTree(tree, 'missing'), [])
})

test('expands every matching owner while a search is active', () => {
  assert.deepEqual(expandedOwnerIdsForSearch(tree, '课程'), [7])
  assert.deepEqual(expandedOwnerIdsForSearch(tree, '8'), [8])
  assert.deepEqual(expandedOwnerIdsForSearch(tree, ''), [])
})

test('deduplicates valid batch document ids in their original order', () => {
  assert.deepEqual(uniqueDocumentIds([12, 11, 12, 0, -1, Number.NaN, 21]), [12, 11, 21])
})

test('summarizes complete and zero-deletion cleanup accurately', () => {
  const result: AdminSnapshotHistoryClearResponse = {
    documents: [{ documentId: 11, deletedObjectCount: 0, releasedBytes: 0, success: true, failureMessage: null }],
    deletedObjectCount: 0,
    releasedBytes: 0
  }
  assert.equal(buildSnapshotCleanupSummary(result), '已删除 0 个历史快照，释放 0 B。 1 篇文档均已完成清理。')
})

test('includes document-specific failures in a partial cleanup summary', () => {
  const result: AdminSnapshotHistoryClearResponse = {
    documents: [
      { documentId: 11, deletedObjectCount: 2, releasedBytes: 1536, success: true, failureMessage: null },
      { documentId: 12, deletedObjectCount: 1, releasedBytes: 512, success: false, failureMessage: '删除快照失败，可重试' }
    ],
    deletedObjectCount: 3,
    releasedBytes: 2048
  }
  assert.equal(
    buildSnapshotCleanupSummary(result),
    '已删除 3 个历史快照，释放 2.0 KB。\n1 篇文档未完整清理：\n文档 #12：删除快照失败，可重试'
  )
})
