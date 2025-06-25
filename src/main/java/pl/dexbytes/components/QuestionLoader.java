package pl.dexbytes.components;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

@Slf4j
public class QuestionLoader {
    
    public static List<String> loadQuestions() {
        try {
            // Get the JSON file from the resources folder
            InputStream inputStream = QuestionLoader.class.getClassLoader().getResourceAsStream("/questions.json");

            // Use Jackson ObjectMapper to parse JSON
            ObjectMapper objectMapper = new ObjectMapper();
            TypeReference<List<String>> typeReference = new TypeReference<>() {};
            
            return objectMapper.readValue(inputStream, typeReference);
        } catch (Exception e) {
            log.error("Error loading questions", e);
            return List.of();
        }
    }
}