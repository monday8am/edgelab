---
name: edgelab-testing
description: Testing conventions, fake object patterns, and ViewModel test templates for the EdgeLab :presentation module. Load this skill before writing or modifying tests.
---

# EdgeLab Testing Reference

## Infrastructure

| Aspect | Value |
|--------|-------|
| Framework | JUnit 4 + `kotlin.test` assertions |
| Coroutine testing | `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `advanceUntilIdle`) |
| Flow testing | Turbine 1.2.1 (available in `:presentation`) |
| Test doubles | Hand-written fakes. No MockK, no Mockito. |
| Source location | `src/test/kotlin/` (note: `kotlin/`, not `java/`) |

## Fake Object Rules

- Implement the full interface
- Use `MutableStateFlow` for observable state
- Constructor parameters for configurable behavior (`shouldFail`, `progressSteps`, `initialToken`)
- Tracking booleans (`initializeCalled`, `closeSessionCalled`) when tests need to verify invocation
- Mark as `internal class`
- Shared fakes → `TestFakes.kt`. Simple one-off fakes → in the test file.

## ViewModel Test Template

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class XxxViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): XxxViewModelImpl {
        return XxxViewModelImpl(
            // inject fakes here
            ioDispatcher = testDispatcher,  // CRITICAL
        )
    }

    @Test
    fun `SomeAction should update state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SomeAction("arg"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.someCondition)
        viewModel.dispose()  // ALWAYS dispose
    }
}
```

## Critical Rules

- `@OptIn(ExperimentalCoroutinesApi::class)` on class
- `Dispatchers.setMain(testDispatcher)` in `@BeforeTest`
- `Dispatchers.resetMain()` in `@AfterTest`
- Pass `testDispatcher` as `ioDispatcher` in `createViewModel()`
- `advanceUntilIdle()` after creating ViewModel AND after each action
- `viewModel.dispose()` at end of EVERY test
- Use `runTest { }` (not `runBlocking { }`)
- Use `kotlin.test` assertions (`assertEquals`, `assertTrue`)

## Minimum Coverage for New Features

- Test initial UiState after creation
- Test each UiAction path (each `when` branch in `onUiAction`)
- Test at least one error/failure state
- Test reactive state updates (flow emission → UiState change)
