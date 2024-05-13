package app.cash.backfila.embedded.annotations;

import app.cash.backfila.client.fixedset.FixedSetBackend;
import app.cash.backfila.client.spi.BackfilaParametersOperator;

import app.cash.backfila.embedded.JavaBackfila;
import app.cash.backfila.protos.service.ConfigureServiceRequest;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import kotlin.jvm.JvmClassMappingKt;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test that too many annotations fails.
 */
@MiskTest(startService = true)
public class TooManyAnnotationsTest {
  @MiskTestModule
  TooManyAnnotationsModule module = new TooManyAnnotationsModule();

  @Inject JavaBackfila backfila;

  @Inject FixedSetBackend backend;

  @Test
  void checkForAnnotationBackfills() {
    ConfigureServiceRequest configureServiceData = backfila.getConfigureServiceData();
    // Check that the service failed to register to backfila
    assertThat(configureServiceData).isNull();

    // Check that the backfills were registered to the backend though.
    assertThat(backend.backfills().stream().map(it -> it.getName()).collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(List.of("app.cash.backfila.embedded.annotations.TwoAnnotationParameterBackfill",
            "app.cash.backfila.embedded.annotations.ThreeAnnotationParameterBackfill"));

    // And that collecting parameters fail.
    var twoParameterException = assertThrows(IllegalStateException.class, () -> {
      BackfilaParametersOperator.Companion.backfilaParametersFromClass(JvmClassMappingKt.getKotlinClass(TwoAnnotationParameterBackfill.TwoAnnotationParameter.class));
    });
    assertThat(twoParameterException).hasMessageContaining("Only one of @BackfilaRequired, @BackfilaDefault, or @BackfilaNullDefault can be set on each constructor Parameter.");

    var threeParameterException = assertThrows(IllegalStateException.class, () -> {
      BackfilaParametersOperator.Companion.backfilaParametersFromClass(JvmClassMappingKt.getKotlinClass(ThreeAnnotationParameterBackfill.ThreeAnnotationParameter.class));
    });
    assertThat(threeParameterException).hasMessageContaining("Only one of @BackfilaRequired, @BackfilaDefault, or @BackfilaNullDefault can be set on each constructor Parameter.");
  }
}
