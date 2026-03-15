# Testing

How to write and run tests in EdgeLab. Enables agent self-verification.

---

## Infrastructure

| Aspect | Value |
|--------|-------|
| Framework | JUnit 4 + `kotlin.test` assertions |
| Coroutine testing | `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`, `advanceUntilIdle`) |
| Flow testing | Turbine 1.2.1 (available in `:presentation`) |
| Test doubles | Hand-written fakes (implement interfaces). No mocking frameworks. |
| Source location | `src/test/kotlin/` (note: `kotlin/`, not `java/`) |

### Commands

| Scope | Command |
|-------|---------|
| All | `./gradlew test` |
| Presentation | `./gradlew :presentation:test` |
| Agent | `./gradlew :agent:test` |
| Data | `./gradlew :data:test` |
| Core (Android) | `./gradlew :core:testDebugUnitTest` |

---

## Fake Objects

Fakes implement the interface directly with controllable behavior. No MockK, no Mockito.

### Shared fakes in `presentation/src/test/kotlin/.../TestFakes.kt`

```kotlin
internal class FakeLocalInferenceEngine : LocalInferenceEngine {
    var initializeCalled = false

    override suspend fun initialize(modelConfig: ModelConfiguration, modelPath: String): Result<Unit> {
        initializeCalled = true
        return Result.success(Unit)
    }

    override suspend fun prompt(prompt: String): Result<String> = Result.success("Test response")
    override fun promptStreaming(prompt: String) = flowOf("Hi!")

    var closeSessionCalled = false
    override fun closeSession(): Result<Unit> {
        closeSessionCalled = true
        return Result.success(Unit)
    }
}

internal class FakeModelDownloadManager(
    private val progressSteps: List<Float> = emptyList(),
    private val shouldFail: Boolean = false,
    private val modelsStatusFlow: MutableStateFlow<Map<String, ModelDownloadManager.Status>> =
        MutableStateFlow(emptyMap()),
) : ModelDownloadManager {
    override val modelsStatus: Flow<Map<String, ModelDownloadManager.Status>>
        get() = modelsStatusFlow
    // ... controllable download behavior
}
```

### Inline fakes for simple interfaces (in test files)

```kotlin
internal class FakeAuthRepository(initialToken: String? = null) : AuthRepository {
    private val _authToken = MutableStateFlow(initialToken)
    override val authToken: StateFlow<String?> = _authToken.asStateFlow()

    override suspend fun saveToken(token: String) { _authToken.value = token }
    override suspend fun clearToken() { _authToken.value = null }
}
```

### Rules for fakes

- Always implement the full interface
- Use `MutableStateFlow` for observable state
- Constructor parameters for configurable behavior (`shouldFail`, `progressSteps`, `initialToken`)
- Tracking booleans (`initializeCalled`, `closeSessionCalled`) when tests need to verify invocation
- Mark as `internal class`
- Shared fakes → `TestFakes.kt`. Simple one-off fakes → in the test file.

---

## ViewModel Test Template

Complete template based on `OnboardViewModelTest`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class XxxViewModelTest {
    // 1. Test dispatcher
    private val testDispatcher = StandardTestDispatcher()

    // 2. Fakes
    private lateinit var fakeDep1: FakeDependency1
    private lateinit var fakeDep2: FakeDependency2

    // 3. Setup: set main dispatcher, create fakes
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDep1 = FakeDependency1()
        fakeDep2 = FakeDependency2()
    }

    // 4. Teardown: reset main dispatcher
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 5. Factory with defaults
    private fun createViewModel(
        dep1: Dep1Interface = fakeDep1,
        dep2: Dep2Interface = fakeDep2,
    ): XxxViewModelImpl {
        return XxxViewModelImpl(
            dependency1 = dep1,
            dependency2 = dep2,
            ioDispatcher = testDispatcher,  // CRITICAL: inject test dispatcher
        )
    }

    // 6. Tests grouped by region

    // region Initialization Tests

    @Test
    fun `Initialize should show correct initial state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(expectedValue, state.someProperty)

        viewModel.dispose()  // ALWAYS dispose
    }

    // endregion

    // region Action Tests

    @Test
    fun `SomeAction should update state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onUiAction(UiAction.SomeAction("arg"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.someCondition)

        viewModel.dispose()
    }

    // endregion
}
```

### Critical rules

- `@OptIn(ExperimentalCoroutinesApi::class)` on class
- `Dispatchers.setMain(testDispatcher)` in `@BeforeTest`
- `Dispatchers.resetMain()` in `@AfterTest`
- Pass `testDispatcher` as `ioDispatcher` in `createViewModel()`
- `advanceUntilIdle()` after creating ViewModel AND after each action
- `viewModel.dispose()` at end of EVERY test
- Use `runTest { }` (not `runBlocking { }`)
- Use `kotlin.test` assertions (`assertEquals`, `assertTrue`), not JUnit's

---

## Data Layer Test Template

Simpler — no dispatcher setup needed:

```kotlin
class ModelRepositoryTest {
    private val model1 = ModelCatalog.QWEN3_0_6B
    private val model2 = ModelCatalog.GEMMA3_1B

    private val fakeProvider = object : ModelCatalogProvider {
        override fun getModels(): Flow<List<ModelConfiguration>> = flowOf(listOf(model1, model2))
    }
    private val repository = ModelRepositoryImpl(fakeProvider)

    @Test
    fun `findById should return model when exists`() {
        repository.setModels(listOf(model1, model2))
        val result = repository.findById(model1.modelId)
        assertNotNull(result)
    }
}
```

---

## Agent Self-Verification Workflow

After making code changes, run this sequence:

```bash
./gradlew ktfmtFormat                                              # 1. Fix formatting
./gradlew test                                                      # 2. Run all tests
./gradlew :app:explorer:assembleDebug :app:copilot:assembleDebug    # 3. Both apps compile
```

If tests fail: read the failure output, fix the issue, re-run from step 1.

### Minimum test coverage for new features

- Test initial UiState after creation
- Test each UiAction path (each `when` branch in `onUiAction`)
- Test at least one error/failure state
- Test reactive state updates (flow emission → UiState change)

---

## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| `every { mock.method() } returns value` (MockK) | Create `FakeXxx` implementing the interface |
| `@Mock lateinit var mock: Interface` (Mockito) | `private lateinit var fake: FakeInterface` |
| Test `AndroidXxxViewModel` | Test `XxxViewModelImpl` in `:presentation` |
| `runBlocking { }` | `runTest { }` |
| Forget `viewModel.dispose()` | Always call it at end of every test |
| `Thread.sleep()` | `advanceUntilIdle()` or `advanceTimeBy()` |
| `@get:Rule val rule = InstantTaskExecutorRule()` | `Dispatchers.setMain(testDispatcher)` |
