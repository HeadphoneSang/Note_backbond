package org.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.template.model.NoteBlock;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDiffRequest {

    private List<NoteBlock> updatedBlocks;
    private List<NoteBlock> deletedBlocks;
    private List<NoteBlock> insertedBlocks;
}


