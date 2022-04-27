package app.cash.backfila.embedded;

import app.cash.backfila.client.BackfilaDefault;
import app.cash.backfila.client.BackfilaRequired;

public class CaseParameters {
  public final boolean toUpper;
  public final long testLong;
  public final int testInt;
  public final boolean testBool;

  public CaseParameters(
      @BackfilaDefault(name = "casing", value = "upper") String casing,
      @BackfilaDefault(name = "testLong", value = "123") long testLong,
      @BackfilaDefault(name = "testInt", value = "789") int testInt,
      @BackfilaDefault(name = "testBool", value = "false") boolean testBool,
      @BackfilaRequired(name = "required") String requiredParameter) {
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
  }
}
