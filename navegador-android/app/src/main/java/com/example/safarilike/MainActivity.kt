package com.example.safarilike

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// Cores inspiradas no iOS
private val IosBlue = Color(0xFF007AFF)
private val IosBg = Color(0xFFF2F2F7)
private val IosPill = Color(0xFFE5E5EA)
private val IosGray = Color(0xFF8E8E93)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BrowserApp() }
    }
}

class BrowserTab(val webView: WebView) {
    var title by mutableStateOf("Nova aba")
    var url by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
}

@SuppressLint("SetJavaScriptEnabled")
fun newTab(ctx: Context, startUrl: String?): BrowserTab {
    val wv = WebView(ctx)
    val tab = BrowserTab(wv)
    wv.settings.javaScriptEnabled = true
    wv.settings.domStorageEnabled = true
    wv.webViewClient = object : WebViewClient() {
        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            tab.url = url
            tab.canBack = view.canGoBack()
            tab.canForward = view.canGoForward()
        }
        override fun onPageFinished(view: WebView, url: String) {
            tab.title = view.title ?: url
        }
    }
    wv.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }
    }
    startUrl?.let { wv.loadUrl(it); tab.url = it }
    return tab
}

fun toUrl(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> "https://www.google.com/search?q=" + Uri.encode(t)
    }
}

@Composable
fun BrowserApp() {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val tabs = remember { mutableStateListOf(newTab(ctx, null)) }
    var current by remember { mutableIntStateOf(0) }
    var showTabs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val tab = tabs[current]

    fun go(text: String) {
        val u = toUrl(text)
        tab.url = u
        tab.webView.loadUrl(u)
        editing = false
        focus.clearFocus()
    }

    BackHandler(enabled = showTabs || tab.canBack) {
        if (showTabs) showTabs = false else tab.webView.goBack()
    }

    MaterialTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(IosBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    showTabs -> TabGrid(
                        tabs = tabs,
                        current = current,
                        onPick = { current = it; showTabs = false },
                        onClose = { i ->
                            tabs[i].webView.destroy()
                            tabs.removeAt(i)
                            if (tabs.isEmpty()) tabs.add(newTab(ctx, null))
                            if (i < current) current--
                            current = current.coerceIn(0, tabs.lastIndex)
                        },
                        onNew = { tabs.add(newTab(ctx, null)); current = tabs.lastIndex; showTabs = false },
                        onDone = { showTabs = false }
                    )
                    tab.url.isEmpty() -> StartPage { go(it) }
                    else -> key(tab) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = {
                                (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
                                tab.webView
                            }
                        )
                    }
                }
            }

            if (!showTabs) {
                if (tab.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { tab.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = IosBlue,
                        trackColor = Color.Transparent
                    )
                }
                // Barra de endereço em formato de pílula, embaixo (como no Safari do iPhone)
                Row(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IosPill)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val display = if (tab.url.isEmpty()) "Pesquisar ou inserir site"
                    else Uri.parse(tab.url).host?.removePrefix("www.") ?: tab.url
                    BasicTextField(
                        value = if (editing) input else display,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = if (!editing && tab.url.isEmpty()) IosGray else Color.Black,
                            textAlign = if (editing) TextAlign.Start else TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                        keyboardActions = KeyboardActions(onGo = { go(input) }),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged {
                                editing = it.isFocused
                                if (it.isFocused) input = tab.url
                            }
                    )
                    if (!editing && tab.url.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Refresh, "Recarregar", tint = Color.Black,
                            modifier = Modifier.size(20.dp).clickable { tab.webView.reload() }
                        )
                    }
                }
                // Barra de ferramentas
                Row(
                    Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tab.webView.goBack() }, enabled = tab.canBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = if (tab.canBack) IosBlue else IosGray)
                    }
                    IconButton(onClick = { tab.webView.goForward() }, enabled = tab.canForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Avançar", tint = if (tab.canForward) IosBlue else IosGray)
                    }
                    IconButton(onClick = { tabs.add(newTab(ctx, null)); current = tabs.lastIndex }) {
                        Icon(Icons.Filled.Add, "Nova aba", tint = IosBlue)
                    }
                    Box(
                        Modifier
                            .size(24.dp)
                            .border(BorderStroke(2.dp, IosBlue), RoundedCornerShape(6.dp))
                            .clickable { showTabs = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${tabs.size}", color = IosBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun StartPage(onOpen: (String) -> Unit) {
    val favs = listOf(
        "Google" to "google.com",
        "Wikipédia" to "pt.wikipedia.org",
        "YouTube" to "youtube.com",
        "GitHub" to "github.com"
    )
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Favoritos", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            favs.forEach { (name, site) ->
                Column(
                    Modifier.clickable { onOpen(site) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.first().toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = IosBlue)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun TabGrid(
    tabs: List<BrowserTab>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
    onDone: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(tabs) { i, t ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(
                            BorderStroke(if (i == current) 2.dp else 0.dp, if (i == current) IosBlue else Color.Transparent),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onPick(i) }
                        .height(150.dp)
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.title, Modifier.weight(1f), fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Filled.Close, "Fechar aba", tint = IosGray,
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onClose(i) }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t.url.ifEmpty { "Favoritos" }, fontSize = 11.sp, color = IosGray,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onNew) { Text("+ Nova aba", color = IosBlue, fontSize = 17.sp) }
            TextButton(onClick = onDone) { Text("Concluído", color = IosBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
