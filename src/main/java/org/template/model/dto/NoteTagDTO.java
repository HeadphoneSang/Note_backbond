package org.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagDTO {

    private Integer noteId;

    private Integer tagId;

    private Integer userId;

    private String tagName;

    private String tagColor;

    private LocalDateTime createdAt;

}
