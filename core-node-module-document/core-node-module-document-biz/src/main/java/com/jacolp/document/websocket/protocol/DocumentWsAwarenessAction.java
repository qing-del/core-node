package com.jacolp.document.websocket.protocol;

/** Awareness 元数据控制帧的生命周期动作。 */
public enum DocumentWsAwarenessAction {
    /** 发布或刷新一个活跃 WebSocket Session 的可信元数据。 */
    UPSERT,
    /** 删除已经离开 Room 的 WebSocket Session 元数据。 */
    REMOVE
}
