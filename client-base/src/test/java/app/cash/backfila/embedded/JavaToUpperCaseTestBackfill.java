package app.cash.backfila.embedded;

import app.cash.backfila.client.BackfillConfig;
import app.cash.backfila.client.NoParameters;
import app.cash.backfila.client.ValidateResult;
import app.cash.backfila.client.fixedset.FixedSetBackfill;
import app.cash.backfila.client.fixedset.FixedSetRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.jetbrains.annotations.NotNull;

/**
 * Testing that creating Java backfill classes works. Parameters are not currently supported.
 */
class JavaToUpperCaseTestBackfill extends FixedSetBackfill<NoParameters> {
  public List<String> runOrder = new ArrayList<>();

  @Inject
  public JavaToUpperCaseTestBackfill() {

  }

  @NotNull @Override
  public ValidateResult<NoParameters> checkBackfillConfig(@NotNull BackfillConfig<NoParameters> backfillConfig) {
    // No parameters to check
    return super.checkBackfillConfig(backfillConfig);
  }

  @Override public void runOne(@NotNull FixedSetRow row) {
    runOrder.add(row.getValue());
    row.setValue(row.getValue().toUpperCase(Locale.ROOT));
  }
}

