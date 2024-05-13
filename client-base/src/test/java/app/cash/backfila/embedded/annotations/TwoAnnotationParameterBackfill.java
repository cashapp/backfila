package app.cash.backfila.embedded.annotations;

import app.cash.backfila.client.BackfilaDefault;
import app.cash.backfila.client.BackfilaRequired;
import app.cash.backfila.client.BackfillConfig;
import app.cash.backfila.client.fixedset.FixedSetBackfill;
import app.cash.backfila.client.fixedset.FixedSetRow;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

/**
 * Testing that having too many annotations fails.
 */
class TwoAnnotationParameterBackfill extends FixedSetBackfill<TwoAnnotationParameterBackfill.TwoAnnotationParameter> {

  @Inject
  public TwoAnnotationParameterBackfill(){
  }

  @Override public void runOne(@NotNull FixedSetRow row,
      @NotNull BackfillConfig<TwoAnnotationParameter> backfillConfig) {
    throw new RuntimeException("This backfill should never be runnable.");
  }

  public static class TwoAnnotationParameter {
    public final String parameter;

    public TwoAnnotationParameter(
        @BackfilaDefault(name = "parameter", value = "upper")
        @BackfilaRequired(name = "whyMultipleAreDangerous")
        String parameter) {
      this.parameter = parameter;
    }
  }
}

