package org.template.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTag {
    private Integer noteId;
    private Integer tagId;

    @TableField(fill = FieldFill.INSERT)
    private String updatedAt;
}
