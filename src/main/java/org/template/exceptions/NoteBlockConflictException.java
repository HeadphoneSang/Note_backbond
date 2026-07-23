package org.template.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.template.model.dto.NoteDiffResult;

@EqualsAndHashCode(callSuper = true)
@Data
public class NoteBlockConflictException extends RuntimeException {

    private final NoteDiffResult result;

    public NoteBlockConflictException(NoteDiffResult result,String message) {
        super(message);
        this.result = result;
    }

}
