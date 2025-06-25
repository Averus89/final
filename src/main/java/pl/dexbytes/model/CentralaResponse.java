package pl.dexbytes.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Data
@Builder
@Jacksonized
public class CentralaResponse {
    private int code;
    private String message;
    List<String> ok;
    List<String> failed;
}
