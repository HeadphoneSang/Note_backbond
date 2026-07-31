package org.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.template.model.Tag;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagDiffRequest {

    private Integer noteId;

    private List<Integer> addTagIds;

    private List<Integer> removeTagIds;
}
