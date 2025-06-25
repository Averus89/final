package pl.dexbytes.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class CentralaQuery {
    String task;
    String apikey;
    Object answer;
}
