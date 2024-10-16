/*
 * Copyright (C) 2022 Block, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.cash.backfila.client.sqldelight.plugin

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class BackfilaSqlDelightGradlePluginTest {
  @Test
  fun happyPath() {
    val projectDir = File("src/test/projects/happyPath")

    val taskName = ":lib:compileKotlin"
    val result = createRunner(projectDir, "clean", taskName).build()
    assertThat(SUCCESS_OUTCOMES)
      .contains(result.task(taskName)!!.outcome)
  }

  private fun createRunner(
    projectDir: File,
    vararg taskNames: String,
  ): GradleRunner {
    val arguments = arrayOf("--info", "--stacktrace", "--continue")
    return GradleRunner.create()
      .withProjectDir(projectDir)
      .withDebug(true) // Run in-process.
      .withArguments(*arguments, *taskNames, versionProperty)
      .forwardOutput()
  }

  companion object {
    val SUCCESS_OUTCOMES = listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
    val versionProperty = "-PbackfilaVersion=${System.getProperty("backfilaVersion")}"
  }
}
