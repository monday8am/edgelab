# Code Patterns

Concrete patterns used throughout EdgeLab. Every section includes anti-patterns.

---

## ViewModel Pattern (Three Layers)

Every feature needs three layers. This is non-negotiable.

### Layer 1 — Interface + UiState + UiAction in `:presentation`

All live in one file per feature:

```kotlin
// presentation/src/main/kotlin/.../onboard/OnboardViewModel.kt

data class UiState(
    val models: ImmutableList<ModelInfo> = persistentListOf(),
    val isLoadingCatalog: Boolean = false,
    val isLoggedIn: Boolean = false,
    val statusMessage: String = "Sign in to download models",
)

sealed class UiAction {
    data class DownloadModel(val modelId: String) : UiAction()
    data object CancelCurrentDownload : UiAction()
}

interface OnboardViewModel {
    val uiState: StateFlow<UiState>
    fun onUiAction(action: UiAction)
    fun dispose()
}
```

Rules:
- `UiState` is a `data class` with default values for every field
- Collections in UiState MUST use `ImmutableList` / `ImmutableMap` from `kotlinx.collections.immutable` — regular `List` breaks Compose recomposition stability
- `UiAction` is a `sealed class` with `data class` or `data object` subclasses
- Interface exposes `uiState: StateFlow<UiState>`, `onUiAction()`, and `dispose()`
- UiState, UiAction, and supporting domain models all live in the same file as the interface

### Layer 2 — Implementation in `:presentation`

```kotlin
// Same file or same package

class OnboardViewModelImpl(
    private val modelDownloadManager: ModelDownloadManager,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnboardViewModel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val uiState: StateFlow<UiState> =
        combine(modelDownloadManager.modelsStatus, authRepository.authToken) {
                modelsStatus, authToken ->
                deriveUiState(modelsStatus, isLoggedIn = authToken != null)
            }
            .flowOn(ioDispatcher)
            .stateIn(scope, SharingStarted.Eagerly, UiState())

    override fun onUiAction(action: UiAction) {
        when (action) {
            is UiAction.DownloadModel -> startDownload(action.modelId)
            is UiAction.CancelCurrentDownload -> cancelDownload()
        }
    }

    override fun dispose() {
        scope.cancel()
    }
}
```

Rules:
- Own scope: `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
- Accept `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` for testability
- State: `combine().flowOn(ioDispatcher).stateIn(scope, SharingStarted.Eagerly, default)`
- `dispose()` calls `scope.cancel()`
- No `viewModelScope` — that belongs in Layer 3
- No `android.*` imports

### Layer 3 — Android wrapper in `:app:*`

```kotlin
// app/copilot/src/.../onboard/AndroidOnboardViewModel.kt

class AndroidOnboardViewModel(private val impl: OnboardViewModel) :
    ViewModel(), OnboardViewModel by impl {

    override val uiState: StateFlow<UiState> =
        impl.uiState.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState(),
        )

    override fun onCleared() {
        super.onCleared()
        impl.dispose()
    }
}
```

Rules:
- Extends `ViewModel()` and delegates via `by impl`
- Overrides `uiState` with `stateIn(viewModelScope, WhileSubscribed(5000L), default)`
- Calls `impl.dispose()` in `onCleared()`
- This is the ONLY place `viewModelScope` and `androidx.lifecycle.ViewModel` appear

**Anti-patterns:**
- Creating a monolithic ViewModel in `:app:*` with all business logic (logic goes in `:presentation`)
- Using `viewModelScope` in the `:presentation` impl (impl owns its own scope)
- Forgetting `dispose()` (coroutine scope leaks)
- Skipping the interface (needed for delegation and testability)
- Using `SharingStarted.Lazily` in impl (use `Eagerly` in impl, `WhileSubscribed` in wrapper)

---

## MVI State Flow

### Pattern A — Simple combine

Used when state is derived from external flows (OnboardViewModel, ModelSelectorViewModel):

```kotlin
override val uiState: StateFlow<UiState> =
    combine(flow1, flow2) { a, b -> deriveUiState(a, b) }
        .flowOn(ioDispatcher)
        .stateIn(scope, SharingStarted.Eagerly, UiState())
```

### Pattern B — Complex state machine with flatMapLatest

Used when ViewModel has internal state transitions (TestViewModel):

```kotlin
override val uiState: StateFlow<TestUiState> =
    combine(viewModelState, loadedTests) { state, tests -> state to tests }
        .flatMapLatest { (state, tests) ->
            when (state) {
                is ViewModelState.Idle -> flow { emit(createIdleUiState(tests)) }
                is ViewModelState.Running -> executeTests(tests)
                    .map { execState -> deriveUiState(execState, tests) }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, initialValue)
```

### Pattern C — Internal ViewModelState

For ViewModel-local state (not from a repository):

```kotlin
private sealed class ViewModelState {
    data class Idle(val lastUseGpu: Boolean = false) : ViewModelState()
    data class Running(val useGpu: Boolean, val filterDomain: TestDomain?) : ViewModelState()
}
private val viewModelState = MutableStateFlow<ViewModelState>(ViewModelState.Idle())
```

**Anti-patterns:**
- `MutableLiveData` (use `StateFlow`)
- Mutable properties in UiState (all `val`, immutable)
- Exposing `MutableStateFlow` publicly (expose `StateFlow` via interface)
- `SharedFlow` for state (state has a current value — use `StateFlow`)

---

## Coroutine Patterns

### Scope creation

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
```

Always `SupervisorJob()` (one child failure won't cancel siblings). Always `Main.immediate` as base.

### Background work

```kotlin
scope.launch(ioDispatcher) {
    modelDownloadManager.downloadModel(...)
}
```

Use the injected `ioDispatcher`, never hardcode `Dispatchers.IO`.

### Dispatcher injection

```kotlin
class XxxViewModelImpl(
    ...,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
)
```

Default for production, tests override with `StandardTestDispatcher()`.

### Error handling

Result type for engine operations:
```kotlin
override suspend fun initialize(...): Result<Unit>
```

Flow catch for stream errors:
```kotlin
.catch { e ->
    Logger.e("Failed to load tests", e)
    emit(emptyList())
}
```

**Anti-patterns:**
- `GlobalScope.launch { }` (use scoped coroutine)
- `runBlocking { }` in production code (use `suspend` or `launch`)
- Hardcoded `Dispatchers.IO` without constructor parameter (untestable)
- Catching and silently swallowing exceptions without logging

---

## DI Pattern

Two-level manual factory. No Hilt, no Koin, no Dagger, no `@Inject`.

### Level 1 — `CoreDependencies` in `:core`

Factory methods for shared infrastructure:

```kotlin
object CoreDependencies {
    fun createInferenceEngine(): LocalInferenceEngine = LiteRTLmInferenceEngineImpl()
    fun createDownloadManager(context: Context, authRepo: AuthRepository): ModelDownloadManager = ...
    fun createOAuthManager(context: Context, clientId: String, redirectScheme: String, ...): HuggingFaceOAuthManager = ...
    fun createAuthRepository(context: Context, scope: CoroutineScope): AuthRepository = ...
}
```

### Level 2 — App-level `Dependencies` object

```kotlin
// app/explorer/src/.../Dependencies.kt
object Dependencies {
    lateinit var appContext: Context
    val applicationScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val authRepository: AuthRepository by lazy { CoreDependencies.createAuthRepository(appContext, applicationScope) }
    val modelDownloadManager: ModelDownloadManager by lazy { CoreDependencies.createDownloadManager(appContext, authRepository) }
}
```

### ViewModel creation in composables

```kotlin
viewModel { AndroidXxxViewModel(XxxViewModelImpl(Dependencies.dep1, Dependencies.dep2)) }
```

**Anti-patterns:**
- `@Inject`, `@Module`, `@Component`, `@Provides`, `@HiltViewModel`
- Creating a DI framework or service locator library
- Adding Hilt/Koin/Dagger dependencies

---

## Compose Screen Pattern

Every screen has two composable functions:

### Connected Screen (handles ViewModel, navigation, side effects)

```kotlin
@Composable
fun OnboardScreen(
    onNavigateToSetup: () -> Unit,
    viewModel: AndroidOnboardViewModel = viewModel {
        AndroidOnboardViewModel(OnboardViewModelImpl(Dependencies.dep1, Dependencies.dep2))
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardScreenContent(uiState = uiState, onAction = viewModel::onUiAction)
}
```

### Stateless Content (pure UI, previewable)

```kotlin
@Composable
private fun OnboardScreenContent(
    uiState: UiState,
    onAction: (UiAction) -> Unit = {},
) {
    // All UI code here
}
```

### Previews with multiple states

```kotlin
@Preview(showBackground = true)
@Composable
private fun OnboardPreview() {
    EdgeLabTheme { OnboardScreenContent(uiState = UiState()) }
}

@Preview(showBackground = true, name = "Logged In")
@Composable
private fun OnboardPreviewLoggedIn() {
    EdgeLabTheme { OnboardScreenContent(uiState = UiState(isLoggedIn = true)) }
}
```

Rules:
- Use `collectAsStateWithLifecycle()` (not `collectAsState()`)
- Default parameter values on Content lambdas (`= {}`) for preview compatibility
- Content function is `private`

**Anti-patterns:**
- Business logic in composables (belongs in `:presentation` ViewModel)
- Skipping the Content split (makes previews impossible without a ViewModel)
- `collectAsState()` instead of `collectAsStateWithLifecycle()`
- No `@Preview` functions

---

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| ViewModel interface | `XxxViewModel` | `OnboardViewModel` |
| ViewModel impl | `XxxViewModelImpl` | `OnboardViewModelImpl` |
| Android wrapper | `AndroidXxxViewModel` | `AndroidOnboardViewModel` |
| Screen composable | `XxxScreen` | `OnboardScreen` |
| Content composable | `XxxScreenContent` (private) | `OnboardScreenContent` |
| UiState | `UiState` (file-scoped) | Not prefixed |
| UiAction | `UiAction` (file-scoped) | Not prefixed |

### Packages

- `:data` — `com.monday8am.edgelab.data.<feature>` (auth, model, testing, huggingface)
- `:agent` — `com.monday8am.edgelab.agent.<subpackage>` (core, tools, local)
- `:presentation` — `com.monday8am.edgelab.presentation.<feature>` (onboard, modelselector, testing)
- `:core` — `com.monday8am.edgelab.core.<subpackage>` (di, inference, download, oauth, storage)
- `:app:explorer` — `com.monday8am.edgelab.explorer.ui.screens.<feature>`
- `:app:copilot` — `com.monday8am.edgelab.copilot.ui.screens.<feature>`

### Test naming

Backtick-quoted descriptive names: `` `Initialize should show two models`() ``

Region comments for grouping: `// region Download Flow Tests` ... `// endregion`

---

## Caching (Stale-While-Revalidate)

Used by `ModelRepositoryImpl`, `TestRepositoryImpl`, and `HuggingFaceModelRepository`:

1. Emit cached data immediately from local data source
2. Fetch fresh data from network in background
3. Deduplicate: only emit if network data differs from cache
4. Persist network data to local data source
5. On network failure, fall back to bundled assets (`ModelCatalog.ALL_MODELS`, `AssetsTestRepository`)

---

## Logging

Use Kermit `Logger`:

```kotlin
Logger.i("LocalInferenceEngine") { "Starting inference (prompt: ${prompt.length} chars)" }
Logger.e("Failed to load tests", throwable)
Logger.w("No tests loaded to run")
```

**Anti-patterns:**
- `android.util.Log` in pure Kotlin modules (Kermit is cross-platform)
- `println()` for logging

---

## Import Ordering

ktfmt handles import ordering automatically. Run `./gradlew ktfmtFormat` and let the tool handle it. Do not manually sort imports.

---

## Module Boundary Rules

### `:data` — Pure Kotlin
- Zero `android.*` imports. No Context. No Android SDK types.
- Interfaces defined here; Android implementations live in `:core`.
- `StateFlow` for observable state, `kotlinx.serialization` for data classes.

### `:agent` — Pure Kotlin/JVM
- Zero `android.*` imports.
- Uses `litertlm-jvm` (NOT `litertlm-android`). The Android implementation lives in `:core`.
- `LocalInferenceEngine` interface defined here; implementation in `:core`.

### `:presentation` — Pure Kotlin
- Zero `android.*` or `androidx.*` imports.
- No `ViewModel`, no `viewModelScope`, no `LiveData`.
- All ViewModels follow the 3-layer pattern (see above).
- `ImmutableList`/`ImmutableMap` in all UiState data classes.

### `:core` — Android library
- Infrastructure only, no business logic.
- Implements interfaces from `:data` and `:presentation`.
- MUST exclude `litertlm-jvm` from `:agent` and `:presentation` transitive deps.
- Contains `CoreDependencies` factory object.

### `:app:explorer` / `:app:copilot` — Android apps
- Android ViewModel wrappers, Compose screens, navigation, DI wiring.
- Business logic does NOT go here — it belongs in `:presentation`.
- Each app has its own `Dependencies` object wiring `CoreDependencies` factories.
