package com.jacolp.note.application.dto;

/** Temporary note-local pagination defaults; move to common-core when the legacy DTO shell is removed. */
public interface PageParamProvider {
    int DEFAULT_PAGE = 1;
    int DEFAULT_PAGE_SIZE = 15;

    Integer getPageNum();

    Integer getPageSize();

    default int getPageNumOrDefault() {
        Integer pageNum = getPageNum();
        return pageNum == null ? DEFAULT_PAGE : pageNum;
    }

    default int getPageSizeOrDefault() {
        Integer pageSize = getPageSize();
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
