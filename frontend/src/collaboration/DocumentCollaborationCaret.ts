import CollaborationCaret from '@tiptap/extension-collaboration-caret'
import type { Extension } from '@tiptap/core'

import type { DocumentCollaborationClient } from './DocumentCollaborationClient'

/**
 * 创建基于现有 WebSocket 客户端的协同光标扩展。
 * provider 仅负责把 Awareness 适配给 Tiptap，不引入额外网络事实源。
 */
export function createDocumentCollaborationCaret(
  client: DocumentCollaborationClient,
  localUser: Record<string, unknown>,
): Extension {
  return CollaborationCaret.configure({
    provider: client.getAwarenessProvider(),
    user: localUser,
    render: renderCollaborativeCaret,
    selectionRender: renderCollaborativeSelection,
  })
}

function renderCollaborativeCaret(user: Record<string, unknown>): HTMLElement {
  const color = safeColor(user.color)
  const sessionId = displayString(user.sessionId, 'unknown-session')
  const name = displayString(user.name, '协作者')
  const labelText = name + ' · Session ' + sessionId
  const awarenessClientId = displayString(user.awarenessClientId, '')

  const caret = document.createElement('span')
  caret.classList.add('collaboration-carets__caret')
  caret.style.color = color
  caret.style.borderColor = color
  caret.dataset.awarenessClientId = awarenessClientId
  caret.dataset.sessionId = sessionId

  const label = document.createElement('div')
  label.classList.add('collaboration-carets__label')
  label.style.backgroundColor = color
  label.textContent = labelText
  label.title = labelText
  caret.append(label)

  return caret
}

function renderCollaborativeSelection(
  user: Record<string, unknown>,
): Record<string, string> {
  const color = safeColor(user.color)
  const sessionId = displayString(user.sessionId, 'unknown-session')
  const awarenessClientId = displayString(user.awarenessClientId, '')

  return {
    nodeName: 'span',
    class: 'collaboration-carets__selection',
    style: 'background-color: ' + color + '40; color: ' + color + ';',
    'data-awareness-client-id': awarenessClientId,
    'data-session-id': sessionId,
  }
}

function safeColor(value: unknown): string {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value)
    ? value
    : '#000000'
}

function displayString(value: unknown, fallback: string): string {
  if (typeof value === 'string' && value.trim().length > 0) {
    return value
  }

  if (typeof value === 'number' && Number.isSafeInteger(value)) {
    return String(value)
  }

  return fallback
}
