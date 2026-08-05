package org.template.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String title;

    private String summary;

    private Boolean isMarked;

    /** 逻辑删除（0=否 1=是）— 所有自动生成 SQL 自动过滤 is_delete = 0 */
    @TableLogic
    private Boolean isDelete;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @Version
    private Integer version;

    private Integer userId;

    private Integer notebookId;

    @TableField(exist = false)
    private List<Tag> tags;
}
