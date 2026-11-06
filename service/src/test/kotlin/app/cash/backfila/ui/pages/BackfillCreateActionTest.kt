package app.cash.backfila.ui.pages

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.Parameter
import jakarta.inject.Inject
import javax.servlet.http.HttpServletRequest
import misk.Action
import misk.MiskCaller
import misk.api.HttpRequest
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.scope.ActionScope
import misk.scope.ActionScopedProviderModule
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.dashboard.BaseDashboardModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfillCreateActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var backfillCreateAction: BackfillCreateAction

  @Inject lateinit var scope: ActionScope

  @BeforeEach
  fun setup() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .parameters(listOf(Parameter.Builder().name("param1").build()))
                .build(),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
  }

  @Test
  fun `defaults are used without query parameters`() {
    val html = render("ChickenSandwich")

    assertThat(inputTag(html, "dryRun")).contains("checked=\"checked\"")
    assertThat(inputTag(html, "batchSize")).contains("value=\"100\"")
    assertThat(inputTag(html, "scanSize")).contains("value=\"10000\"")
    assertThat(inputTag(html, "rangeStart")).doesNotContain("value=")
    assertThat(inputTag(html, "customParameter_param1")).doesNotContain("value=")
  }

  @Test
  fun `query parameters pre-fill the create form`() {
    val html = render(
      "ChickenSandwich",
      "dryRun=on&rangeStart=10&rangeEnd=20&batchSize=250&scanSize=5000&threadsPerPartition=4" +
        "&extraSleepMs=7&backoffSchedule=100,200&customParameter_param1=%22%3E%3Cscript%3E",
    )

    assertThat(inputTag(html, "dryRun")).contains("checked=\"checked\"")
    assertThat(inputTag(html, "rangeStart")).contains("value=\"10\"")
    assertThat(inputTag(html, "rangeEnd")).contains("value=\"20\"")
    assertThat(inputTag(html, "batchSize")).contains("value=\"250\"")
    assertThat(inputTag(html, "scanSize")).contains("value=\"5000\"")
    assertThat(inputTag(html, "threadsPerPartition")).contains("value=\"4\"")
    assertThat(inputTag(html, "extraSleepMs")).contains("value=\"7\"")
    assertThat(inputTag(html, "backoffSchedule")).contains("value=\"100,200\"")
    assertThat(inputTag(html, "customParameter_param1")).contains("value=\"&quot;&gt;&lt;script&gt;\"")
    assertThat(html).doesNotContain("\"><script>")
  }

  @Test
  fun `dryRun off or false unchecks the box`() {
    assertThat(inputTag(render("ChickenSandwich", "dryRun=off"), "dryRun")).doesNotContain("checked=\"checked\"")
    assertThat(inputTag(render("ChickenSandwich", "dryRun=false"), "dryRun")).doesNotContain("checked=\"checked\"")
    assertThat(inputTag(render("ChickenSandwich", "dryRun=FALSE"), "dryRun")).doesNotContain("checked=\"checked\"")
  }

  @Test
  fun `parseCheckbox matches the create handler`() {
    assertThat(BackfillCreateAction.parseCheckbox(null)).isNull()
    assertThat(BackfillCreateAction.parseCheckbox(" ")).isNull()
    assertThat(BackfillCreateAction.parseCheckbox("off")).isFalse()
    assertThat(BackfillCreateAction.parseCheckbox("False")).isFalse()
    assertThat(BackfillCreateAction.parseCheckbox("on")).isTrue()
    assertThat(BackfillCreateAction.parseCheckbox("true")).isTrue()
  }

  @Test
  fun `blank or invalid query parameters fall back to defaults`() {
    val html = render(
      "ChickenSandwich",
      "dryRun=&rangeStart=&batchSize=&scanSize=abc&threadsPerPartition=1.5&extraSleepMs=0007" +
        "&backoffSchedule=&customParameter_param1=",
    )

    assertThat(inputTag(html, "dryRun")).contains("checked=\"checked\"")
    assertThat(inputTag(html, "rangeStart")).doesNotContain("value=")
    assertThat(inputTag(html, "batchSize")).contains("value=\"100\"")
    assertThat(inputTag(html, "scanSize")).contains("value=\"10000\"")
    assertThat(inputTag(html, "threadsPerPartition")).contains("value=\"1\"")
    assertThat(inputTag(html, "extraSleepMs")).contains("value=\"7\"")
    assertThat(inputTag(html, "backoffSchedule")).doesNotContain("value=")
    assertThat(inputTag(html, "customParameter_param1")).doesNotContain("value=")
  }

  @Test
  fun `blank query parameters keep cloned values`() {
    val backfillId = createRun()

    val html = render(backfillId, "dryRun=&batchSize=&scanSize=abc&customParameter_param1=&rangeStart=&rangeEnd=")
    assertThat(inputTag(html, "dryRun")).doesNotContain("checked=\"checked\"")
    assertThat(inputTag(html, "batchSize")).contains("value=\"123\"")
    assertThat(inputTag(html, "scanSize")).contains("value=\"223\"")
    assertThat(inputTag(html, "customParameter_param1")).contains("value=\"val1\"")
    assertThat(inputTag(html, "range-option-restart")).contains("checked=\"checked\"")
    assertThat(html).contains("id=\"range-inputs\" style=\"display: none;\"")
  }

  @Test
  fun `unknown custom parameters are ignored`() {
    val html = render("ChickenSandwich", "customParameter_unknown=surprise")

    assertThat(html).doesNotContain("customParameter_unknown")
    assertThat(html).doesNotContain("surprise")
  }

  @Test
  fun `query parameters take precedence over cloned values`() {
    val backfillId = createRun()

    val cloned = render(backfillId)
    assertThat(inputTag(cloned, "dryRun")).doesNotContain("checked=\"checked\"")
    assertThat(inputTag(cloned, "batchSize")).contains("value=\"123\"")
    assertThat(inputTag(cloned, "customParameter_param1")).contains("value=\"val1\"")
    assertThat(inputTag(cloned, "range-option-restart")).contains("checked=\"checked\"")
    assertThat(cloned).contains("id=\"range-inputs\" style=\"display: none;\"")

    val prefilled = render(backfillId, "dryRun=on&batchSize=250&customParameter_param1=val2&rangeStart=5")
    assertThat(inputTag(prefilled, "dryRun")).contains("checked=\"checked\"")
    assertThat(inputTag(prefilled, "batchSize")).contains("value=\"250\"")
    assertThat(inputTag(prefilled, "customParameter_param1")).contains("value=\"val2\"")
    assertThat(inputTag(prefilled, "scanSize")).contains("value=\"223\"")
    assertThat(inputTag(prefilled, "range-option-restart")).doesNotContain("checked=\"checked\"")
    assertThat(inputTag(prefilled, "range-option-new")).contains("checked=\"checked\"")
    assertThat(prefilled).contains("id=\"range-inputs\" style=\"display: block;\"")
    assertThat(inputTag(prefilled, "rangeStart")).contains("value=\"5\"")
  }

  private fun createRun(): String = scope.fakeCaller(user = "molly") {
    createBackfillAction.create(
      "deep-fryer",
      RESERVED_VARIANT,
      CreateBackfillRequest.Builder()
        .backfill_name("ChickenSandwich")
        .batch_size(123)
        .scan_size(223)
        .dry_run(false)
        .parameter_map(mapOf("param1" to "val1".encodeUtf8()))
        .build(),
    ).backfill_run_id.toString()
  }

  private fun render(backfillNameOrId: String, query: String = ""): String {
    val url = "http://backfila.test/backfills/create/deep-fryer/$backfillNameOrId/?$query".toHttpUrl()
    return scope.create(
      mapOf(
        HttpCall::class.toKey() to FakeHttpCall(url = url),
        MiskCaller::class.toKey() to MiskCaller(user = "molly"),
      ),
    ).inScope {
      val buffer = Buffer()
      backfillCreateAction.get("deep-fryer", backfillNameOrId, "").body.writeTo(buffer)
      buffer.readUtf8()
    }
  }

  private fun inputTag(html: String, nameOrId: String): String =
    Regex("""<input[^>]*(name|id)="$nameOrId"[^>]*>""").find(html)?.value
      ?: throw AssertionError("No input named $nameOrId")

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(BackfilaTestingModule())
      install(BaseDashboardModule(true))
      install(
        object : ActionScopedProviderModule() {
          override fun configureProviders() {
            bindSeedData(HttpCall::class)
            bindSeedData(HttpRequest::class)
            bindSeedData(HttpServletRequest::class)
            bindSeedData(Action::class)
            newMultibinder<MiskCallerAuthenticator>()
          }
        },
      )
    }
  }
}
