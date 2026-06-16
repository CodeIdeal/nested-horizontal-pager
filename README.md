# Nested HorizontalPager

[中文](README.zh-CN.md) | English

An Android Jetpack Compose sample and small library module for handling same-direction nested `HorizontalPager` gestures.

It fixes the common case where an inner `HorizontalPager` reaches its first or last page and the gesture should continue into the parent `HorizontalPager`, but the parent page switch feels delayed, hard to fling, or can get stuck between pages.

## Screen Recordings
### Before fix
The parent pager can lag at the child boundary and may stop between pages.  

https://github.com/user-attachments/assets/6943f839-aa6a-44d8-aaca-3f86e9c5512b

### After fix
Boundary drag and fling hand off to the parent pager more consistently.  

https://github.com/user-attachments/assets/2f0393de-bd42-4590-8d78-15283e9d7878


## Problem

A single `HorizontalPager` receives the full drag delta and fling velocity:

```text
user drag / release
-> Pager scrollable receives the full delta
-> PagerState.currentPageOffsetFraction changes continuously
-> flingBehavior receives the full velocity
-> Pager settles to the target page
```

With nested horizontal pagers, the parent usually sees only what the child leaves behind:

```text
user drags inside the child Pager
-> child Pager handles or competes for the gesture first
-> only boundary leftover delta reaches the parent
-> leftover velocity may be consumed by the default pageNestedScrollConnection
-> parent Pager receives an incomplete gesture
```

This can cause:

- parent paging that does not follow the finger at the child boundary;
- fling gestures that are much harder to trigger than a normal single `HorizontalPager`;
- the parent pager getting stuck between two pages when the user repeatedly drags around the boundary.

## Solution

The library treats each parent-child pager pair as an explicit hand-off boundary.

During drag:

- the child keeps the gesture while it can scroll;
- when the child reaches a boundary in the gesture direction, the connection moves the parent via `PagerState.dispatchRawDelta`;
- once the parent starts moving in the current gesture, later deltas stay with the parent until fling/settle finishes.

During fling:

- boundary-direction velocity is intercepted in `onPreFling` before the child pager can consume it;
- the parent pager runs `animateScrollToPage`;
- `onPostFling` still snaps the parent if it was left between pages.

For 3+ nested pagers, create one connection for every adjacent pair.

## Modules

```text
app/
  Demo app with two-level and three-level nested HorizontalPager examples.

nested-horizontal-pager/
  Library module containing the nested pager hand-off logic.
```

## API

```kotlin
@Composable
fun NestedHorizontalPager(
    state: PagerState,
    parentState: PagerState? = null,
    ...
)

@Composable
fun NestedHorizontalPagerContent(
    state: PagerState,
    enabled: Boolean = true,
    ...
)
```

Use `NestedHorizontalPager` instead of `HorizontalPager` for pagers that participate in same-direction nesting. Pass `parentState` only when the pager has a direct parent pager; root pagers can omit it.

Use `NestedHorizontalPagerContent` around leaf page content when that content contains its own horizontal scrollables, such as `LazyRow`. This isolates leftover content fling velocity so it does not bubble into the outer pager hand-off connection.

Advanced APIs are still available if you need manual wiring:

```kotlin
object NoOpNestedScrollConnection : NestedScrollConnection

@Composable
fun rememberNestedHorizontalPagerConnection(
    parentState: PagerState,
    childState: PagerState
): NestedScrollConnection
```

## Gradle

The library is published on Maven Central. Make sure your project uses `mavenCentral()`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("io.github.codeideal:nested-horizontal-pager:1.1.0")
}
```

## Two-Level Usage

```kotlin
val outerPagerState = rememberPagerState(pageCount = { outerTabs.size })

NestedHorizontalPager(
    state = outerPagerState
) { outerPage ->
    val innerPagerState = rememberPagerState(pageCount = { innerTabs.size })

    NestedHorizontalPager(
        state = innerPagerState,
        parentState = outerPagerState
    ) { innerPage ->
        NestedHorizontalPagerContent(state = innerPagerState) {
            // leaf page content, including LazyRow or other horizontal scrollers
        }
    }
}
```

## Three-Level Usage

```kotlin
NestedHorizontalPager(
    state = middlePagerState,
    parentState = outerPagerState
) {
    NestedHorizontalPager(
        state = innerPagerState,
        parentState = middlePagerState
    ) {
        NestedHorizontalPagerContent(state = innerPagerState) {
            // leaf page content
        }
    }
}
```

## Demo

Run the sample app:

```bash
./gradlew :app:assembleDebug
```

The app contains:

- a two-level nested pager demo;
- a three-level nested pager demo;
- boundary drag and fling hand-off examples.

## Limitations

The current implementation targets the common case:

- horizontal pagers;
- LTR layout;
- `reverseLayout = false`;
- same-direction nested pagers.

If your pager uses RTL or `reverseLayout = true`, the gesture direction mapping needs to be adapted.

## Verification

Both the app and library module build successfully:

```bash
./gradlew :app:assembleDebug
./gradlew :nested-horizontal-pager:assembleDebug
```
