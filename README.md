# Deployagram Android AppA Kotest Demo

A small Android app, AppA, and its emulator tests. It shows how to test an Android app's own networking
with **Kotest** and **MockWebServer**: the test drives the screen, the app makes the HTTP call, and a
MockWebServer double stands in for the service the app calls.

It is one of the [Deployagram](https://deployagram.com) demos. The app itself is not about Deployagram.

## What it does

AppA has one screen. The user types an email address and presses **Recommend book**. The app asks
BookOverflow for a book suggestion, and shows the answer on the screen.

```
GET /BookOverflow/suggestion?email=<the email the user typed>
```

| Situation                                        | What the screen shows                                              |
|--------------------------------------------------|--------------------------------------------------------------------|
| BookOverflow suggests a book                     | The book's name, edition, format and authors                       |
| Email is blank, and BookOverflow refuses (400)   | `You must enter a valid email!`                                    |
| Any other failure                                | `Sorry, no suggestion right now (<reason>)`                        |

The app calls BookOverflow at `http://10.0.2.2:8080` by default. `10.0.2.2` is how the Android emulator
reaches the host machine. Tests replace that address with the address of a double.

## What it is for

The app is a stand-in for a real Android app. The demo shows how to:

* drive an Android screen from a test with **Espresso**, without the test making any HTTP request itself;
* stand in for a service the app calls, with a **MockWebServer** double;
* write the test in **Kotest** (`FunSpec`), run on the device by the JUnit 4 runner;
* check what the app sent to the double, and what the screen shows, in the same test.

The tests never send an HTTP request. They type an email and press the button, and AppA makes the
request. The double answers, and the test checks both sides.

## Tech

* Kotlin, Android Gradle Plugin 8.9.0, Gradle 8.12 (checked-in wrapper), JDK 17 to build
* Min SDK 26, compile and target SDK 36
* OkHttp 5.3.2 in the app, to call BookOverflow
* Kotest 6.1.2 (`kotest-runner-junit4`, `FunSpec`) for the tests
* MockWebServer 5.3.2 for the BookOverflow double
* Espresso 3.6.1 and AndroidX Test (`AndroidJUnitRunner`) to drive the screen

## Project layout

```
app/src/main/java/com/deployagram/demo/appa/
    MainActivity.kt          the one screen: email field, button, result
    BookOverflowClient.kt    GET /BookOverflow/suggestion, with OkHttp, and the error for a refused request
    BookSuggestion.kt        the suggestion, and how it is shown
    BookOverflowConfig.kt    where BookOverflow is (the tests change it)
app/src/debug/               cleartext HTTP allowed to localhost, 127.0.0.1 and 10.0.2.2, debug builds only
app/src/androidTest/java/com/deployagram/demo/appa/
    RecommendBookScreenTest.kt   the two emulator tests, and the BookOverflow double
```

## Run the app

Open the project in Android Studio, or run it from the command line:

```bash
./gradlew installDebug
```

Start a BookOverflow service on the host at port 8080, with a GET /BookOverflow/suggestion endpoint, to get real suggestions. Without it, the app shows the
"Sorry, no suggestion right now" message.

## Run the tests

The tests run on an Android emulator. Create one with API 36 (the demo uses `Medium_Phone_API_36`), start
it, and run:

```bash
./gradlew connectedDebugAndroidTest
```

The tests need the emulator's screen to be awake and unlocked, and no system dialog on top of the
app. If Espresso reports that the window never got focus, dismiss the dialog and run again.

### What the tests check

`RecommendBookScreenTest` has two tests:

* **Pressing Recommend book asks BookOverflow for a suggestion and shows it.** BookOverflow suggests a
  book. The test checks that BookOverflow was asked for the email the test typed, and that the screen
  shows the book.
* **Pressing Recommend book with no email asks BookOverflow, which refuses, and asks for a valid email.**
  The test leaves the email blank. The double refuses a request with no email, with a 400. The test
  checks that BookOverflow was asked with a blank email, and that the screen shows
  `You must enter a valid email!`.

The double's refusal of a blank email is a rule of this demo's double. The Java BookOverflow demo in
this repository accepts a blank email.

Each test sets up the double in a **given** step, presses the button in a **when** step, and checks the
result in **then** steps. Each step is a small named function, and a test body reads as a sentence.
