package pl.dexbytes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface Assistant {

    @SystemMessage("""
            You're Java, JavaScript and Typescript master specialist, helping answering questions from user, using available knowledge-base.
            """)
    String coderChat(@UserMessage String userMessage);
}
