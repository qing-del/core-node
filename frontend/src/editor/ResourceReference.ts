import { Node, mergeAttributes } from '@tiptap/core'

export interface ResourceReferenceAttributes {
  /** 引用节点自身的稳定 ID；新建、复制和导入时必须使用新的 UUID。 */
  refId: string
  /** 被引用资源的类型；文档绑定协议使用大写 DOCUMENT。 */
  resourceType: string | null
  /** 被引用资源的业务 ID；以十进制字符串保存，避免前端 number 精度丢失。 */
  resourceId: string | null
  /** 编辑器中展示的引用文本；example: {@code '项目设计文档'} */
  displayText: string
  /** 用户自定义的显示别名；example: {@code '设计文档'} */
  alias: string | null
}

/**
 * 经由 Tiptap 协作绑定写入 Y.XmlFragment 的行内原子节点。
 * 第一版只记录引用属性；后续关系投影器可直接消费这些属性，无需改写编辑器正文。
 */
export const ResourceReference = Node.create({
  name: 'resourceReference',

  inline: true,
  group: 'inline',
  atom: true,
  selectable: true,

  /** 声明引用节点可持久化的最小属性集合。 */
  addAttributes() {
    return {
      // 保留 null 默认值以兼容历史占位节点；新 Link 操作会在业务层严格校验完整属性。
      refId: {
        default: null,
        parseHTML: element => element.getAttribute('data-ref-id') || null
      },
      resourceType: {
        default: null,
        parseHTML: element => element.getAttribute('data-resource-type') || null
      },
      resourceId: {
        default: null,
        parseHTML: element => element.getAttribute('data-resource-id') || null
      },
      displayText: {
        default: '',
        parseHTML: element => element.getAttribute('data-display-text') || element.textContent || ''
      },
      alias: {
        default: null,
        parseHTML: element => element.getAttribute('data-alias') || null
      }
    }
  },

  /** 从带有资源引用 data 属性的 span 恢复 Tiptap 节点。 */
  parseHTML() {
    return [{ tag: 'span[data-document-resource-ref]' }]
  },

  /** 将引用属性渲染为稳定的 data 属性和可读文本。 */
  renderHTML({ HTMLAttributes }) {
    const attributes = HTMLAttributes as ResourceReferenceAttributes
    return ['span', mergeAttributes(
      {
        'data-document-resource-ref': '',
        'data-ref-id': attributes.refId,
        'data-resource-type': attributes.resourceType,
        'data-resource-id': attributes.resourceId,
        'data-display-text': attributes.displayText,
        'data-alias': attributes.alias,
        class: 'document-resource-reference'
      },
      HTMLAttributes
    ), attributes.alias || attributes.displayText || '未命名引用']
  }
})
