package pl.dexbytes.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Answer {
    private String question;
    private String answer;
    private Boolean isCorrect;

    public Boolean isCorrect() {
        return isCorrect != null && isCorrect;
    }
}
