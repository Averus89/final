package pl.dexbytes.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import pl.dexbytes.ai.Ingestion;

@Path("/loader")
public class LoaderResource {
    @Inject
    Ingestion ingestion;

    @GET
    @Path("/ingest")
    public String ingestFile(@QueryParam("path") String path) {
        ingestion.ingest(path);
        return "OK";
    }
}
