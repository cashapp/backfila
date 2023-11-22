package app.cash.backfila.embedded;

import app.cash.backfila.client.BackfilaHttpClientConfig;
import app.cash.backfila.client.fixedset.FixedSetBackfillModule;
import app.cash.backfila.client.misk.MiskBackfillModule;
import com.google.inject.AbstractModule;
import misk.MiskTestingServiceModule;
import misk.environment.DeploymentModule;
import misk.logging.LogCollectorModule;
import wisp.deployment.DeploymentKt;

/**
 * Testing that Java modules work too.
 */
public class JavaTestingModule extends AbstractModule {

  @Override protected void configure() {
    install(new DeploymentModule(DeploymentKt.getTESTING()));
    install(new LogCollectorModule());
    install(new MiskTestingServiceModule());
    install(
        new MiskBackfillModule(
            new BackfilaHttpClientConfig(
                "test.url",
                "#test"
            )
        )
    );
    install(FixedSetBackfillModule.create(JavaChangeCaseTestBackfill.class));

    install(new EmbeddedBackfilaModule());
  }
}
