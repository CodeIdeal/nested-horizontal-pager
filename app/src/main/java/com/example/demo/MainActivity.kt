package com.example.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import com.example.demo.ui.theme.DemoTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val mainTab = listOf("Tab1", "Tab2")
    val mainPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mainTab.size }
    )
    Column(modifier = modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = mainPagerState.currentPage,
        ) {
            mainTab.forEachIndexed { index, string ->
                Tab(
                    text = {
                        Text(string)
                    },
                    selected = index == mainPagerState.currentPage,
                    onClick = {
                        scope.launch {
                            mainPagerState.animateScrollToPage(index)
                        }
                    }
                )

            }
        }
        HorizontalPager(
            state = mainPagerState,
            modifier = Modifier.fillMaxSize(),
            // Pager 的默认 pageNestedScrollConnection 会主动消费同方向的嵌套滚动。
            // 这里禁用它，避免和下面的内外层交接逻辑同时处理同一段手势。
            pageNestedScrollConnection = EmptyNestedScrollConnection
        ) { mainPage ->
            val secondaryTab = listOf(
                "sTab1",
                "sTab2",
                "sTab3",
                "sTab4",
                "sTab5",
                "sTab6",
                "sTab7",
                "sTab8"
            )
            val secondaryPagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { secondaryTab.size }
            )
            val nestedPagerConnection = rememberNestedHorizontalPagerConnection(
                parentState = mainPagerState,
                childState = secondaryPagerState
            )
            Column {
                SecondaryScrollableTabRow(
                    selectedTabIndex = secondaryPagerState.currentPage,
                ) {
                    secondaryTab.forEachIndexed { index, string ->
                        Tab(
                            text = {
                                Text(string)
                            },
                            selected = index == secondaryPagerState.currentPage,
                            onClick = {
                                scope.launch {
                                    secondaryPagerState.animateScrollToPage(index)
                                }
                            }
                        )

                    }
                }
                HorizontalPager(
                    state = secondaryPagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedPagerConnection),
                    // 内层也禁用默认连接，否则它可能在 fling 阶段消费掉横向 velocity，
                    // 外层拿不到接近原始的松手速度，切页会比单一 Pager 更不容易触发。
                    pageNestedScrollConnection = EmptyNestedScrollConnection
                ) { secondaryPage ->
                    val color = remember(mainPage, secondaryPage) {
                        Color(
                            red = Random.nextInt(256),
                            green = Random.nextInt(256),
                            blue = Random.nextInt(256),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${mainTab[mainPage]}_${secondaryTab[secondaryPage]}")
                    }
                }
            }
        }
    }
}

// 空连接只用于关闭 Pager 默认的 pageNestedScrollConnection。
private object EmptyNestedScrollConnection : NestedScrollConnection

@Composable
private fun rememberNestedHorizontalPagerConnection(
    parentState: PagerState,
    childState: PagerState
): NestedScrollConnection {
    val minimumFlingVelocity = LocalViewConfiguration.current.minimumFlingVelocity

    return remember(parentState, childState, minimumFlingVelocity) {
        object : NestedScrollConnection {
            // 一旦父 Pager 在本次手势中接手，后续反向拖动也先交给父 Pager。
            // 这样用户在边界附近来回搓时，父 Pager 能先回到整页位置，不会悬在半页。
            private var parentDragActive = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }

                // 父层已经接手后，继续优先移动父层，直到手势结束。
                if (parentDragActive) {
                    return scrollParent(available.x)
                }

                // 内层不能再沿当前手势方向滚动时，在 pre-scroll 阶段把 delta 交给父层。
                // 这比等内层处理完再分发剩余量更跟手。
                return if (!childState.canScrollInGestureDirection(available.x)) {
                    scrollParent(available.x)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }

                // 兜底处理内层滚完后剩下的 delta，覆盖边界判断有一帧滞后的情况。
                return if (parentDragActive || parentState.canScrollInGestureDirection(available.x)) {
                    scrollParent(available.x)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // 在 pre-fling 抢先把边界方向的速度交给父层，避免内层 fling 先消费速度。
                // 这是让外层切页接近单一 HorizontalPager 手感的关键。
                if (available.x == 0f || !shouldParentHandleFling(available.x)) {
                    return Velocity.Zero
                }

                settleParent(available.x)
                return available.copy(y = 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // 如果拖动阶段已经把父层推到半页，但 pre-fling 没有足够速度，
                // 这里仍然负责收尾吸附，避免停在两个外层 Page 中间。
                if (available.x == 0f || !shouldParentSettle()) {
                    return Velocity.Zero
                }

                settleParent(available.x)
                return available.copy(y = 0f)
            }

            private fun scrollParent(deltaX: Float): Offset {
                // 父层已经在两页之间时，即使当前方向在页边界不可继续滚，
                // 也要允许它反向回到吸附位。
                val parentIsBetweenPages =
                    parentState.currentPageOffsetFraction.absoluteValue >= 0.001f
                if (!parentIsBetweenPages && !parentState.canScrollInGestureDirection(deltaX)) {
                    parentDragActive = false
                    return Offset.Zero
                }

                // dispatchRawDelta 接收的是 Pager 内部滚动方向，和手指拖动方向相反；
                // 返回值也要反转回 nested-scroll 的 Offset 方向。
                val consumed = -parentState.dispatchRawDelta(-deltaX)
                if (consumed.absoluteValue > 0f) {
                    parentDragActive = true
                }
                return Offset(x = consumed, y = 0f)
            }

            private fun shouldParentHandleFling(velocityX: Float): Boolean {
                // 只有内层处在 fling 方向边界，或父层已接手本次手势时，才让父层消费 fling。
                // 内层仍能滚动时保持内层自己的 fling 行为。
                val childAtBoundary = !childState.canScrollInGestureDirection(velocityX)
                val parentCanMove =
                    parentDragActive ||
                        parentState.canScrollInGestureDirection(velocityX) ||
                        parentState.isBetweenPages()

                return parentCanMove &&
                    (parentDragActive || childAtBoundary) &&
                    velocityX.absoluteValue >= minimumFlingVelocity
            }

            private fun shouldParentSettle(): Boolean {
                // 父层接手过或已经被拖出整页位置，就需要一次吸附收尾。
                return parentDragActive || parentState.isBetweenPages()
            }

            private suspend fun settleParent(velocityX: Float) {
                parentDragActive = false
                parentState.animateScrollToPage(
                    parentState.settleTargetPage(velocityX, minimumFlingVelocity)
                )
            }
        }
    }
}

private fun PagerState.canScrollInGestureDirection(deltaX: Float): Boolean {
    // 对默认 LTR 水平 Pager：手指向左拖或 fling 为负值，内容向前滚动。
    return when {
        deltaX < 0f -> canScrollForward
        deltaX > 0f -> canScrollBackward
        else -> false
    }
}

private fun PagerState.settleTargetPage(velocityX: Float, minimumFlingVelocity: Float): Int {
    // 有足够 fling 速度时按速度方向翻页；否则回到当前 closest page。
    // 这里没有降低 Pager 阈值，而是让父层在边界时直接拿到 fling 决策权。
    val targetPage = when {
        velocityX < -minimumFlingVelocity && canScrollForward -> currentPage + 1
        velocityX > minimumFlingVelocity && canScrollBackward -> currentPage - 1
        else -> currentPage
    }

    return targetPage.coerceIn(0, pageCount - 1)
}

private fun PagerState.isBetweenPages(): Boolean {
    return currentPageOffsetFraction.absoluteValue >= 0.001f
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoTheme {
        Greeting()
    }
}
