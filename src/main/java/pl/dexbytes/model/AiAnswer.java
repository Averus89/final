package pl.dexbytes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class AiAnswer {
    private String question;
    @JsonProperty("_thoughts")
    private String thoughts;
    private String answer;
}
