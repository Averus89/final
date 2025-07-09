package pl.dexbytes.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import pl.dexbytes.ai.Ingestion;
import pl.dexbytes.components.StructurizrIngestion;

@Path("/loader")
@Slf4j
public class LoaderResource {
    @Inject
    Ingestion ingestion;
    @Inject
    StructurizrIngestion structurizrIngestion;

    @GET
    @Path("/ingest")
    public String ingestFile(@QueryParam("path") String path) {
        ingestion.ingest(path);
        return "OK";
    }

    @POST
    @Path("/ingest/structurizr")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public String ingestStructurizr(String structurizrJson) {
        try {
            structurizrIngestion.load(structurizrJson);
            return "OK";
        } catch (Exception e) {
            log.error("Error while loading Structurizr JSON: {}", e.getMessage(), e);
            return "Error while loading Structurizr JSON: " + e.getMessage();
        }
    }
}
