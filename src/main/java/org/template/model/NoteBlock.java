package org.template.model;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 笔记实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_block")
public class NoteBlock {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String type;

    private String orderKey;

    private JsonNode deltaJson;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

}
