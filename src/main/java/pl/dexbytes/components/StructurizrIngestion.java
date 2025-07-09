package pl.dexbytes.components;

import com.structurizr.Workspace;
import com.structurizr.util.WorkspaceUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;

@Slf4j
@ApplicationScoped
public class StructurizrIngestion {
    @Inject
    Driver driver;

    public void load(String json) throws Exception {
        Workspace workspace = WorkspaceUtils.fromJson(json);
        try (var session = driver.session()) {
            Result result = session.run("MATCH (n) DETACH DELETE n");
            log.info(result.consume().toString());
        }

        new StructurizrSimpleLoader().load(workspace, driver, "neo4j");
    }
}
