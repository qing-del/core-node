package com.jacolp.note.context;

public class NoteImageResolveContext {

    private static final ThreadLocal<Long> NOTE_ID_HOLDER = new ThreadLocal<>();

    private NoteImageResolveContext() {
    }

    public static void setCurrentNoteId(Long noteId) {
        NOTE_ID_HOLDER.set(noteId);
    }

    public static Long getCurrentNoteId() {
        return NOTE_ID_HOLDER.get();
    }

    public static void clear() {
        NOTE_ID_HOLDER.remove();
    }
}
