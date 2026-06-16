package com.example.nestedhorizontalpager

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
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
