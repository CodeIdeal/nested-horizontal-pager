package io.github.codeideal.nestedhorizontalpager

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/**
 * A no-op [NestedScrollConnection] for disabling [HorizontalPager][androidx.compose.foundation.pager.HorizontalPager]
 * default `pageNestedScrollConnection`.
 *
 * Use this on every same-direction nested pager that participates in the hand-off. The library
 * connection below becomes the single place that decides when a child boundary drag/fling should
 * move the parent pager.
 */
object NoOpNestedScrollConnection : NestedScrollConnection

/**
 * A [HorizontalPager] wrapper for same-direction nested horizontal pagers.
 *
 * The wrapper always disables the Pager's default `pageNestedScrollConnection`. When [parentState]
 * is provided, it also installs the hand-off connection that moves the direct parent pager once this
 * pager reaches a horizontal boundary.
 *
 * Use [NestedHorizontalPagerContent] inside a page when the page content contains its own
 * same-direction horizontal scrollables, such as `LazyRow`.
 */
@Composable
fun NestedHorizontalPager(
    state: PagerState,
    parentState: PagerState? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSize: PageSize = PageSize.Fill,
    beyondViewportPageCount: Int = PagerDefaults.BeyondViewportPageCount,
    pageSpacing: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.flingBehavior(state = state),
    userScrollEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    key: ((index: Int) -> Any)? = null,
    snapPosition: SnapPosition = SnapPosition.Start,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    pageContent: @Composable PagerScope.(page: Int) -> Unit
) {
    val pagerModifier = if (parentState == null) {
        modifier
    } else {
        modifier.nestedScroll(
            rememberNestedHorizontalPagerConnection(
                parentState = parentState,
                childState = state
            )
        )
    }

    HorizontalPager(
        state = state,
        modifier = pagerModifier,
        contentPadding = contentPadding,
        pageSize = pageSize,
        beyondViewportPageCount = beyondViewportPageCount,
        pageSpacing = pageSpacing,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        reverseLayout = reverseLayout,
        key = key,
        snapPosition = snapPosition,
        overscrollEffect = overscrollEffect,
        pageNestedScrollConnection = NoOpNestedScrollConnection,
        pageContent = pageContent
    )
}

/**
 * Isolates same-direction horizontal scrollables inside a pager page.
 *
 * Wrap leaf page content with this composable when the content contains `LazyRow`, horizontal
 * `scrollable`, or another non-pager horizontal scroller. It consumes leftover horizontal fling
 * velocity at the current pager boundary so that content flings do not bubble into an outer pager's
 * hand-off connection.
 *
 * Keep this wrapper around page content, not around another [NestedHorizontalPager], so pager-to-pager
 * hand-off remains explicit.
 */
@Composable
fun NestedHorizontalPagerContent(
    state: PagerState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val contentModifier = if (enabled) {
        modifier.nestedScroll(
            PagerDefaults.pageNestedScrollConnection(
                state = state,
                orientation = Orientation.Horizontal
            )
        )
    } else {
        modifier
    }

    Box(
        modifier = contentModifier,
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints,
        content = content
    )
}

/**
 * Creates a [NestedScrollConnection] that hands horizontal drags/flings from [childState] to
 * [parentState] when the child pager reaches either horizontal boundary.
 *
 * This connection is intentionally scoped to one parent-child pair. For three or more nested
 * pagers, create one connection for every adjacent pair.
 *
 * The current implementation targets the common LTR, `reverseLayout = false` horizontal pager
 * setup. If a pager uses RTL or `reverseLayout = true`, the direction mapping needs to be adapted.
 */
@Composable
fun rememberNestedHorizontalPagerConnection(
    parentState: PagerState,
    childState: PagerState
): NestedScrollConnection {
    val minimumFlingVelocity = LocalViewConfiguration.current.minimumFlingVelocity

    return remember(parentState, childState, minimumFlingVelocity) {
        NestedHorizontalPagerConnection(
            parentState = parentState,
            childState = childState,
            minimumFlingVelocity = minimumFlingVelocity
        )
    }
}

private class NestedHorizontalPagerConnection(
    private val parentState: PagerState,
    private val childState: PagerState,
    private val minimumFlingVelocity: Float
) : NestedScrollConnection {
    /**
     * Once the parent starts moving in this gesture, keep subsequent deltas with the parent until
     * fling/settle finishes. This prevents repeated boundary direction changes from leaving the
     * parent between two pages.
     */
    private var parentDragActive = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.x == 0f) {
            return Offset.Zero
        }

        if (parentDragActive) {
            return scrollParent(available.x)
        }

        // In a 3+ level hierarchy, Compose delivers pre-scroll to ancestors before closer
        // connections. Only pre-consume while the direct child pager owns the gesture; otherwise a
        // grandparent could steal a drag that the innermost pager can still handle.
        if (!childState.isScrollInProgress) {
            return Offset.Zero
        }

        // When the child cannot scroll further in the gesture direction, move the parent during
        // pre-scroll so the hand-off feels continuous.
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

        // Backstop for any leftover child delta after the child's own scroll pass.
        return if (parentDragActive || parentState.canScrollInGestureDirection(available.x)) {
            scrollParent(available.x)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Consume boundary-direction velocity before the child pager's fling can swallow it.
        if (available.x == 0f || !shouldParentHandlePreFling(available.x)) {
            return Velocity.Zero
        }

        settleParent(available.x)
        return available.copy(y = 0f)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        // If dragging moved the parent off its snap position but there was no usable pre-fling
        // velocity, still snap the parent to an integer page. This also lets leftover velocity
        // bubble from an inner pager through a middle pager to an outer pager.
        if (available.x == 0f || !(shouldParentSettle() || shouldParentHandleFling(available.x))) {
            return Velocity.Zero
        }

        settleParent(available.x)
        return available.copy(y = 0f)
    }

    private fun scrollParent(deltaX: Float): Offset {
        val parentIsBetweenPages = parentState.isBetweenPages()
        if (!parentIsBetweenPages && !parentState.canScrollInGestureDirection(deltaX)) {
            parentDragActive = false
            return Offset.Zero
        }

        // PagerState.dispatchRawDelta uses pager scroll coordinates. For LTR horizontal pagers,
        // those coordinates are the opposite of the user's drag delta, so invert both input and
        // return value.
        val consumed = -parentState.dispatchRawDelta(-deltaX)
        if (consumed.absoluteValue > 0f) {
            parentDragActive = true
        }
        return Offset(x = consumed, y = 0f)
    }

    private fun shouldParentHandleFling(velocityX: Float): Boolean {
        val childAtBoundary = !childState.canScrollInGestureDirection(velocityX)
        val parentCanMove =
            parentDragActive ||
                parentState.canScrollInGestureDirection(velocityX) ||
                parentState.isBetweenPages()

        return parentCanMove &&
            (parentDragActive || childAtBoundary) &&
            velocityX.absoluteValue >= minimumFlingVelocity
    }

    private fun shouldParentHandlePreFling(velocityX: Float): Boolean {
        return (parentDragActive || childState.isScrollInProgress) &&
            shouldParentHandleFling(velocityX)
    }

    private fun shouldParentSettle(): Boolean {
        return parentDragActive || parentState.isBetweenPages()
    }

    private suspend fun settleParent(velocityX: Float) {
        parentDragActive = false
        parentState.animateScrollToPage(
            parentState.settleTargetPage(velocityX, minimumFlingVelocity)
        )
    }
}

private fun PagerState.canScrollInGestureDirection(deltaX: Float): Boolean {
    return when {
        deltaX < 0f -> canScrollForward
        deltaX > 0f -> canScrollBackward
        else -> false
    }
}

private fun PagerState.settleTargetPage(velocityX: Float, minimumFlingVelocity: Float): Int {
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
