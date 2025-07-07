package pl.dexbytes.controller;

import dev.langchain4j.service.UserMessage;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import pl.dexbytes.services.ChatService;

@Path("/chat")
public class ChatResource {
    @Inject
    ChatService chatService;

    @POST
    @Path("/coder")
    public String coderChat(@UserMessage String message) {
        return chatService.coderChat(message);
    }
}
