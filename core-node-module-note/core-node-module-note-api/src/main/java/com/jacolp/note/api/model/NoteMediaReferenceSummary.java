package com.jacolp.note.api.model;

import java.time.LocalDateTime;

/** Stable projection of a note's relation to one media file. */
public record NoteMediaReferenceSummary(Long id, String title, Short isCrossUser,
                                        Short status, LocalDateTime createTime) { }
