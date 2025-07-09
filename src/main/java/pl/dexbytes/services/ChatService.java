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
        // Initialize assistant - this must be done after injection
        ChatModel chatModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_AI_API_KEY"))
                .endpoint(System.getenv("AZURE_AI_BASE_URL"))
                .deploymentName("gpt-4.1")
                .logRequestsAndResponses(true)
                .build();

        assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retriever.get())
                .build();
    }

    public String coderChat(String message) {
        return assistant.coderChat(message);
    }
}