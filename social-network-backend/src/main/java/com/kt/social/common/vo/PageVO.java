package com.kt.social.common.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageVO<T> {
    Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private Integer numberOfElements;
    private List<T> content;
}
