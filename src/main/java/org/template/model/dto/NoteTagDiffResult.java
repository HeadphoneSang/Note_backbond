package org.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagDiffResult {

    private List<Integer> failAddedTags;

    private List<Integer> failRemovedTags;

    public boolean hasConflict() {
        return !failAddedTags.isEmpty() || !failRemovedTags.isEmpty();
    }
}
