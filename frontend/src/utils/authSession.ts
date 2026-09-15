export type AuthClientId = 'user' | 'admin'

export type AuthGrantType = 'password' | 'email-code' | 'refresh_token'

export interface AuthTokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  scope: string
}

export interface StoredAuthSession {
  accessToken: string | null
  refreshToken: string | null
  clientId: AuthClientId | null
  scopes: string[]
}

export interface AuthSessionListener {
  onUpdated?: (session: StoredAuthSession) => void
  onCleared?: () => void
}

const ACCESS_TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refreshToken'
const CLIENT_ID_KEY = 'authClientId'
const SCOPES_KEY = 'authScopes'

let sessionListener: AuthSessionListener | null = null

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage
}

function readValue(key: string): string | null {
  return getStorage()?.getItem(key) ?? null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** Maps the backend's snake_case token response without retaining raw response details in errors. */
export function normalizeAuthTokenResponse(value: unknown): AuthTokenResponse {
  if (!isRecord(value)) {
    throw new Error('认证响应无效')
  }

  const accessToken = value.access_token
  const tokenType = value.token_type
  const expiresIn = value.expires_in
  const refreshToken = value.refresh_token
  const scope = value.scope

  if (typeof accessToken !== 'string' || accessToken.length === 0
      || typeof tokenType !== 'string' || tokenType.length === 0
      || typeof expiresIn !== 'number' || !Number.isFinite(expiresIn) || expiresIn <= 0
      || typeof refreshToken !== 'string' || refreshToken.length === 0
      || typeof scope !== 'string') {
    throw new Error('认证响应无效')
  }

  return { accessToken, tokenType, expiresIn, refreshToken, scope }
}

/** Keeps the server-issued scope patterns as-is; it never expands wildcard scopes. */
export function parseGrantedScopes(scope: string): string[] {
  if (!scope) return []
  return scope.split(' ').filter(Boolean)
}

function parseScopePattern(scope: string): [string, string] | null {
  const parts = scope.split(':')
  if (parts.length !== 2 || !parts[0] || !parts[1]) return null
  const isValidComponent = (component: string) => component === '*'
    || /^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(component)
  return isValidComponent(parts[0]) && isValidComponent(parts[1])
    ? [parts[0], parts[1]]
    : null
}

/** Matches one concrete route scope against a server-issued scope pattern. */
export function hasGrantedScope(grantedScopes: readonly string[], requiredScope: string): boolean {
  const required = parseScopePattern(requiredScope)
  if (!required) return false

  return grantedScopes.some((grantedScope) => {
    const granted = parseScopePattern(grantedScope)
    if (!granted) return false

    const resourceMatches = required[0] === '*'
      ? granted[0] === '*'
      : granted[0] === '*' || granted[0] === required[0]
    const actionMatches = required[1] === '*'
      ? granted[1] === '*'
      : granted[1] === '*' || granted[1] === required[1]
    return resourceMatches && actionMatches
  })
}

export function hasAllGrantedScopes(grantedScopes: readonly string[], requiredScopes: readonly string[]): boolean {
  return requiredScopes.every(scope => hasGrantedScope(grantedScopes, scope))
}

export function readAuthSession(): StoredAuthSession {
  const rawScopes = readValue(SCOPES_KEY)
  let scopes: string[] = []
  if (rawScopes) {
    try {
      const parsed: unknown = JSON.parse(rawScopes)
      if (Array.isArray(parsed) && parsed.every(scope => typeof scope === 'string')) {
        scopes = parsed
      }
    } catch {
      scopes = []
    }
  }

  const rawClientId = readValue(CLIENT_ID_KEY)
  return {
    accessToken: readValue(ACCESS_TOKEN_KEY),
    refreshToken: readValue(REFRESH_TOKEN_KEY),
    clientId: rawClientId === 'user' || rawClientId === 'admin' ? rawClientId : null,
    scopes
  }
}

export function saveAuthSession(response: AuthTokenResponse, clientId: AuthClientId): StoredAuthSession {
  const storage = getStorage()
  const scopes = parseGrantedScopes(response.scope)
  storage?.setItem(ACCESS_TOKEN_KEY, response.accessToken)
  storage?.setItem(REFRESH_TOKEN_KEY, response.refreshToken)
  storage?.setItem(CLIENT_ID_KEY, clientId)
  storage?.setItem(SCOPES_KEY, JSON.stringify(scopes))
  // Remove the pre-upgrade client marker once a new session has been issued.
  storage?.removeItem('authScope')

  return { accessToken: response.accessToken, refreshToken: response.refreshToken, clientId, scopes }
}

export function clearStoredAuth(): boolean {
  const storage = getStorage()
  const hadSession = Boolean(storage?.getItem(ACCESS_TOKEN_KEY) || storage?.getItem(REFRESH_TOKEN_KEY))
  storage?.removeItem(ACCESS_TOKEN_KEY)
  storage?.removeItem('accessToken')
  storage?.removeItem(REFRESH_TOKEN_KEY)
  storage?.removeItem(CLIENT_ID_KEY)
  storage?.removeItem(SCOPES_KEY)
  storage?.removeItem('authScope')
  return hadSession
}

export function registerAuthSessionListener(listener: AuthSessionListener): () => void {
  sessionListener = listener
  return () => {
    if (sessionListener === listener) sessionListener = null
  }
}

export function notifyAuthSessionUpdated(session: StoredAuthSession): void {
  sessionListener?.onUpdated?.(session)
}

export function notifyAuthSessionCleared(): void {
  sessionListener?.onCleared?.()
}
