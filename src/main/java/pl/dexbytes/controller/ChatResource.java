package pl.dexbytes.controller;

import dev.langchain4j.service.UserMessage;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pl.dexbytes.services.ChatService;
import pl.dexbytes.model.CentralaResponse;

@Path("/chat")
public class ChatResource {
    @Inject
    ChatService chatService;

    @POST
    @Path("/coder")
    public String coderChat(@UserMessage String message) {
        return chatService.coderChat(message);
    }

    @GET
    @Path("/answers")
    public CentralaResponse answers() {
        return chatService.answerQuestions();
    }
}
