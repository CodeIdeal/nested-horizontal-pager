# Nested HorizontalPager

中文 | [English](README.md)

这是一个 Android Jetpack Compose 示例工程和小型 library module，用来处理同方向嵌套 `HorizontalPager` 的手势交接。

它解决的问题是：内层 `HorizontalPager` 滑到第一页或最后一页后，继续向边界外拖动时，外层 `HorizontalPager` 应该接手切页，但实际表现可能是不跟手、fling 不容易触发，甚至频繁在边界左右滑动时卡在两个外层 Page 中间。

## 录屏示例
### 修复前
内层边界外拖时，父层可能不跟手，并可能停在两个 Page 中间。  

https://github.com/user-attachments/assets/6943f839-aa6a-44d8-aaca-3f86e9c5512b

### 修复后
边界拖拽和 fling 能更稳定地交接给父层 Pager。  

https://github.com/user-attachments/assets/2f0393de-bd42-4590-8d78-15283e9d7878

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

参与同方向嵌套的 Pager 使用 `NestedHorizontalPager` 替代 `HorizontalPager`。只有当前 Pager 有直接父 Pager 时才需要传 `parentState`；根层 Pager 可以不传。

当页面内容内部还有横向滚动组件，例如 `LazyRow`，用 `NestedHorizontalPagerContent` 包住叶子页面内容。它会隔离内容内部剩余的横向 fling velocity，避免继续冒泡到外层 Pager 的手势交接 connection。

如果需要手动接线，也可以继续使用底层 API：

```kotlin
object NoOpNestedScrollConnection : NestedScrollConnection

@Composable
fun rememberNestedHorizontalPagerConnection(
    parentState: PagerState,
    childState: PagerState
): NestedScrollConnection
```

## Gradle

库已经发布到 Maven Central。先确保你的项目启用了 `mavenCentral()`：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

然后添加依赖：

```kotlin
dependencies {
    implementation("io.github.codeideal:nested-horizontal-pager:1.1.0")
}
```

## 两层嵌套用法

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
            // 叶子页面内容，可以包含 LazyRow 或其他横向滚动组件
        }
    }
}
```

## 三层嵌套用法

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
            // 叶子页面内容
        }
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
