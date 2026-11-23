package com.deployagram.demo.appa

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.deployagram.mockwebserver.RecordingDispatcher
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.FunSpec
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.hamcrest.CoreMatchers.containsString
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Emulator tests of AppA's "Load Config" button, which calls a second back-end, AliceMessager.
 *
 * The tests never send an HTTP request themselves. They press the buttons, and AppA makes the requests. Both
 * back-ends are MockWebServer doubles, and the tests check what each double received and what the screen shows.
 */
class LoadConfigButtonTest : FunSpec({

    lateinit var bookOverflow: MockWebServer
    lateinit var bookOverflowAnswers: SuggestionAnswers
    lateinit var aliceMessager: MockWebServer
    lateinit var enteredEmail: String
    var screen: ActivityScenario<MainActivity>? = null

    beforeTest {
        enteredEmail = "reader-${UUID.randomUUID()}@example.com"

        bookOverflowAnswers = SuggestionAnswers()
        bookOverflow = MockWebServer()
        bookOverflow.dispatcher = RecordingDispatcher(
            bookOverflowAnswers,
            "AliceA",
            "ORG_ctm_01m1j08sx96792d2tysabvyadp:BookOverflow",
        )
        bookOverflow.start()
        BookOverflowConfig.baseUrl = bookOverflow.url("/").toString().trimEnd('/')

        aliceMessager = MockWebServer()
        aliceMessager.dispatcher = RecordingDispatcher(AliceMessagerAnswers(), "AliceA", "AliceMessager")
        aliceMessager.start()
        AliceMessagerConfig.baseUrl = aliceMessager.url("/").toString().trimEnd('/')
    }

    afterTest {
        screen?.close()
        screen = null
        bookOverflow.shutdown()
        aliceMessager.shutdown()
        BookOverflowConfig.baseUrl = "http://10.0.2.2:8080"
        AliceMessagerConfig.baseUrl = "http://10.0.2.2:8081"
    }

    // Given

    fun givenBookOverflowHasASuggestion() {
        bookOverflowAnswers.suggestionJson = """
            {
              "name": "Refactoring",
              "edition": "1st",
              "format": "paperback",
              "authors": ["Fowler Martin", "Beck Kent"]
            }
        """.trimIndent()
    }

    // When

    fun whenARecommendationIsMade() {
        screen = ActivityScenario.launch(MainActivity::class.java)
        onView(withHint("Email address"))
            .perform(replaceText(enteredEmail), closeSoftKeyboard())
        onView(withText("Recommend book"))
            .perform(click())
    }

    fun whenTheLoadConfigButtonIsPressed() {
        onView(withText("Load Config"))
            .perform(click())
    }

    // Then

    suspend fun thenTheSuggestionIsShown() {
        eventually(10.seconds) {
            onView(withText(containsString("Refactoring")))
                .check(matches(isDisplayed()))
        }
    }

    suspend fun thenTheReplyIsShown(reply: String) {
        eventually(10.seconds) {
            onView(withText(reply))
                .check(matches(isDisplayed()))
        }
    }

    fun thenAliceMessagerWasSentTheConfigRequest(expectedBody: String?) {
        val request = aliceMessager.takeRequest(5, TimeUnit.SECONDS)
            ?: error("AliceMessager was not sent a request")
        request.path shouldBe "/AliceMessager/config"
        val body = request.body.clone().readUtf8()
        if (expectedBody == null) {
            body shouldBe ""
        } else {
            body shouldBe expectedBody
        }
    }

    // Tests

    test("pressing Load Config after a recommendation sends Hello and shows World") {
        givenBookOverflowHasASuggestion()

        whenARecommendationIsMade()
        thenTheSuggestionIsShown()
        whenTheLoadConfigButtonIsPressed()

        thenTheReplyIsShown("World")
        thenAliceMessagerWasSentTheConfigRequest(AliceMessagerAnswers.HELLO)
    }

    test("pressing Load Config with no recommendation sends an empty payload and shows the reply") {
        screen = ActivityScenario.launch(MainActivity::class.java)

        whenTheLoadConfigButtonIsPressed()

        thenTheReplyIsShown("Is someone there?")
        thenAliceMessagerWasSentTheConfigRequest(null)
    }
})

// What AliceMessager answers. A POST with {"message":"Hello"} gets 200 with {"message":"World"}. Any other payload,
// including an empty one, gets 400 with {"message":"Is someone there?"}.
class AliceMessagerAnswers : Dispatcher() {

    companion object {
        const val HELLO = "{\"message\":\"Hello\"}"
        const val WORLD = "{\"message\":\"World\"}"
        const val IS_SOMEONE_THERE = "{\"message\":\"Is someone there?\"}"
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val json = "application/json"
        return if (request.body.clone().readUtf8() == HELLO) {
            MockResponse().setResponseCode(200).setHeader("Content-Type", json).setBody(WORLD)
        } else {
            MockResponse().setResponseCode(400).setHeader("Content-Type", json).setBody(IS_SOMEONE_THERE)
        }
    }
}
