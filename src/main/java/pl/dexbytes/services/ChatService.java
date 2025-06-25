package pl.dexbytes.services;

import com.azure.core.credential.AzureKeyCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import pl.dexbytes.ai.Assistant;
import pl.dexbytes.components.QuestionLoader;
import pl.dexbytes.ai.Retriever;
import pl.dexbytes.model.AiAnswer;
import pl.dexbytes.model.Answer;
import pl.dexbytes.model.CentralaQuery;
import pl.dexbytes.model.CentralaResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_MINI;

@ApplicationScoped
@Slf4j
public class ChatService {
    private final OkHttpClient client;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    Retriever retriever;
    @Inject
    AnswerStorageService answerStorageService;
    private List<String> questions;
    private Assistant assistant;

    public ChatService() {
        client = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(log::info).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static Integer extractIntegerFromBrackets(String input) {
        // For strings like "index[123] = value"
        int start = input.indexOf('[') + 1;
        int end = input.indexOf(']');
        if (start > 0 && end > start) {
            return Integer.parseInt(input.substring(start, end));
        }
        return null;
    }

    @PostConstruct
    public void init() {
        // Initialize questions
        questions = QuestionLoader.loadQuestions();

        // Initialize assistant - this must be done after injection
        ChatModel chatModel = AzureOpenAiChatModel.builder()
                .apiKey(System.getenv("AZURE_AI_API_KEY"))
                .endpoint(System.getenv("AZURE_AI_BASE_URL"))
                .deploymentName("gpt-4.1")
                .build();

        assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .retrievalAugmentor(retriever.get())
                .build();
    }

    public CentralaResponse answerQuestions() {
        List<AiAnswer> aiAnswers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String completion = assistant.chat(questions.get(i), getCorrectData(), getIncorrectData());
            log.info("Answer: {}", completion);
            try {
                AiAnswer answer = objectMapper.readValue(completion, AiAnswer.class);
                aiAnswers.add(answer);
                answerStorageService.storeAnswer(
                        i,
                        Answer.builder()
                                .question(answer.getQuestion())
                                .answer(answer.getAnswer())
                                .build());
                Thread.sleep(15000);
            } catch (Exception e) {
                log.error("Error parsing answer", e);
            }
        }
        try {
            return sendResults(aiAnswers);
        } catch (Exception e) {
            log.error("Error sending answers", e);
        }
        return CentralaResponse.builder().build();
    }

    private CentralaResponse sendResults(List<AiAnswer> aiAnswers) throws IOException {
        CentralaQuery query = CentralaQuery.builder()
                .task("story")
                .apikey("2e86eb0e-629c-4609-a540-60d52256e6a0")
                .answer(aiAnswers.stream().map(AiAnswer::getAnswer).collect(Collectors.toCollection(ArrayList::new)))
                .build();
        String json = objectMapper.writeValueAsString(query);
        Request request = new Request.Builder()
                .post(RequestBody.create(json, MediaType.get("application/json; charset=utf-8")))
                .url("https://c3ntrala.ag3nts.org/report")
                .build();
        Call call = client.newCall(request);
        CentralaResponse centralaResponse;
        try (Response response = call.execute()) {
            centralaResponse = objectMapper.readValue(response.body().string(), CentralaResponse.class);
            List<String> failed = centralaResponse.getFailed();
            List<String> ok = centralaResponse.getOk();
            saveCorrectAnswers(failed, false);
            saveCorrectAnswers(ok, true);
        }
        return centralaResponse;
    }

    private String getCorrectData() {
        StringBuilder sb = new StringBuilder();
        answerStorageService.getAllCorrectAnswers()
                .stream()
                .map(a -> a.getQuestion() + ": " + a.getAnswer())
                .forEach(line -> sb.append(line).append("\n"));
        return sb.toString();
    }

    private String getIncorrectData() {
        StringBuilder sb = new StringBuilder();
        answerStorageService.getAllIncorrectAnswers()
                .stream()
                .map(a -> a.getQuestion() + ": " + a.getAnswer())
                .forEach(line -> sb.append(line).append("\n"));
        return sb.toString();
    }

    private String getExtraContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nBłędne pytania i odpowiedzi:\n");
        answerStorageService.getAllIncorrectAnswers()
                .stream()
                .map(a -> a.getQuestion() + ": " + a.getAnswer())
                .forEach(line -> sb.append(line).append("\n"));
        sb.append("\nPoprawne pytania i odpowiedzi:\n");
        answerStorageService.getAllCorrectAnswers()
                .stream()
                .map(a -> a.getQuestion() + ": " + a.getAnswer())
                .forEach(line -> sb.append(line).append("\n"));
        return sb.toString();
    }

    private void saveCorrectAnswers(List<String> ok, boolean isCorrect) {
        for (String line : ok) {
            Integer index = extractIntegerFromBrackets(line);
            if (index != null) {
                Answer a = answerStorageService.getAnswer(index);
                if (a != null && (a.isCorrect() == null || !a.isCorrect() && isCorrect)) {
                    a.setIsCorrect(isCorrect);
                    answerStorageService.storeAnswer(index, a);
                }
            }
        }
    }

    public String coderChat(String message) {
        return assistant.coderChat(message);
    }
}