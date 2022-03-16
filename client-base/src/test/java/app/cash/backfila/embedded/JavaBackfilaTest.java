package app.cash.backfila.embedded;

import app.cash.backfila.client.fixedset.FixedSetDatastore;
import javax.inject.Inject;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing the Java way of using the Backfila testing infrastructure.
 */
@MiskTest(startService = true)
class JavaBackfilaTest {
  @MiskTestModule
  JavaTestingModule module = new JavaTestingModule();

  @Inject FixedSetDatastore datastore;
  @Inject JavaBackfila backfila;

  @Test
  void happyPath() {
    datastore.put("instance", "a", "b", "c");

    var backfillRun = backfila.createWetRun(JavaToUpperCaseTestBackfill.class);
    backfillRun.execute();
    assertThat(backfillRun.getBackfill().runOrder).containsExactly("a", "b", "c");
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C");
  }

  @Test
  void twoInstanceBackfill() {
    datastore.put("instance-1", "a", "b", "c");
    datastore.put("instance-2", "e", "f");

    var backfillRun = backfila.createWetRun(JavaToUpperCaseTestBackfill.class);
    backfillRun.execute();
    assertThat(backfillRun.getBackfill().runOrder).containsExactly("a", "b", "c", "e", "f");
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C", "E", "F");
  }
}
