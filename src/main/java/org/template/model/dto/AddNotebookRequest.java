package org.template.model.dto;

import lombok.Data;

/**
 * 创建笔记本请求 DTO
 */
@Data
public class AddNotebookRequest {

    private String name;

    private String description;

    private Integer userId;
}