# Nested HorizontalPager

[中文](README.zh-CN.md) | English

An Android Jetpack Compose sample and small library module for handling same-direction nested `HorizontalPager` gestures.

It fixes the common case where an inner `HorizontalPager` reaches its first or last page and the gesture should continue into the parent `HorizontalPager`, but the parent page switch feels delayed, hard to fling, or can get stuck between pages.

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
object NoOpNestedScrollConnection : NestedScrollConnection

@Composable
fun rememberNestedHorizontalPagerConnection(
    parentState: PagerState,
    childState: PagerState
): NestedScrollConnection
```

Use `NoOpNestedScrollConnection` to disable the default `HorizontalPager` `pageNestedScrollConnection` on pagers that participate in the hand-off.

Use `rememberNestedHorizontalPagerConnection(parentState, childState)` for one adjacent parent-child pager pair. Do not reuse the same connection across multiple pairs because it stores per-gesture state.

## Gradle

This repository includes the library as a local module:

```kotlin
dependencies {
    implementation(project(":nested-horizontal-pager"))
}
```

## Two-Level Usage

```kotlin
val outerPagerState = rememberPagerState(pageCount = { outerTabs.size })

HorizontalPager(
    state = outerPagerState,
    pageNestedScrollConnection = NoOpNestedScrollConnection
) { outerPage ->
    val innerPagerState = rememberPagerState(pageCount = { innerTabs.size })
    val connection = rememberNestedHorizontalPagerConnection(
        parentState = outerPagerState,
        childState = innerPagerState
    )

    HorizontalPager(
        state = innerPagerState,
        modifier = Modifier.nestedScroll(connection),
        pageNestedScrollConnection = NoOpNestedScrollConnection
    ) { innerPage ->
        // page content
    }
}
```

## Three-Level Usage

```kotlin
val outerMiddleConnection = rememberNestedHorizontalPagerConnection(
    parentState = outerPagerState,
    childState = middlePagerState
)

val middleInnerConnection = rememberNestedHorizontalPagerConnection(
    parentState = middlePagerState,
    childState = innerPagerState
)
```

Attach each connection to its direct child pager:

```kotlin
HorizontalPager(
    state = middlePagerState,
    modifier = Modifier.nestedScroll(outerMiddleConnection),
    pageNestedScrollConnection = NoOpNestedScrollConnection
) {
    HorizontalPager(
        state = innerPagerState,
        modifier = Modifier.nestedScroll(middleInnerConnection),
        pageNestedScrollConnection = NoOpNestedScrollConnection
    ) {
        // page content
    }
}
```

## Demo

Screen recordings:

- [Before fix](before_fix.mp4): the parent pager can lag at the child boundary and may stop between pages.
- [After fix](after_fix.mp4): boundary drag and fling hand off to the parent pager more consistently.

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
