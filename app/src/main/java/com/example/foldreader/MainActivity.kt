package com.example.foldreader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

private data class Book(val uri: String, val name: String, val type: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor = android.graphics.Color.rgb(24,31,34); window.navigationBarColor = android.graphics.Color.rgb(24,31,34); setContent { FoldReaderApp() } }
}

@Composable
private fun FoldReaderApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("library", 0) }
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf(loadBooks(prefs)) }
    var current by remember { mutableStateOf<Book?>(null) }
    var showUrl by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; val name = displayName(context, uri); val type = extension(name); if (type in setOf("epub","pdf","docx","png","jpg","jpeg","webp","gif","bmp")) { books = (books.filterNot { it.uri == uri.toString() } + Book(uri.toString(), name, type)); saveBooks(prefs, books) } }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFC99664), background = Color(0xFF182022), surface = Color(0xFF242E30))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF182022)) {
            if (current == null) LibraryScreen(books, onAdd = { picker.launch(arrayOf("application/epub+zip","application/pdf","application/vnd.openxmlformats-officedocument.wordprocessingml.document","image/*")) }, onUrl = { showUrl = true }, onOpen = { current = it }, onDelete = { b -> books = books - b; saveBooks(prefs, books) })
            else ReaderScreen(current!!, onBack = { current = null }, onProgress = { n -> prefs.edit().putInt("p:${current!!.uri}",n).apply() }, initial = prefs.getInt("p:${current!!.uri}",0), busy = busy, setBusy = { busy = it }, onError = { error = it })
        }
    }
    if (showUrl) UrlDialog(onDismiss = { showUrl = false }, onOpen = { raw ->
        showUrl = false
        scope.launch {
            busy = true
            try {
                val url = raw.trim(); val uri = withContext(Dispatchers.IO) { val connection = java.net.URL(url).openConnection(); connection.connectTimeout=12000; connection.readTimeout=20000; val bytes=connection.getInputStream().use { it.readBytes() }; val name = Uri.parse(url).lastPathSegment?.takeIf { it.contains('.') } ?: "Tài liệu tải về"; val f=File(context.cacheDir, "remote_${System.currentTimeMillis()}_${name.replace(Regex("[^\\w. -]"), "_")}"); f.writeBytes(bytes); Uri.fromFile(f) }
                val name = Uri.parse(url).lastPathSegment ?: "Sách từ đường dẫn"; val b = Book(uri.toString(),name,extension(name)); books = books.filterNot { it.uri == b.uri } + b; saveBooks(prefs,books); current=b
            } catch(e: Exception) { error = "Không tải được đường dẫn: ${e.localizedMessage ?: "lỗi mạng"}" } finally { busy=false }
        }
    })
    error?.let { msg -> AlertDialog(onDismissRequest={error=null}, title={Text("Không mở được tài liệu")}, text={Text(msg)}, confirmButton={TextButton(onClick={error=null}){Text("Đóng")}}) }
}

@Composable
private fun LibraryScreen(books: List<Book>, onAdd:()->Unit, onUrl:()->Unit, onOpen:(Book)->Unit, onDelete:(Book)->Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().background(Brush.verticalGradient(listOf(Color(0xFF182022),Color(0xFF202A2A)))).padding(horizontal=24.dp, vertical=18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                Column { Text("FOLDREADER", color=Color(0xFFC99664), fontSize=12.sp, fontWeight=FontWeight.Bold, letterSpacing=2.sp); Text("Thư viện", color=Color(0xFFF0E9DC), fontSize=32.sp, fontWeight=FontWeight.SemiBold) }
                Button(onClick=onAdd, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFC99664),contentColor=Color(0xFF172020))) { Text("＋  Thêm sách") }
            }
            Spacer(Modifier.height(8.dp)); Text("Sách của bạn  ·  ${books.size} tài liệu", color=Color(0xFF9AA6A1),fontSize=14.sp)
            if (books.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("▤", fontSize=48.sp, color=Color(0xFFC99664)); Spacer(Modifier.height(8.dp)); Text("Bắt đầu thư viện của bạn",color=Color(0xFFE9E2D6),fontSize=20.sp); Text("Thêm EPUB, PDF, Word hoặc ảnh để đọc.",color=Color(0xFF98A39E)); Spacer(Modifier.height(18.dp)); OutlinedButton(onClick=onUrl){Text("Mở từ đường dẫn")}} }
            else LazyColumn(contentPadding=PaddingValues(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { items(books,key={it.uri}) { b -> BookCard(b,onClick={onOpen(b)},onDelete={onDelete(b)}) } }
        }
        // Fold wide-screen companion panel appears only when there is room.
        BoxWithConstraints(Modifier.fillMaxHeight()) { if(maxWidth > 500.dp) Column(Modifier.width(250.dp).fillMaxHeight().background(Color(0xFF20292A)).padding(22.dp),verticalArrangement=Arrangement.Bottom) { Text("GÓC ĐỌC",color=Color(0xFFC99664),fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp); Spacer(Modifier.height(12.dp)); Text("Một khoảng lặng dành riêng cho những trang sách.",color=Color(0xFFD6D5C9),fontSize=17.sp,lineHeight=25.sp); Spacer(Modifier.height(20.dp)); TextButton(onClick=onUrl){Text("＋  Mở từ đường dẫn")}; Text("Tệp trong máy được mở an toàn qua bộ chọn của Android.",color=Color(0xFF899692),fontSize=12.sp) } }
    }
}
@Composable private fun BookCard(book:Book,onClick:()->Unit,onDelete:()->Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF252F30)).clickable(onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(width=54.dp,height=72.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(Color(0xFFC99664),Color(0xFF665044)))),contentAlignment=Alignment.Center){Text(icon(book.type),fontSize=24.sp,color=Color.White)}; Column(Modifier.weight(1f).padding(horizontal=16.dp)) { Text(book.name,color=Color(0xFFF0E9DC),fontWeight=FontWeight.Medium,fontSize=16.sp,maxLines=2); Text(book.type.uppercase()+"  ·  "+(if(book.type=="pdf") "PDF Renderer" else "Chạm để đọc tiếp"),color=Color(0xFF97A29D),fontSize=12.sp) }; TextButton(onClick=onDelete){Text("Xóa",color=Color(0xFFC69B87))} } }

@Composable
private fun ReaderScreen(book:Book,onBack:()->Unit,onProgress:(Int)->Unit,initial:Int,busy:Boolean,setBusy:(Boolean)->Unit,onError:(String)->Unit) {
    val context= LocalContext.current; val scope= rememberCoroutineScope(); var page by remember { mutableIntStateOf(initial) }; var count by remember { mutableIntStateOf(0) }; var textPages by remember { mutableStateOf<List<String>>(emptyList()) }; var pdfPage by remember { mutableStateOf<Bitmap?>(null) }; var pdf by remember { mutableStateOf<PdfRenderer?>(null) }; var image by remember { mutableStateOf<Bitmap?>(null) }; var title by remember { mutableStateOf(book.name) }; var loading by remember { mutableStateOf(true) }
    BackHandler(onBack=onBack)
    DisposableEffect(book.uri) { onDispose { pdf?.close() } }
    LaunchedEffect(book.uri) {
        loading=true
        withContext(Dispatchers.IO) { try {
            when(book.type) {
                "pdf" -> { val pfd = if(book.uri.startsWith("file:")) ParcelFileDescriptor.open(File(Uri.parse(book.uri).path!!),ParcelFileDescriptor.MODE_READ_ONLY) else context.contentResolver.openFileDescriptor(Uri.parse(book.uri),"r"); if(pfd!=null) { pdf=PdfRenderer(pfd); count=pdf!!.pageCount; page=initial.coerceIn(0,(count-1).coerceAtLeast(0)); renderPdf(pdf!!,page)?.let { pdfPage=it } } }
                "epub" -> { val local = localFile(context,Uri.parse(book.uri)); val chapters=readEpub(local); textPages=paginate(chapters.joinToString("\n\n")); count=textPages.size; page=initial.coerceIn(0,(count-1).coerceAtLeast(0)) }
                "docx" -> { val f=localFile(context,Uri.parse(book.uri)); val body=ZipFile(f).use { z -> z.getInputStream(z.getEntry("word/document.xml")).bufferedReader().readText() }; val plain=Jsoup.parse(body, "", Parser.xmlParser()).text(); textPages=paginate(plain); count=textPages.size; page=initial.coerceIn(0,(count-1).coerceAtLeast(0)) }
                else -> { val f=localFile(context,Uri.parse(book.uri)); image=android.graphics.BitmapFactory.decodeFile(f.absolutePath); count=1;page=0 }
            }
        } catch(e:Exception) { onError("${e.localizedMessage ?: "Không đọc được file"}. Nếu là file cloud, hãy tải xuống máy trước.") } }
        loading=false
    }
    val rotation by animateFloatAsState(if(loading) 3f else 0f,label="pageTurn")
    Column(Modifier.fillMaxSize().background(Color(0xFF101718))) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF1D2728)).padding(horizontal=18.dp, vertical=10.dp),verticalAlignment=Alignment.CenterVertically) { TextButton(onClick=onBack){Text("‹  Thư viện",color=Color(0xFFC99664))}; Text(title,Modifier.weight(1f),color=Color(0xFFE9E2D6),maxLines=1); Text(if(count>0) "${page+1} / $count" else "",color=Color(0xFF9AA6A1),fontSize=12.sp) }
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            if(loading||busy) CircularProgressIndicator(color=Color(0xFFC99664))
            else when(book.type) {
                "pdf" -> pdfPage?.let { androidx.compose.foundation.Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal=16.dp,vertical=8.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit) }
                "epub","docx" -> BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal=18.dp,vertical=14.dp)) { val wide=maxWidth>700.dp; Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.Center) { repeat(if(wide)2 else 1) { pane -> val idx=page+pane; if(idx<textPages.size) Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal=if(wide)20.dp else 4.dp).graphicsLayer { rotationY=rotation; cameraDistance=18* density }.background(Color(0xFFF0E9DC),RoundedCornerShape(8.dp)).padding(horizontal=if(wide)30.dp else 24.dp,vertical=24.dp)) { Text(title,color=Color(0xFF8B7257),fontSize=11.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(16.dp)); Text(textPages[idx],Modifier.weight(1f).verticalScroll(rememberScrollState()),color=Color(0xFF292721),fontSize=if(wide)18.sp else 17.sp,lineHeight=if(wide)30.sp else 28.sp); Spacer(Modifier.height(12.dp)); Text("${idx+1}",Modifier.align(Alignment.CenterHorizontally),color=Color(0xFF8B8172),fontSize=11.sp) } } } }
                else -> image?.let { androidx.compose.foundation.Image(it.asImageBitmap(),null,Modifier.fillMaxSize().padding(8.dp),contentScale=androidx.compose.ui.layout.ContentScale.Fit) }
            }
            if(!loading&&!busy) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Box(Modifier.fillMaxHeight().width(70.dp).clickable { if(page>0) { page--; onProgress(page); if(book.type=="pdf") scope.launch(Dispatchers.IO){pdf?.let{renderPdf(it,page)?.let{x->pdfPage=x}}} } }); Box(Modifier.fillMaxHeight().width(70.dp).clickable { if(page+1<count) { page++; onProgress(page); if(book.type=="pdf") scope.launch(Dispatchers.IO){pdf?.let{renderPdf(it,page)?.let{x->pdfPage=x}}} } }) }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF1D2728)).padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={if(page>0){page--;onProgress(page); if(book.type=="pdf")scope.launch(Dispatchers.IO){pdf?.let{renderPdf(it,page)?.let{x->pdfPage=x}}}}},enabled=page>0){Text("← Trước",color=Color(0xFFC99664))}; Text("Vị trí được lưu tự động",color=Color(0xFF899692),fontSize=12.sp); TextButton(onClick={if(page+1<count){page++;onProgress(page);if(book.type=="pdf")scope.launch(Dispatchers.IO){pdf?.let{renderPdf(it,page)?.let{x->pdfPage=x}}}}},enabled=page+1<count){Text("Tiếp →",color=Color(0xFFC99664))} }
    }
}

@Composable private fun UrlDialog(onDismiss:()->Unit,onOpen:(String)->Unit) { var url by remember { mutableStateOf("") }; AlertDialog(onDismissRequest=onDismiss,title={Text("Mở từ đường dẫn")},text={OutlinedTextField(url,{url=it},label={Text("https://…")},singleLine=true)},confirmButton={TextButton(onClick={onOpen(url)}){Text("Tải và mở")}},dismissButton={TextButton(onClick=onDismiss){Text("Hủy")}}) }
private fun renderPdf(renderer:PdfRenderer,index:Int):Bitmap? { if(index !in 0 until renderer.pageCount) return null; val p=renderer.openPage(index); val scale=(1800f/p.width).coerceAtMost(3f); val b=Bitmap.createBitmap((p.width*scale).toInt(),(p.height*scale).toInt(),Bitmap.Config.ARGB_8888); p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); return b }
private fun localFile(context:android.content.Context,uri:Uri):File { if(uri.scheme=="file") return File(uri.path!!); val name="reader_${uri.lastPathSegment?.hashCode() ?: System.nanoTime()}"; val f=File(context.cacheDir,name); if(!f.exists()) context.contentResolver.openInputStream(uri)!!.use { input-> f.outputStream().use(input::copyTo) }; return f }
private fun readEpub(file:File):List<String> { val zip=ZipFile(file); val opf=zip.entries().asSequence().firstOrNull { it.name.endsWith(".opf") } ?: error("EPUB thiếu OPF"); val xml=zip.getInputStream(opf).bufferedReader().readText(); val doc=Jsoup.parse(xml,"",Parser.xmlParser()); val base=opf.name.substringBeforeLast('/',""); val manifest=doc.select("manifest item").associate { it.attr("id") to it.attr("href") }; val ids=doc.select("spine itemref").map { it.attr("idref") }; return ids.mapNotNull { id -> manifest[id]?.let { href -> val entry=listOf(base,href).filter{it.isNotEmpty()}.joinToString("/"); zip.getEntry(entry)?.let { Jsoup.parse(zip.getInputStream(it).bufferedReader().readText()).text() } } }.also { zip.close() } }
private fun paginate(s:String):List<String> { val clean=s.replace(Regex("[ \\t]+")," ").trim(); if(clean.isEmpty()) return listOf("Tài liệu này không có văn bản trích xuất được."); val out=mutableListOf<String>(); var pos=0; val target=2600; while(pos<clean.length) { var end=(pos+target).coerceAtMost(clean.length); if(end<clean.length) { val cut=clean.lastIndexOf(' ',end); if(cut>pos+target/2) end=cut }; out.add(clean.substring(pos,end).trim()); pos=end }; return out }
private fun displayName(c:android.content.Context,u:Uri):String { var name=u.lastPathSegment ?: "Tài liệu"; runCatching { c.contentResolver.query(u,null,null,null,null)?.use { if(it.moveToFirst()){val i=it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0)name=it.getString(i)}} }; return name }
private fun extension(s:String)=s.substringAfterLast('.',"").lowercase()
private fun icon(t:String)=when(t){"pdf"->"▧";"epub"->"▤";"docx"->"W";else->"▧"}
private fun loadBooks(p:android.content.SharedPreferences):List<Book> { val raw=p.getString("books","") ?: ""; return raw.lineSequence().mapNotNull { line -> val x=line.split('|',limit=3); if(x.size==3) Book(x[0],x[1],x[2]) else null }.toList() }
private fun saveBooks(p:android.content.SharedPreferences,b:List<Book>) { p.edit().putString("books",b.joinToString("\n") { "${it.uri}|${it.name.replace("|","")}|${it.type}" }).apply() }
