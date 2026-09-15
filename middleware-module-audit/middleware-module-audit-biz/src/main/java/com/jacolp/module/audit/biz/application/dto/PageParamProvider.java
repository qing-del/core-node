package com.jacolp.module.audit.biz.application.dto;

public interface PageParamProvider {
    int DEFAULT_PAGE = 1;
    int DEFAULT_PAGE_SIZE = 15;
    Integer getPageNum();
    Integer getPageSize();
    default int getPageNumOrDefault() { return getPageNum() == null ? DEFAULT_PAGE : getPageNum(); }
    default int getPageSizeOrDefault() { return getPageSize() == null ? DEFAULT_PAGE_SIZE : getPageSize(); }
}
