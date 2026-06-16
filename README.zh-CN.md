# Nested HorizontalPager

中文 | [English](README.md)

这是一个 Android Jetpack Compose 示例工程和小型 library module，用来处理同方向嵌套 `HorizontalPager` 的手势交接。

它解决的问题是：内层 `HorizontalPager` 滑到第一页或最后一页后，继续向边界外拖动时，外层 `HorizontalPager` 应该接手切页，但实际表现可能是不跟手、fling 不容易触发，甚至频繁在边界左右滑动时卡在两个外层 Page 中间。

## 录屏示例

| 修复前 | 修复后                                                                             |
| --- |---------------------------------------------------------------------------------|
| https://github.com/user-attachments/assets/595b0ea5-24e1-4284-b2b9-2fd58e32191c | https://github.com/user-attachments/assets/16c59db0-1082-4e6c-bcc2-1b7a93897d8c |
| 内层边界外拖时，父层可能不跟手，并可能停在两个 Page 中间。 | 边界拖拽和 fling 能更稳定地交接给父层 Pager。                                                   |

## 问题

单一 `HorizontalPager` 的手势链路很干净：

```text
用户拖拽/松手
-> Pager scrollable 完整收到 delta
-> PagerState.currentPageOffsetFraction 连续变化
-> flingBehavior 完整收到 velocity
-> Pager settle 到目标页
```

嵌套横向 Pager 后，父层通常只能拿到内层处理后的剩余输入：

```text
用户在内层 Pager 区域拖拽
-> 内层 Pager 先处理或竞争手势
-> 内层到边界后才产生剩余 delta
-> 剩余 delta 通过 nested scroll 给父层
-> 剩余 velocity 还可能被默认 pageNestedScrollConnection 消费
-> 父 Pager 看到的不是完整原始手势
```

这会导致：

- 内层边界外拖时，父层切页不跟手；
- fling 触发外层切页比单一 `HorizontalPager` 困难；
- 用户在边界附近频繁反向拖动时，父层可能停在两个 Page 中间。

## 解决方案

这个库把每一对相邻的父子 Pager 当成一个明确的手势交接边界。

拖动阶段：

- 内层还能滚动时，不干预；
- 内层到达当前手势方向的边界后，通过 `PagerState.dispatchRawDelta` 推动父层；
- 一旦父层在本次手势中接手，后续 delta 继续优先给父层，直到 fling/settle 结束。

fling 阶段：

- 在 `onPreFling` 抢先拦截边界方向 velocity，避免被内层 Pager 消费；
- 父层直接执行 `animateScrollToPage`；
- `onPostFling` 保留兜底，负责把已经被拖到两页之间的父层吸附回整页。

如果有三层或更多层嵌套，需要为每一对相邻的父子 Pager 创建一份 connection。

## 模块结构

```text
app/
  示例 app，包含两层和三层 HorizontalPager 嵌套示例。

nested-horizontal-pager/
  library module，包含嵌套 Pager 手势交接逻辑。
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

`NoOpNestedScrollConnection` 用来关闭参与交接的 `HorizontalPager` 默认 `pageNestedScrollConnection`。

`rememberNestedHorizontalPagerConnection(parentState, childState)` 用于一对相邻父子 Pager。不要把同一个 connection 复用到多对 Pager，因为它内部保存了一次手势中的状态。

## Gradle

当前仓库把库作为本地 module 引用：

```kotlin
dependencies {
    implementation(project(":nested-horizontal-pager"))
}
```

## 两层嵌套用法

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

## 三层嵌套用法

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

每个 connection 挂到它的直接子 Pager 上：

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

## 示例

构建示例 app：

```bash
./gradlew :app:assembleDebug
```

示例 app 包含：

- 两层嵌套 Pager；
- 三层嵌套 Pager；
- 内层边界拖拽和 fling 交接场景。

## 限制

当前实现面向常见场景：

- 横向 Pager；
- LTR 布局；
- `reverseLayout = false`；
- 同方向嵌套 Pager。

如果你的 Pager 使用 RTL 或 `reverseLayout = true`，需要调整手势方向映射。

## 验证

app 和 library module 都可以正常构建：

```bash
./gradlew :app:assembleDebug
./gradlew :nested-horizontal-pager:assembleDebug
```
