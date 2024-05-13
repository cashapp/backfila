package app.cash.backfila.embedded.annotations;

import app.cash.backfila.client.BackfilaApi;
import app.cash.backfila.client.BackfilaHttpClientConfig;
import app.cash.backfila.client.OnStartup;
import app.cash.backfila.client.fixedset.FixedSetBackfillModule;
import app.cash.backfila.client.internal.EmbeddedBackfila;
import app.cash.backfila.client.misk.MiskBackfillModule;
import app.cash.backfila.embedded.Backfila;
import app.cash.backfila.embedded.EmbeddedBackfilaModule;
import com.google.inject.AbstractModule;
import misk.MiskTestingServiceModule;
import misk.environment.DeploymentModule;
import misk.logging.LogCollectorModule;
import wisp.deployment.DeploymentKt;

/**
 * Add all the annotations.
 */
public class TooManyAnnotationsModule extends AbstractModule {

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
    install(FixedSetBackfillModule.create(TwoAnnotationParameterBackfill.class));

    install(new EmbeddedBackfilaModule(OnStartup.CONTINUE_ON_STARTUP));
  }
}
