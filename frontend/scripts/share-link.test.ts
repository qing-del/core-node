import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { resolve } from 'node:path'
import {
  isValidShareLinkCode,
  resolvePostLoginDestination,
  shareDocumentRouteFromId,
  shareLoginLocation,
  sanitizeShareRedirect,
  shareLinkCodeFromPath
} from '../src/utils/shareLink.ts'
import {
  MAX_DOCUMENT_SHARE_LINK_USES,
  MAX_DOCUMENT_SHARE_LINK_VALID_FOR_SECONDS,
  normalizeShareLink,
  normalizeShareLinkRedeemResponse,
  validateShareLinkCreateInput
} from '../src/utils/shareLinkContract.ts'

const VALID_CODE = 'A'.repeat(43)

test('accepts only the server opaque Base64URL code shape', () => {
  assert.equal(isValidShareLinkCode(VALID_CODE), true)
  assert.equal(isValidShareLinkCode(`${VALID_CODE}x`), false)
  assert.equal(isValidShareLinkCode('A'.repeat(42)), false)
  assert.equal(isValidShareLinkCode('!'.repeat(43)), false)
  assert.equal(isValidShareLinkCode(null), false)
})

test('allows only an exact internal share landing path as login redirect', () => {
  const sharePath = `/share/documents/${VALID_CODE}`
  assert.equal(sanitizeShareRedirect(sharePath), sharePath)
  assert.equal(shareLinkCodeFromPath(sharePath), VALID_CODE)

  assert.equal(sanitizeShareRedirect('https://attacker.example/'), null)
  assert.equal(sanitizeShareRedirect(`//attacker.example/share/documents/${VALID_CODE}`), null)
  assert.equal(sanitizeShareRedirect(`/user/documents/1`), null)
  assert.equal(sanitizeShareRedirect(`${sharePath}?next=https://attacker.example`), null)
  assert.equal(sanitizeShareRedirect(`${sharePath}#fragment`), null)
  assert.equal(shareLinkCodeFromPath('/share/documents/not-a-code'), null)
})

test('preserves only the share flow through login and uses the redeem document ID', () => {
  const sharePath = `/share/documents/${VALID_CODE}`
  assert.deepEqual(shareLoginLocation(sharePath), { path: '/login', query: { redirect: sharePath } })
  assert.equal(shareLoginLocation('https://attacker.example/'), '/login')
  assert.equal(resolvePostLoginDestination('user', sharePath), sharePath)
  assert.equal(resolvePostLoginDestination('user', 'https://attacker.example/'), '/user')
  assert.equal(resolvePostLoginDestination('admin', sharePath), '/admin')
  assert.deepEqual(shareDocumentRouteFromId(314), {
    name: 'UserDocumentEditor',
    params: { documentId: '314' }
  })
  assert.throws(() => shareDocumentRouteFromId(0))
})

test('normalizes a historical link with an omitted shareUrl', () => {
  const link = normalizeShareLink({
    shareLinkId: 9,
    documentId: 42,
    permission: 'READ',
    expiresAt: '2030-01-01T00:00:00',
    maxUses: 10,
    usedCount: 1,
    enabled: true,
    createTime: '2026-01-01T00:00:00',
    updateTime: '2026-01-01T00:00:00'
  })
  assert.equal(link.shareUrl, null)
  assert.equal(link.revokedAt, null)
})

test('rejects unsafe share-link response fields and create bounds', () => {
  const base = {
    shareLinkId: 9,
    documentId: 42,
    permission: 'READ',
    shareUrl: `https://app.example/s/${VALID_CODE}`,
    expiresAt: '2030-01-01T00:00:00',
    maxUses: 10,
    usedCount: 1,
    enabled: true,
    createTime: '2026-01-01T00:00:00',
    updateTime: '2026-01-01T00:00:00'
  }
  for (const field of ['permission', 'shareLinkId', 'documentId', 'expiresAt', 'maxUses', 'usedCount']) {
    const invalid = {
      ...base,
      [field]: field === 'permission' ? 'ADMIN' : field === 'expiresAt' ? 'not-a-date' : field === 'usedCount' ? 11 : 0
    }
    assert.throws(() => normalizeShareLink(invalid))
  }
  assert.throws(() => normalizeShareLink({ ...base, shareUrl: `https://attacker.example/${VALID_CODE}` }))
  assert.throws(() => normalizeShareLink({ ...base, shareUrl: null }, { requireShareUrl: true }))
  assert.throws(() => normalizeShareLinkRedeemResponse({ documentId: 0, permission: 'READ', owner: false }))
  assert.throws(() => validateShareLinkCreateInput({ permission: 'READ', validForSeconds: 0, maxUses: 1 }))
  assert.throws(() => validateShareLinkCreateInput({ permission: 'READ', validForSeconds: MAX_DOCUMENT_SHARE_LINK_VALID_FOR_SECONDS + 1, maxUses: 1 }))
  assert.throws(() => validateShareLinkCreateInput({ permission: 'READ', validForSeconds: 1, maxUses: MAX_DOCUMENT_SHARE_LINK_USES + 1 }))
})

test('keeps Vite and Nginx ownership of short-link and SPA routes separate', () => {
  const repositoryRoot = resolve(process.cwd(), '..')
  const viteConfig = readFileSync(resolve(repositoryRoot, 'frontend/vite.config.ts'), 'utf8')
  const nginxConfig = readFileSync(resolve(repositoryRoot, 'nginx.conf'), 'utf8')
  const viteShareBlock = viteConfig.split("'/s/'")[1]?.split('\n      }')[0] ?? ''
  assert.match(viteConfig, /'\/api'/)
  assert.match(viteConfig, /'\/ws'/)
  assert.match(viteShareBlock, /target:\s*'http:\/\/localhost:8080'/)
  assert.doesNotMatch(viteShareBlock, /rewrite:/)
  assert.doesNotMatch(viteConfig, /['"]\/share\//)
  assert.match(nginxConfig, /location \^~ \/s\/ \{[\s\S]*proxy_pass http:\/\/backend;[\s\S]*access_log off;/)
  assert.match(nginxConfig, /location \^~ \/api\/user\/document\/share-links\/ \{[\s\S]*rewrite \^\/api\/\(\.\*\)\$ \/\$1 break;[\s\S]*access_log off;/)
  assert.equal(nginxConfig.includes('location / {'), true)
  assert.equal(nginxConfig.includes('try_files $uri $uri/ /index.html;'), true)
  assert.doesNotMatch(nginxConfig, /location \^~ \/share\//)
})
