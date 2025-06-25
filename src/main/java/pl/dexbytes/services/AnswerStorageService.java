package pl.dexbytes.services;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pl.dexbytes.model.Answer;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AnswerStorageService {
    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<Integer, Answer> valueCommands;
    private KeyCommands<Integer> keyCommands;

    public void init() {
        if (valueCommands == null) {
            valueCommands = redisDataSource.value(Integer.class, Answer.class);
            keyCommands = redisDataSource.key(Integer.class);
        }

    }

    public void storeAnswer(int index, Answer answer) {
        init();
        valueCommands.set(index, answer);
    }

    public Answer getAnswer(int index) {
        init();
        return valueCommands.get(index);
    }

    // Get all correct answers
    public List<Answer> getAllCorrectAnswers() {
        init();
        List<Answer> results = new ArrayList<>();

        List<Integer> keys = keyCommands.keys("*");

        for (int key : keys) {
            Answer answer = valueCommands.get(key);
            if (answer != null && answer.isCorrect()) {
                results.add(answer);
            }
        }

        return results;
    }

    // Get all incorrect answers
    public List<Answer> getAllIncorrectAnswers() {
        init();
        List<Answer> results = new ArrayList<>();

        List<Integer> keys = keyCommands.keys("*");

        for (int key : keys) {
            Answer answer = valueCommands.get(key);
            if (answer != null && !answer.isCorrect()) {
                results.add(answer);
            }
        }

        return results;
    }

}
