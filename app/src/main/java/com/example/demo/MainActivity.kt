package com.example.demo

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.demo.ui.theme.DemoTheme
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration

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
    val mainTab = listOf("Tab1","Tab2")
    val mainPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { mainTab.size }
    )
    Column(modifier = modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = mainPagerState.currentPage,
        )  {
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
            modifier = Modifier.fillMaxSize()
        ) {
            val secondaryTab = listOf("sTab1","sTab2","sTab3","sTab4","sTab5","sTab6","sTab7","sTab8",)
            val secondaryPagerState = rememberPagerState(
                initialPage = 0,
                pageCount = { secondaryTab.size }
            )
            Column {
                SecondaryScrollableTabRow(
                    selectedTabIndex = secondaryPagerState.currentPage,
                )  {
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(
                                Color(
                                    red = Random.nextInt(256),
                                    green = Random.nextInt(256),
                                    blue = Random.nextInt(256),
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${mainTab[mainPagerState.currentPage]}_${secondaryTab[secondaryPagerState.currentPage]}")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoTheme {
        Greeting()
    }
}