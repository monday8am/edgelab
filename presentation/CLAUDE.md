# :presentation Module

Pure Kotlin, KMP-ready. Contains all ViewModels and state management. NO Android dependencies.

## Mandatory ViewModel Checklist

Every new feature needs THREE layers (see `docs/patterns.md` for full examples):

1. **Interface + UiState + UiAction** in this module:
   - `interface XxxViewModel { val uiState: StateFlow<UiState>; fun onUiAction(action: UiAction); fun dispose() }`
   - `data class UiState(...)` with `ImmutableList`/`ImmutableMap` for all collections
   - `sealed class UiAction` with `data class`/`data object` subclasses

2. **Implementation** in this module:
   - `class XxxViewModelImpl(..., ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`
   - `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`
   - `dispose()` calls `scope.cancel()`

3. **Android wrapper** in `:app:*` (NOT here):
   - `class AndroidXxxViewModel(impl) : ViewModel(), XxxViewModel by impl`

## Boundary Rules

- ZERO `android.*` or `androidx.*` imports
- No `ViewModel`, no `viewModelScope`, no `LiveData`
- Use `ImmutableList` (not `List`) in ALL UiState data classes
- Accept `CoroutineDispatcher` parameter in impl constructor
- Do NOT use `GlobalScope`

## Package Structure

Each feature gets its own package: `com.monday8am.edgelab.presentation.<feature>/`

## Testing

- Tests in `presentation/src/test/kotlin/`
- Shared fakes in `TestFakes.kt`
- MUST use `StandardTestDispatcher` + `Dispatchers.setMain`/`resetMain`
- MUST pass `testDispatcher` as `ioDispatcher`
- MUST call `viewModel.dispose()` at end of every test
- See `docs/testing.md` for complete template

## Mandatory Deliverables

When adding or modifying a ViewModel:
- Unit test file at `presentation/src/test/kotlin/.../XxxViewModelTest.kt`
- Tests cover: initial state, at least one action→state transition, and `dispose()`
- Use `StandardTestDispatcher`, `Dispatchers.setMain`/`resetMain`, and fake repositories from `docs/testing.md`

When adding or modifying a Composable screen in `:app:*`:
- `@Preview` function in the same file covering default state + at least one variant (loading, error, or non-default data)

These are not optional. Do not mark a task complete without them.

## Anti-patterns

- Importing `androidx.lifecycle.ViewModel` (wrong module — goes in `:app:*`)
- Using `viewModelScope` (impl owns its own scope)
- Using `List` instead of `ImmutableList` in UiState
- Forgetting `dispose()` method
- Not accepting `ioDispatcher` parameter (tests hang or flake)
- Hardcoding `Dispatchers.IO` instead of using injected dispatcher

See also the Anti-Patterns table in root `CLAUDE.md`.
