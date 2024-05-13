package app.cash.backfila.embedded;

import app.cash.backfila.client.BackfilaDefault;
import app.cash.backfila.client.BackfilaNullDefault;
import app.cash.backfila.client.BackfilaRequired;
import app.cash.backfila.client.Description;

public class CaseParameters {
  public final boolean toUpper;
  public final long testLong;
  public final int testInt;
  public final boolean testBool;
  public final String testNullString;
  public final Integer testNullInt;
  public final Boolean testNullBoolean;
  public final String required;

  public CaseParameters(
      @Description(text = "Whether to change case to upper case or lower case.")
      @BackfilaDefault(name = "casing", value = "upper") String casing,
      @BackfilaDefault(name = "testLong", value = "123") long testLong,
      @BackfilaDefault(name = "testInt", value = "789") int testInt,
      @BackfilaDefault(name = "testBool", value = "false") boolean testBool,
      @BackfilaNullDefault(name = "testNullString") String testNullString,
      @BackfilaNullDefault(name = "testNullInt") Integer testNullInt,
      @BackfilaNullDefault(name = "testNullBoolean") Boolean testNullBoolean,
      @BackfilaRequired(name = "required") String required) {
    if ("upper".equalsIgnoreCase(casing)) {
      toUpper = true;
    } else if ("lower".equalsIgnoreCase(casing)) {
      toUpper = false;
    } else {
      throw new IllegalArgumentException(String.format("Invalid casing string. %s", casing));
    }
    this.testLong = testLong;
    this.testInt = testInt;
    this.testBool = testBool;
    this.testNullString = testNullString;
    this.testNullInt = testNullInt;
    this.testNullBoolean = testNullBoolean;
    this.required = required;
  }
}
