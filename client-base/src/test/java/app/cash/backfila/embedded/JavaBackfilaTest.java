package app.cash.backfila.embedded;

import app.cash.backfila.client.fixedset.FixedSetDatastore;
import app.cash.backfila.protos.service.Parameter;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import misk.testing.MiskTest;
import misk.testing.MiskTestModule;
import okio.ByteString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
  void happyDefaults() {
    var backfillData = backfila.getConfigureServiceData().backfills
        .stream().filter(it -> it.name.equals(JavaChangeCaseTestBackfill.class.getCanonicalName())).findFirst();
    var toUpperParameter = backfillData.get().parameters.stream().filter(it -> it.name.equals("casing")).findFirst();
    assertThat(toUpperParameter.get().description).isEqualTo("Whether to change case to upper case or lower case.");
    var parameterList = backfillData.get().parameters.stream().map(it -> it.name).collect(Collectors.toList());
    assertThat(parameterList).containsExactly(
        "casing",
        "testLong",
        "testInt",
        "testBool",
        "testNullString",
        "testNullInt",
        "testNullBoolean",
        "required"
    );

    datastore.put("instance", "a", "B", "c");

    var backfillRun = backfila.createWetRun(JavaChangeCaseTestBackfill.class,
        Map.of("required", ByteString.encodeUtf8("isRequired")));
    backfillRun.execute();
    assertThat(backfillRun.getBackfill().runOrder).containsExactly("a", "B", "c");
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C");
    CaseParameters seenParameters = backfillRun.getBackfill().seenParameters;
    assertThat(seenParameters.toUpper).isEqualTo(true);
    assertThat(seenParameters.testLong).isEqualTo(123);
    assertThat(seenParameters.testInt).isEqualTo(789);
    assertThat(seenParameters.testBool).isEqualTo(false);
    assertThat(seenParameters.testNullString).isNull();
    assertThat(seenParameters.testNullInt).isNull();
    assertThat(seenParameters.testNullBoolean).isNull();
    assertThat(seenParameters.required).isEqualTo("isRequired");
  }

  @Test
  void lowerCase() {
    datastore.put("instance", "A", "b", "C");

    var backfillRun = backfila.createWetRun(JavaChangeCaseTestBackfill.class,
        Map.of(
            "casing", ByteString.encodeUtf8("lower"),
            "required", ByteString.encodeUtf8("isRequired")
        ));
    backfillRun.execute();
    assertThat(backfillRun.getBackfill().runOrder).containsExactly("A", "b", "C");
    assertThat(datastore.valuesToList()).containsExactly("a", "b", "c");
    CaseParameters seenParameters = backfillRun.getBackfill().seenParameters;
    assertThat(seenParameters.toUpper).isEqualTo(false);
    assertThat(seenParameters.testLong).isEqualTo(123);
    assertThat(seenParameters.testInt).isEqualTo(789);
    assertThat(seenParameters.testBool).isEqualTo(false);
    assertThat(seenParameters.testNullString).isNull();
    assertThat(seenParameters.testNullInt).isNull();
    assertThat(seenParameters.testNullBoolean).isNull();
    assertThat(seenParameters.required).isEqualTo("isRequired");
  }

  @Test
  void settingAllParameters() {
    datastore.put("instance", "A", "b", "C");

    var backfillRun = backfila.createWetRun(JavaChangeCaseTestBackfill.class,
        Map.of(
            "casing", ByteString.encodeUtf8("lower"),
            "testLong", ByteString.encodeUtf8("456"),
            "testInt", ByteString.encodeUtf8("1011"),
            "testBool", ByteString.encodeUtf8("true"),
            "testNullString", ByteString.encodeUtf8("Not null this time"),
            "testNullInt", ByteString.encodeUtf8("9876"),
            "testNullBoolean", ByteString.encodeUtf8("false"),
            "required", ByteString.encodeUtf8("isRequired")
        ));
    CaseParameters seenParameters = backfillRun.getBackfill().seenParameters;
    assertThat(seenParameters.toUpper).isEqualTo(false);
    assertThat(seenParameters.testLong).isEqualTo(456);
    assertThat(seenParameters.testInt).isEqualTo(1011);
    assertThat(seenParameters.testBool).isEqualTo(true);
    assertThat(seenParameters.testNullString).isEqualTo("Not null this time");
    assertThat(seenParameters.testNullInt).isEqualTo(9876);
    assertThat(seenParameters.testNullBoolean).isEqualTo(false);
    assertThat(seenParameters.required).isEqualTo("isRequired");
  }

  @Test
  void missingRequiredParameter() {
    datastore.put("instance", "A", "b", "C");

    var ex = assertThrows(IllegalArgumentException.class, () -> {
      backfila.createWetRun(JavaChangeCaseTestBackfill.class,
          Map.of(
              "casing", ByteString.encodeUtf8("upper")
          ));
    });

    assertThat(ex).hasMessageContaining("Parameter data class has a required member required with no provided value.");
  }

  @Test
  void parametersException() {
    datastore.put("instance", "A", "b", "C");

    var ex = assertThrows(IllegalArgumentException.class, () -> {
      backfila.createWetRun(JavaChangeCaseTestBackfill.class,
          Map.of(
              "casing", ByteString.encodeUtf8("error"),
              "required", ByteString.encodeUtf8("isRequired")
          ));
    });

    assertThat(ex).hasMessageContaining("Failed to create Parameter object");
  }

  @Test
  void twoInstanceBackfill() {
    datastore.put("instance-1", "a", "b", "c");
    datastore.put("instance-2", "e", "f");

    var backfillRun = backfila.createWetRun(JavaChangeCaseTestBackfill.class,
        Map.of("required", ByteString.encodeUtf8("isRequired")));
    backfillRun.execute();
    assertThat(backfillRun.getBackfill().runOrder).containsExactly("a", "b", "c", "e", "f");
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C", "E", "F");
  }

  @Test
  void backfillIdIsANumber() {
    datastore.put("instance", "A", "b", "C");

    var backfillRun = backfila.createWetRun(JavaChangeCaseTestBackfill.class,
        Map.of("required", ByteString.encodeUtf8("isRequired")));
    backfillRun.execute();
    String seenBackfillId = backfillRun.getBackfill().seenBackfillId;
    assertThat(Integer.parseInt(seenBackfillId)).isNotNull();
  }
}
