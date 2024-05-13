package app.cash.backfila.embedded.annotations;

import app.cash.backfila.client.BackfilaDefault;
import app.cash.backfila.client.BackfilaNullDefault;
import app.cash.backfila.client.BackfilaRequired;
import app.cash.backfila.client.BackfillConfig;
import app.cash.backfila.client.fixedset.FixedSetBackfill;
import app.cash.backfila.client.fixedset.FixedSetRow;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

/**
 * Testing that having too many annotations fails.
 */
class ThreeAnnotationParameterBackfill extends FixedSetBackfill<ThreeAnnotationParameterBackfill.ThreeAnnotationParameter> {

  @Inject
  public ThreeAnnotationParameterBackfill(){
  }

  @Override public void runOne(@NotNull FixedSetRow row,
      @NotNull BackfillConfig<ThreeAnnotationParameter> backfillConfig) {
    throw new RuntimeException("This backfill should never be runnable.");
  }

  public static class ThreeAnnotationParameter {
    public final String parameter;

    public ThreeAnnotationParameter(
        @BackfilaDefault(name = "parameter", value = "upper")
        @BackfilaRequired(name = "whyMultipleAreDangerous")
        @BackfilaNullDefault(name = "reallyBadIdea")
        String parameter) {
      this.parameter = parameter;
    }
  }
}

