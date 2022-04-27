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
class JavaChangeCaseTestBackfill extends FixedSetBackfill<CaseParameters> {
  public List<String> runOrder = new ArrayList();
  public CaseParameters seenParameters = null;

  @Inject
  public JavaChangeCaseTestBackfill(){

  }

  @NotNull @Override
  public ValidateResult<CaseParameters> checkBackfillConfig(@NotNull BackfillConfig<CaseParameters> backfillConfig) {
    seenParameters = backfillConfig.getParameters();
    return super.checkBackfillConfig(backfillConfig);
  }

  @Override public void runOne(@NotNull FixedSetRow row,
      @NotNull BackfillConfig<CaseParameters> backfillConfig) {
    seenParameters = backfillConfig.getParameters();
    runOrder.add(row.getValue());
    if (backfillConfig.getParameters().toUpper) {
      row.setValue(row.getValue().toUpperCase(Locale.ROOT));
    } else {
      row.setValue(row.getValue().toLowerCase(Locale.ROOT));
    }
  }
}

