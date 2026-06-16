package io.github.codeideal.nestedhorizontalpager.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.codeideal.nestedhorizontalpager.NestedHorizontalPager
import io.github.codeideal.nestedhorizontalpager.NestedHorizontalPagerContent
import io.github.codeideal.nestedhorizontalpager.demo.ui.theme.DemoTheme
import kotlinx.coroutines.launch

private val demoTabs = listOf("Two levels", "Three levels")
private val twoLevelOuterTabs = listOf("Outer A", "Outer B")
private val twoLevelInnerTabs = listOf("Inner 1", "Inner 2", "Inner 3", "Inner 4", "Inner 5")
private val threeLevelOuterTabs = listOf("Outer A", "Outer B")
private val threeLevelMiddleTabs = listOf("Middle 1", "Middle 2", "Middle 3")
private val threeLevelInnerTabs = listOf("Inner 1", "Inner 2", "Inner 3", "Inner 4")

private enum class AppPage {
    Menu,
    BeforeFix,
    Fixed
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DemoApp(modifier: Modifier = Modifier) {
    var currentPage by remember { mutableStateOf(AppPage.Menu) }

    when (currentPage) {
        AppPage.Menu -> DemoMenu(
            modifier = modifier,
            onBeforeFixClick = { currentPage = AppPage.BeforeFix },
            onFixedClick = { currentPage = AppPage.Fixed }
        )

        AppPage.BeforeFix -> DemoPageContainer(
            title = "Before fix",
            modifier = modifier,
            onBackClick = { currentPage = AppPage.Menu }
        ) {
            BeforeFixNestedPagerDemo()
        }

        AppPage.Fixed -> DemoPageContainer(
            title = "Fixed",
            modifier = modifier,
            onBackClick = { currentPage = AppPage.Menu }
        ) {
            NestedPagerDemo()
        }
    }
}

@Composable
private fun DemoMenu(
    onBeforeFixClick: () -> Unit,
    onFixedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nested HorizontalPager",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Button(onClick = onBeforeFixClick) {
            Text("Before fix")
        }
        Button(onClick = onFixedClick) {
            Text("Fixed demo")
        }
    }
}

@Composable
private fun DemoPageContainer(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Button(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = onBackClick
        ) {
            Text("Back")
        }
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun BeforeFixNestedPagerDemo(modifier: Modifier = Modifier) {
    val outerPagerState = rememberPagerState(pageCount = { twoLevelOuterTabs.size })

    Column(modifier = modifier.fillMaxSize()) {
        PagerTabs(
            titles = twoLevelOuterTabs,
            pagerState = outerPagerState,
            level = TabLevel.Primary
        )

        HorizontalPager(
            state = outerPagerState,
            modifier = Modifier.fillMaxSize()
        ) { outerPage ->
            val innerPagerState = rememberPagerState(pageCount = { twoLevelInnerTabs.size })

            Column(Modifier.fillMaxSize()) {
                PagerTabs(
                    titles = twoLevelInnerTabs,
                    pagerState = innerPagerState,
                    level = TabLevel.Secondary
                )

                HorizontalPager(
                    state = innerPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { innerPage ->
                    DemoContentPage(
                        title = "Before fix",
                        labels = listOf(
                            "Outer: ${twoLevelOuterTabs[outerPage]}",
                            "Inner: ${twoLevelInnerTabs[innerPage]}"
                        ),
                        color = demoColor(outerPage, innerPage),
                        innerPagerState = innerPagerState
                    )
                }
            }
        }
    }
}

@Composable
fun NestedPagerDemo(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val demoPagerState = rememberPagerState(pageCount = { demoTabs.size })

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = demoPagerState.currentPage) {
            demoTabs.forEachIndexed { index, title ->
                Tab(
                    selected = index == demoPagerState.currentPage,
                    onClick = {
                        scope.launch {
                            demoPagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        NestedHorizontalPager(
            state = demoPagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TwoLevelNestedPagerDemo(demoPagerState)
                1 -> ThreeLevelNestedPagerDemo(demoPagerState)
            }
        }
    }
}

@Composable
private fun TwoLevelNestedPagerDemo(parentPageState: PagerState, modifier: Modifier = Modifier) {
    val outerPagerState = rememberPagerState(pageCount = { twoLevelOuterTabs.size })

    Column(modifier = modifier.fillMaxSize()) {
        PagerTabs(
            titles = twoLevelOuterTabs,
            pagerState = outerPagerState,
            level = TabLevel.Primary
        )

        NestedHorizontalPager(
            state = outerPagerState,
            parentState = parentPageState,
            modifier = Modifier.fillMaxSize()
        ) { outerPage ->
            val innerPagerState = rememberPagerState(pageCount = { twoLevelInnerTabs.size })

            Column(Modifier.fillMaxSize()) {
                PagerTabs(
                    titles = twoLevelInnerTabs,
                    pagerState = innerPagerState,
                    level = TabLevel.Secondary
                )

                NestedHorizontalPager(
                    state = innerPagerState,
                    parentState = outerPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { innerPage ->
                    DemoContentPage(
                        title = "Two levels",
                        labels = listOf(
                            "Outer: ${twoLevelOuterTabs[outerPage]}",
                            "Inner: ${twoLevelInnerTabs[innerPage]}"
                        ),
                        color = demoColor(outerPage, innerPage),
                        innerPagerState = innerPagerState
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreeLevelNestedPagerDemo(parentPageState: PagerState, modifier: Modifier = Modifier) {
    val outerPagerState = rememberPagerState(pageCount = { threeLevelOuterTabs.size })

    Column(modifier = modifier.fillMaxSize()) {
        PagerTabs(
            titles = threeLevelOuterTabs,
            pagerState = outerPagerState,
            level = TabLevel.Primary
        )

        NestedHorizontalPager(
            state = outerPagerState,
            parentState = parentPageState,
            modifier = Modifier.fillMaxSize()
        ) { outerPage ->
            val middlePagerState = rememberPagerState(pageCount = { threeLevelMiddleTabs.size })

            Column(modifier = Modifier.fillMaxSize()) {
                PagerTabs(
                    titles = threeLevelMiddleTabs,
                    pagerState = middlePagerState,
                    level = TabLevel.Secondary
                )

                NestedHorizontalPager(
                    state = middlePagerState,
                    parentState = outerPagerState,
                    modifier = Modifier.fillMaxSize()
                ) { middlePage ->
                    val innerPagerState = rememberPagerState(pageCount = { threeLevelInnerTabs.size })

                    Column(Modifier.fillMaxSize()) {
                        PagerTabs(
                            titles = threeLevelInnerTabs,
                            pagerState = innerPagerState,
                            level = TabLevel.Secondary
                        )

                        NestedHorizontalPager(
                            state = innerPagerState,
                            parentState = middlePagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { innerPage ->
                            DemoContentPage(
                                title = "Three levels",
                                labels = listOf(
                                    "Outer: ${threeLevelOuterTabs[outerPage]}",
                                    "Middle: ${threeLevelMiddleTabs[middlePage]}",
                                    "Inner: ${threeLevelInnerTabs[innerPage]}"
                                ),
                                color = demoColor(outerPage, middlePage, innerPage),
                                innerPagerState = innerPagerState
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerTabs(
    titles: List<String>,
    pagerState: PagerState,
    level: TabLevel
) {
    val scope = rememberCoroutineScope()
    val tab: @Composable (Int, String) -> Unit = { index, title ->
        Tab(
            selected = index == pagerState.currentPage,
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            text = { Text(title) }
        )
    }

    when (level) {
        TabLevel.Primary -> {
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    tab(index, title)
                }
            }
        }

        TabLevel.Secondary -> {
            SecondaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                titles.forEachIndexed { index, title ->
                    tab(index, title)
                }
            }
        }
    }
}

@Composable
private fun DemoContentPage(
    title: String,
    labels: List<String>,
    color: Color,
    innerPagerState: PagerState
) {
    NestedHorizontalPagerContent(
        state = innerPagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyRow {
                repeat(12) {
                    item {
                        Image(
                            painter = painterResource(android.R.drawable.ic_dialog_dialer),
                            contentDescription = "",
                            modifier = Modifier
                                .background(Color.Gray)
                                .padding(8.dp)
                                .size(60.dp)
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            labels.forEach { label ->
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun demoColor(vararg indexes: Int): Color {
    val seed = indexes.fold(17) { acc, index -> acc * 31 + index * 13 }
    val red = 80 + (seed * 37).mod(120)
    val green = 80 + (seed * 53).mod(120)
    val blue = 80 + (seed * 71).mod(120)
    return Color(red = red, green = green, blue = blue)
}

private enum class TabLevel {
    Primary,
    Secondary
}

@Preview(showBackground = true)
@Composable
fun NestedPagerDemoPreview() {
    DemoTheme {
        DemoApp()
    }
}
