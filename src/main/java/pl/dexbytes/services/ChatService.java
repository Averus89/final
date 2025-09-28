package pl.dexbytes.services;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import pl.dexbytes.ai.Assistant;
import pl.dexbytes.ai.Retriever;

@ApplicationScoped
@Slf4j
public class ChatService {
    @Inject
    Retriever retriever;
    private Assistant assistant;

    @PostConstruct
    public void init() {
        assistant = AiServices.builder(Assistant.class)
                .chatModel(getGpt41())
                .retrievalAugmentor(retriever.get())
                .build();
    }

    public static ChatModel getGpt41() {
        return AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_AI_API_KEY"))
                .endpoint(System.getenv("AZURE_AI_BASE_URL"))
                .deploymentName("gpt-4.1")
                .logRequestsAndResponses(true)
                .build();
    }

    public static ChatModel getGpt41Mini() {
        return AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_AI_API_KEY"))
                .endpoint(System.getenv("AZURE_AI_BASE_URL"))
                .deploymentName("gpt-4.1-mini")
                .logRequestsAndResponses(true)
                .build();
    }

    public String coderChat(String message) {
        return assistant.coderChat(message);
    }
}