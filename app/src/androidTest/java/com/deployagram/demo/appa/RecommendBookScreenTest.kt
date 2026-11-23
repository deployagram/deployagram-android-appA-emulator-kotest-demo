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
 * Emulator tests of AppA's "Recommend book" screen.
 *
 * The tests never send an HTTP request themselves. They type an email and press the button, and AppA makes the
 * request to BookOverflow. BookOverflow is a MockWebServer double, and the tests check what the double received
 * and what the screen shows.
 */
class RecommendBookScreenTest : FunSpec({

    lateinit var bookOverflow: MockWebServer
    lateinit var bookOverflowAnswers: SuggestionAnswers
    lateinit var enteredEmail: String
    var screen: ActivityScenario<MainActivity>? = null

    beforeTest {
        bookOverflow = MockWebServer()
        bookOverflowAnswers = SuggestionAnswers()
        enteredEmail = "reader-${UUID.randomUUID()}@example.com"
        bookOverflow.dispatcher = bookOverflowAnswers
        bookOverflow.start()
        BookOverflowConfig.baseUrl = bookOverflow.url("/").toString().trimEnd('/')
    }

    afterTest {
        screen?.close()
        screen = null
        bookOverflow.shutdown()
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

    fun givenBookOverflowRefusesARequestWithNoEmail() {
        bookOverflowAnswers.refusesRequestsWithNoEmail = true
    }

    fun givenNoEmailIsEntered() {
        enteredEmail = ""
    }

    // When

    fun whenTheRecommendBookButtonIsPressed() {
        screen = ActivityScenario.launch(MainActivity::class.java)
        onView(withHint("Email address"))
            .perform(replaceText(enteredEmail), closeSoftKeyboard())
        onView(withText("Recommend book"))
            .perform(click())
    }

    // Then

    suspend fun thenTheSuggestionIsShown() {
        // AppA makes the request in the background, so the screen is checked until the result appears.
        eventually(10.seconds) {
            onView(withText(containsString("Refactoring")))
                .check(matches(isDisplayed()))
        }
    }

    suspend fun thenTheInvalidEmailPromptIsShown() {
        eventually(10.seconds) {
            onView(withText("You must enter a valid email!"))
                .check(matches(isDisplayed()))
        }
    }

    fun thenBookOverflowWasAskedForTheEnteredEmail() {
        val request = bookOverflow.takeRequest(5, TimeUnit.SECONDS)
            ?: error("BookOverflow was not asked for a suggestion for \"$enteredEmail\"")
        request.requestUrl?.encodedPath shouldBe "/BookOverflow/suggestion"
        request.requestUrl?.queryParameter("email") shouldBe enteredEmail
    }

    fun thenBookOverflowWasCalledWithoutAnEmailAddress() {
        val request = bookOverflow.takeRequest(5, TimeUnit.SECONDS)
            ?: error("BookOverflow was not asked for a suggestion")
        request.requestUrl?.encodedPath shouldBe "/BookOverflow/suggestion"
        request.requestUrl?.queryParameter("email") shouldBe ""
    }

    // Tests

    test("pressing Recommend book asks BookOverflow for a suggestion and shows it") {
        givenBookOverflowHasASuggestion()

        whenTheRecommendBookButtonIsPressed()

        thenTheSuggestionIsShown()
        thenBookOverflowWasAskedForTheEnteredEmail()
    }

    test("pressing Recommend book with no email asks BookOverflow, which refuses, and asks for a valid email") {
        givenBookOverflowRefusesARequestWithNoEmail()
        givenNoEmailIsEntered()

        whenTheRecommendBookButtonIsPressed()

        thenBookOverflowWasCalledWithoutAnEmailAddress()
        thenTheInvalidEmailPromptIsShown()
    }
})

// What BookOverflow answers. A test sets what it wants with the given steps.
// Until then, BookOverflow has no suggestion and answers 404.
class SuggestionAnswers : Dispatcher() {
    @Volatile
    var suggestionJson: String? = null

    // When true, a request without an email is refused with 400, as BookOverflow refuses it.
    @Volatile
    var refusesRequestsWithNoEmail = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (refusesRequestsWithNoEmail && request.requestUrl?.queryParameter("email").isNullOrBlank()) {
            return MockResponse().setResponseCode(400)
        }
        val json = suggestionJson ?: return MockResponse().setResponseCode(404)
        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(json)
    }
}
