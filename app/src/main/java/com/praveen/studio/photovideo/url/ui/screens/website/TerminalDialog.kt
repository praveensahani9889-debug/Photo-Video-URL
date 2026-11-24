package com.praveen.studio.photovideo.url.ui.screens.website

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.praveen.studio.photovideo.url.domain.util.SizeFormat
import com.praveen.studio.photovideo.url.domain.util.WebsiteBuilder
import java.io.ByteArrayInputStream

// ---- look of the terminal ----
private val TermBg = Color(0xFF0D1117)
private val TermPanel = Color(0xFF161B22)
private val TermLine = Color(0xFF30363D)
private val TermText = Color(0xFFE6EDF3)
private val TermMuted = Color(0xFF8B949E)
private val TermGreen = Color(0xFF3FB950)
private val TermBlue = Color(0xFF58A6FF)
private val TermOrange = Color(0xFFFFA657)
private val TermRed = Color(0xFFF85149)
private val TermPurple = Color(0xFFD2A8FF)

private const val PREVIEW_HOST = "preview.ivu"
private const val MAX_TERMINAL_CHARS = 300_000
private const val STARTER_FILE = "index.html"

private val STARTER_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>My website</title>
  <style>
    body { margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
           font-family: system-ui, sans-serif; background: #0f172a; color: #fff; text-align: center; }
    h1 { font-size: 2rem; margin: 0 0 .5rem; }
    p { opacity: .8; }
  </style>
</head>
<body>
  <main>
    <h1>Hello, world!</h1>
    <p>Edit this page, press Run to check it, then Upload.</p>
  </main>
</body>
</html>
"""

private data class ConsoleLine(val text: String, val kind: Int)

private const val K_TEXT = 0
private const val K_OK = 1
private const val K_ERR = 2
private const val K_CMD = 3
private const val K_JS = 4

private val FilesSaver = listSaver<Map<String, String>, String>(
    save = { map -> map.flatMap { listOf(it.key, it.value) } },
    restore = { list -> list.chunked(2).associate { it[0] to it[1] } }
)

/** Syntax colours for html / css / js (the text itself is never changed, so the cursor stays where it is). */
private class CodeHighlighter(private val extension: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length > 120_000) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(s)
        fun paint(regex: Regex, color: Color) {
            regex.findAll(s).forEach { m -> b.addStyle(SpanStyle(color = color), m.range.first, m.range.last + 1) }
        }
        when (extension) {
            "html", "htm" -> {
                paint(HTML_TAG, TermBlue)
                paint(STRING, TermOrange)
                paint(HTML_COMMENT, TermMuted)
            }
            "css" -> {
                paint(CSS_PROP, TermBlue)
                paint(NUMBER, TermOrange)
                paint(STRING, TermGreen)
                paint(BLOCK_COMMENT, TermMuted)
            }
            "js", "mjs", "json" -> {
                paint(JS_KEYWORD, TermPurple)
                paint(NUMBER, TermOrange)
                paint(STRING, TermGreen)
                paint(LINE_COMMENT, TermMuted)
                paint(BLOCK_COMMENT, TermMuted)
            }
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    companion object {
        private val HTML_TAG = Regex("</?[a-zA-Z][a-zA-Z0-9-]*|/?>")
        private val STRING = Regex("\"[^\"\\n]*\"|'[^'\\n]*'")
        private val HTML_COMMENT = Regex("<!--[\\s\\S]*?-->")
        private val CSS_PROP = Regex("[a-zA-Z-]+(?=\\s*:)")
        private val NUMBER = Regex("\\b\\d+(\\.\\d+)?\\b")
        private val BLOCK_COMMENT = Regex("/\\*[\\s\\S]*?\\*/")
        private val LINE_COMMENT = Regex("//.*")
        private val JS_KEYWORD = Regex(
            "\\b(const|let|var|function|return|if|else|for|while|new|class|import|export|from|async|await|true|false|null|document|window)\\b"
        )
    }
}

/**
 * A small premium terminal for writing a website: code editor with file tabs, quick keys, paste / copy, Run (preview
 * inside the app) and a command line (help, ls, cat, new, rm, mv, open, run, clear, size, template, upload).
 * [onUpload] gets every file (name -> text); the review + upload steps follow in the caller.
 */
@Composable
fun TerminalDialog(
    busy: Boolean,
    onClose: () -> Unit,
    onUpload: (Map<String, String>) -> Unit
) {
    var files by rememberSaveable(stateSaver = FilesSaver) { mutableStateOf(mapOf(STARTER_FILE to STARTER_HTML)) }
    var active by rememberSaveable { mutableStateOf(STARTER_FILE) }
    var reload by remember { mutableStateOf(0) }
    var editor by remember(active, reload) { mutableStateOf(TextFieldValue(files[active].orEmpty())) }
    var command by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Map<String, String>?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val console = remember {
        mutableStateListOf(
            ConsoleLine("Photo Video URL terminal. Type help for commands.", K_TEXT)
        )
    }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    fun log(text: String, kind: Int = K_TEXT) {
        console.add(ConsoleLine(text, kind))
        while (console.size > 200) console.removeAt(0)
    }

    fun totalChars(map: Map<String, String>) = map.values.sumOf { it.length }

    fun setFile(name: String, text: String) {
        files = LinkedHashMap(files).also { it[name] = text }
    }

    fun openFile(name: String) {
        active = name
    }

    fun createFile(raw: String): Boolean {
        val name = WebsiteBuilder.safePath(raw)
        when {
            name == null -> log("new: \"$raw\" is not a valid file name (letters, numbers, . _ - and folders with /).", K_ERR)
            WebsiteBuilder.mimeOf(name) == null -> log("new: this file type is not allowed on a website.", K_ERR)
            files.containsKey(name) -> log("new: $name already exists.", K_ERR)
            else -> {
                setFile(name, "")
                openFile(name)
                log("created $name", K_OK)
                return true
            }
        }
        return false
    }

    fun startUpload() {
        val index = files.entries.firstOrNull { it.key.equals("index.html", true) }
        when {
            index == null -> log("upload: index.html is missing. Create it with: new index.html", K_ERR)
            index.value.isBlank() -> log("upload: index.html is empty.", K_ERR)
            else -> {
                log("preparing your website...", K_TEXT)
                onUpload(files)
            }
        }
    }

    fun runCommand(line: String) {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        log("$ ${line.trim()}", K_CMD)
        val arg = parts.getOrNull(1)
        when (parts[0].lowercase()) {
            "help" -> {
                log("help            this list")
                log("ls              list files")
                log("cat <file>      show a file")
                log("new <file>      create a file (e.g. new style.css)")
                log("open <file>     edit a file")
                log("rm <file>       delete a file")
                log("mv <a> <b>      rename a file")
                log("run             preview the website")
                log("size            size of everything")
                log("template        restore the starter index.html")
                log("clear           clear this screen")
                log("upload          publish the website")
            }
            "ls" -> files.entries.sortedBy { it.key }.forEach { log("${it.key}   ${SizeFormat.format(it.value.length.toLong())}") }
            "pwd" -> log("/")
            "cat" -> {
                val text = arg?.let { files[it] }
                if (arg == null) log("cat: which file?", K_ERR)
                else if (text == null) log("cat: $arg: no such file", K_ERR)
                else text.lineSequence().take(40).forEach { log(it) }
            }
            "new", "touch" -> if (arg == null) log("new: give a file name", K_ERR) else createFile(arg)
            "open", "edit" -> if (arg != null && files.containsKey(arg)) openFile(arg) else log("open: no such file", K_ERR)
            "rm" -> when {
                arg == null -> log("rm: which file?", K_ERR)
                !files.containsKey(arg) -> log("rm: $arg: no such file", K_ERR)
                arg.equals("index.html", true) -> log("rm: index.html is the main page and stays (you can edit it).", K_ERR)
                else -> {
                    files = LinkedHashMap(files).also { it.remove(arg) }
                    if (active == arg) openFile(STARTER_FILE)
                    log("deleted $arg", K_OK)
                }
            }
            "mv" -> {
                val to = parts.getOrNull(2)?.let { WebsiteBuilder.safePath(it) }
                when {
                    arg == null || parts.size < 3 -> log("mv: use  mv <old> <new>", K_ERR)
                    !files.containsKey(arg) -> log("mv: $arg: no such file", K_ERR)
                    arg.equals("index.html", true) -> log("mv: index.html cannot be renamed.", K_ERR)
                    to == null || WebsiteBuilder.mimeOf(to) == null -> log("mv: not a valid new name.", K_ERR)
                    files.containsKey(to) -> log("mv: $to already exists.", K_ERR)
                    else -> {
                        val text = files[arg].orEmpty()
                        files = LinkedHashMap(files).also { it.remove(arg); it[to] = text }
                        if (active == arg) openFile(to)
                        log("$arg -> $to", K_OK)
                    }
                }
            }
            "run" -> {
                if (files.keys.none { it == "index.html" }) log("run: index.html is missing.", K_ERR)
                else {
                    preview = files
                    log("running index.html ...", K_OK)
                }
            }
            "size" -> log("${files.size} file(s), ${SizeFormat.format(files.values.sumOf { it.length }.toLong())}")
            "template" -> {
                setFile(STARTER_FILE, STARTER_HTML)
                if (active == STARTER_FILE) reload++
                log("index.html restored", K_OK)
            }
            "clear" -> console.clear()
            "upload", "publish" -> startUpload()
            else -> log("${parts[0]}: command not found (type help)", K_ERR)
        }
    }

    LaunchedEffect(console.size) {
        if (console.isNotEmpty()) listState.animateScrollToItem(console.size - 1)
    }

    fun insert(snippet: String, cursorBack: Int = 0) {
        val sel = editor.selection
        val text = editor.text.replaceRange(sel.min, sel.max, snippet)
        val pos = sel.min + snippet.length - cursorBack
        editor = TextFieldValue(text, TextRange(pos))
        setFile(active, text)
    }

    val changed = files != mapOf(STARTER_FILE to STARTER_HTML)

    Dialog(
        onDismissRequest = { if (changed) confirmClose = true else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("terminal"), color = TermBg) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // ---- top bar ----
                Row(
                    modifier = Modifier.fillMaxWidth().background(TermPanel).padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (changed) confirmClose = true else onClose() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TermText)
                    }
                    Text(
                        text = "terminal",
                        color = TermGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { pasted ->
                            val sel = editor.selection
                            val text = editor.text.replaceRange(sel.min, sel.max, pasted)
                            if (totalChars(files) - editor.text.length + text.length > MAX_TERMINAL_CHARS) {
                                log("paste: too much code for the terminal. Use Upload file for big websites.", K_ERR)
                            } else {
                                editor = TextFieldValue(text, TextRange(sel.min + pasted.length))
                                setFile(active, text)
                            }
                        }
                    }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = TermText) }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(editor.text))
                        log("copied $active", K_OK)
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TermText) }
                    IconButton(onClick = {
                        setFile(active, "")
                        reload++
                    }) { Icon(Icons.Default.Delete, contentDescription = "Clear code", tint = TermText) }
                    IconButton(onClick = { runCommand("run") }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = TermGreen)
                    }
                    IconButton(onClick = { runCommand("upload") }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TermBlue)
                        else Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = TermBlue)
                    }
                }

                // ---- file tabs ----
                Row(
                    modifier = Modifier.fillMaxWidth().background(TermBg).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    files.keys.forEach { fileName ->
                        Text(
                            text = fileName,
                            color = if (fileName == active) TermText else TermMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { openFile(fileName) }
                                .background(if (fileName == active) TermPanel else TermBg)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    IconButton(onClick = { showNew = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "New file", tint = TermBlue)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TermLine))

                // ---- code editor with line numbers ----
                val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = TermText)
                val lineCount = editor.text.count { it == '\n' } + 1
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = (1..lineCount).joinToString("\n"),
                        style = codeStyle.copy(color = TermMuted, textAlign = TextAlign.End),
                        modifier = Modifier.widthIn(min = 38.dp).padding(horizontal = 6.dp)
                    )
                    Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        BasicTextField(
                            value = editor,
                            onValueChange = { v ->
                                val newTotal = totalChars(files) - editor.text.length + v.text.length
                                if (newTotal > MAX_TERMINAL_CHARS && v.text.length > editor.text.length) {
                                    log("The terminal is full (${MAX_TERMINAL_CHARS / 1000} KB). Use Upload file for big websites.", K_ERR)
                                } else {
                                    editor = v
                                    if (v.text != files[active]) setFile(active, v.text)
                                }
                            },
                            textStyle = codeStyle,
                            cursorBrush = SolidColor(TermGreen),
                            visualTransformation = CodeHighlighter(active.substringAfterLast('.', "").lowercase()),
                            modifier = Modifier.widthIn(min = 320.dp).padding(end = 24.dp).testTag("terminal_editor")
                        )
                    }
                }

                // ---- quick keys ----
                Row(
                    modifier = Modifier.fillMaxWidth().background(TermPanel).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickKey("Tab") { insert("  ") }
                    QuickKey("<>") { insert("<>", 1) }
                    QuickKey("</") { insert("</") }
                    QuickKey("/>") { insert("/>") }
                    QuickKey("\"\"") { insert("\"\"", 1) }
                    QuickKey("=") { insert("=") }
                    QuickKey("{}") { insert("{}", 1) }
                    QuickKey("()") { insert("()", 1) }
                    QuickKey(";") { insert(";") }
                    QuickKey(":") { insert(":") }
                    QuickKey("#") { insert("#") }
                    QuickKey("'") { insert("'") }
                    QuickKey("!") { insert("!") }
                }

                // ---- console ----
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TermLine))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(120.dp).background(TermBg).padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    items(console) { line ->
                        Text(
                            text = line.text,
                            color = when (line.kind) {
                                K_OK -> TermGreen
                                K_ERR -> TermRed
                                K_CMD -> TermBlue
                                K_JS -> TermOrange
                                else -> TermText
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(TermPanel).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$", color = TermGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.size(8.dp))
                    BasicTextField(
                        value = command,
                        onValueChange = { command = it.replace("\n", "") },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TermText),
                        cursorBrush = SolidColor(TermGreen),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            val c = command
                            command = ""
                            runCommand(c)
                        }),
                        modifier = Modifier.weight(1f).testTag("terminal_command")
                    )
                }
            }
        }
    }

    preview?.let { snapshot ->
        PreviewDialog(files = snapshot, onClose = { preview = null }, onConsole = { log(it, K_JS) })
    }

    if (showNew) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New file") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.trim() },
                    label = { Text("e.g. style.css") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (createFile(newName)) showNew = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Close the terminal?") },
            text = { Text("The code you wrote here will be lost.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; onClose() }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep writing") } }
        )
    }
}

@Composable
private fun QuickKey(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TermText,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TermLine)
            .clickable(onClick = onClick)
            .widthIn(min = 40.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/** Runs the website inside the app (nothing is uploaded): the files are served from memory to a WebView. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewDialog(files: Map<String, String>, onClose: () -> Unit, onConsole: (String) -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Throwable) {
            }
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("terminal_preview"), color = Color.White) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(TermPanel).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Back", tint = TermText) }
                    Text(
                        text = "preview  \u2022  nothing is uploaded",
                        color = TermText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reload", tint = TermGreen)
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportZoom(true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    val uri = request.url
                                    if (uri.host != PREVIEW_HOST) return null
                                    var path = uri.path.orEmpty().trimStart('/')
                                    if (path.isEmpty()) path = "index.html"
                                    val text = files[path] ?: files["$path.html"] ?: files["${path.trimEnd('/')}/index.html"]
                                    return if (text != null) {
                                        WebResourceResponse(
                                            WebsiteBuilder.mimeOf(path) ?: "text/html",
                                            "UTF-8",
                                            ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
                                        )
                                    } else {
                                        WebResourceResponse(
                                            "text/plain", "UTF-8", 404, "Not Found",
                                            emptyMap(), ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }

                                // links to other websites do not leave the preview
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    request.url.host != PREVIEW_HOST
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                    onConsole("[js] ${message.message()}  (${(message.sourceId() ?: "").substringAfterLast('/')}:${message.lineNumber()})")
                                    return true
                                }
                            }
                            loadUrl("https://$PREVIEW_HOST/index.html")
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}
