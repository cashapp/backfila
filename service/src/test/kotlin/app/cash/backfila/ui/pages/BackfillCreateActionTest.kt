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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
    val page = render("ChickenSandwich")

    assertThat(page.isChecked("dryRun")).isTrue()
    assertThat(page.valueOf("batchSize")).isEqualTo("100")
    assertThat(page.valueOf("scanSize")).isEqualTo("10000")
    assertThat(page.valueOf("rangeStart")).isNull()
    assertThat(page.valueOf("customParameter_param1")).isNull()
  }

  @Test
  fun `query parameters pre-fill the create form`() {
    val query = "dryRun=on&rangeStart=10&rangeEnd=20&batchSize=250&scanSize=5000&threadsPerPartition=4" +
      "&extraSleepMs=7&backoffSchedule=100,200&customParameter_param1=%22%3E%3Cscript%3E"
    val page = render("ChickenSandwich", query)

    assertThat(page.isChecked("dryRun")).isTrue()
    assertThat(page.valueOf("rangeStart")).isEqualTo("10")
    assertThat(page.valueOf("rangeEnd")).isEqualTo("20")
    assertThat(page.valueOf("batchSize")).isEqualTo("250")
    assertThat(page.valueOf("scanSize")).isEqualTo("5000")
    assertThat(page.valueOf("threadsPerPartition")).isEqualTo("4")
    assertThat(page.valueOf("extraSleepMs")).isEqualTo("7")
    assertThat(page.valueOf("backoffSchedule")).isEqualTo("100,200")
    // jsoup decodes entities, so the escaping itself can only be seen in the raw HTML.
    assertThat(page.valueOf("customParameter_param1")).isEqualTo("\"><script>")
    assertThat(renderHtml("ChickenSandwich", query)).contains("value=\"&quot;&gt;&lt;script&gt;\"")
  }

  @Test
  fun `dryRun off or false unchecks the box`() {
    assertThat(render("ChickenSandwich", "dryRun=off").isChecked("dryRun")).isFalse()
    assertThat(render("ChickenSandwich", "dryRun=false").isChecked("dryRun")).isFalse()
    assertThat(render("ChickenSandwich", "dryRun=FALSE").isChecked("dryRun")).isFalse()
  }

  @Test
  fun `blank or invalid query parameters fall back to defaults`() {
    val page = render(
      "ChickenSandwich",
      "dryRun=&rangeStart=&batchSize=&scanSize=abc&threadsPerPartition=1.5&extraSleepMs=0007" +
        "&backoffSchedule=&customParameter_param1=",
    )

    assertThat(page.isChecked("dryRun")).isTrue()
    assertThat(page.valueOf("rangeStart")).isNull()
    assertThat(page.valueOf("batchSize")).isEqualTo("100")
    assertThat(page.valueOf("scanSize")).isEqualTo("10000")
    assertThat(page.valueOf("threadsPerPartition")).isEqualTo("1")
    assertThat(page.valueOf("extraSleepMs")).isEqualTo("7")
    assertThat(page.valueOf("backoffSchedule")).isNull()
    assertThat(page.valueOf("customParameter_param1")).isNull()
  }

  @Test
  fun `blank query parameters keep cloned values`() {
    val backfillId = createRun()

    val page = render(backfillId, "dryRun=&batchSize=&scanSize=abc&customParameter_param1=&rangeStart=&rangeEnd=")
    assertThat(page.isChecked("dryRun")).isFalse()
    assertThat(page.valueOf("batchSize")).isEqualTo("123")
    assertThat(page.valueOf("scanSize")).isEqualTo("223")
    assertThat(page.valueOf("customParameter_param1")).isEqualTo("val1")
    assertThat(page.isChecked("range-option-restart")).isTrue()
    assertThat(page.rangeInputsStyle()).isEqualTo("display: none;")
  }

  @Test
  fun `unknown custom parameters are ignored`() {
    val page = render("ChickenSandwich", "customParameter_unknown=surprise")

    assertThat(page.select("input[name^=customParameter_]").map { it.attr("name") })
      .containsExactly("customParameter_param1")
    assertThat(page.select("[value=surprise]")).isEmpty()
  }

  @Test
  fun `query parameters take precedence over cloned values`() {
    val backfillId = createRun()

    val cloned = render(backfillId)
    assertThat(cloned.isChecked("dryRun")).isFalse()
    assertThat(cloned.valueOf("batchSize")).isEqualTo("123")
    assertThat(cloned.valueOf("customParameter_param1")).isEqualTo("val1")
    assertThat(cloned.isChecked("range-option-restart")).isTrue()
    assertThat(cloned.rangeInputsStyle()).isEqualTo("display: none;")

    val prefilled = render(backfillId, "dryRun=on&batchSize=250&customParameter_param1=val2&rangeStart=5")
    assertThat(prefilled.isChecked("dryRun")).isTrue()
    assertThat(prefilled.valueOf("batchSize")).isEqualTo("250")
    assertThat(prefilled.valueOf("customParameter_param1")).isEqualTo("val2")
    assertThat(prefilled.valueOf("scanSize")).isEqualTo("223")
    assertThat(prefilled.isChecked("range-option-restart")).isFalse()
    assertThat(prefilled.isChecked("range-option-new")).isTrue()
    assertThat(prefilled.rangeInputsStyle()).isEqualTo("display: block;")
    assertThat(prefilled.valueOf("rangeStart")).isEqualTo("5")
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

  private fun render(backfillNameOrId: String, query: String = ""): Document =
    Jsoup.parse(renderHtml(backfillNameOrId, query))

  private fun renderHtml(backfillNameOrId: String, query: String = ""): String {
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

  private fun Document.input(id: String): Element =
    selectFirst("input#$id") ?: throw AssertionError("No input with id $id")

  private fun Document.valueOf(id: String): String? = input(id).takeIf { it.hasAttr("value") }?.attr("value")

  private fun Document.isChecked(id: String): Boolean = input(id).hasAttr("checked")

  private fun Document.rangeInputsStyle(): String? = selectFirst("#range-inputs")?.attr("style")

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
