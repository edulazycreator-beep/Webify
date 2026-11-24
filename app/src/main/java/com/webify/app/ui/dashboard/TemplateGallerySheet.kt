package com.webify.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.AppShortcutItem
import com.webify.app.domain.NativeConfigCodec
import com.webify.app.domain.NavIcon
import com.webify.app.domain.NavMenuItem
import com.webify.app.domain.NavStyle
import com.webify.app.domain.OfflineModeType
import com.webify.app.domain.ScreenOrientation
import com.webify.app.ui.components.IconBadge
import com.webify.app.ui.components.Pill
import com.webify.app.ui.theme.Dimens

/** Template proyek siap pakai: transformasi konfigurasi di atas proyek baru. */
enum class ProjectTemplate(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val highlights: List<String>,
    val apply: (WebProjectEntity) -> WebProjectEntity
) {
    BLANK("Kosong", "Mulai dari konfigurasi standar", Icons.Rounded.CropFree, listOf("Standar"), { it }),

    ECOMMERCE(
        "Toko Online", "E-commerce dengan navigasi bawah & upload bukti transfer", Icons.Rounded.ShoppingBag,
        listOf("Bottom nav 4 tab", "Upload + kamera", "WhatsApp/intent", "Offline modern", "Shortcut"),
        { p ->
            p.copy(
                enablePullToRefresh = true,
                enableFileUploadChooser = true,
                permCamera = true,
                enableExternalIntentRouting = true,
                enableDownloadManager = true,
                offlineModeType = OfflineModeType.DEFAULT_MODERN,
                navStyle = NavStyle.BOTTOM_BAR,
                navItemsJson = NativeConfigCodec.encodeNav(
                    listOf(
                        NavMenuItem("Beranda", "/", NavIcon.HOME.key),
                        NavMenuItem("Kategori", "/kategori", NavIcon.CATEGORY.key),
                        NavMenuItem("Keranjang", "/cart", NavIcon.CART.key),
                        NavMenuItem("Akun", "/akun", NavIcon.ACCOUNT.key)
                    )
                ),
                shortcutsJson = NativeConfigCodec.encodeShortcuts(
                    listOf(
                        AppShortcutItem("Keranjang", "Lihat keranjang belanja", "/cart", NavIcon.CART.key),
                        AppShortcutItem("Pesanan Saya", "Lacak pesanan saya", "/akun/pesanan", NavIcon.RECEIPT.key),
                        AppShortcutItem("Promo", "Promo hari ini", "/promo", NavIcon.STAR.key)
                    )
                ),
                enableAboutDialog = true
            )
        }
    ),

    BLOG(
        "Blog & Portofolio", "Bacaan nyaman, cache offline, & tombol bagikan", Icons.AutoMirrored.Rounded.Article,
        listOf("Cache-first offline", "Zoom", "Tombol share", "Drawer menu"),
        { p ->
            p.copy(
                offlineModeType = OfflineModeType.CACHE_FIRST,
                enableZoomControls = true,
                textZoomPercent = 105,
                hideScrollbars = true,
                navStyle = NavStyle.DRAWER,
                navItemsJson = NativeConfigCodec.encodeNav(
                    listOf(
                        NavMenuItem("Beranda", "/", NavIcon.HOME.key),
                        NavMenuItem("Artikel", "/blog", NavIcon.LIST.key),
                        NavMenuItem("Tentang", "/about", NavIcon.INFO.key),
                        NavMenuItem("Kontak", "/contact", NavIcon.CHAT.key)
                    )
                ),
                customCssCode = (p.customCssCode + "\n" + BLOG_CSS).trim(),
                customJsOnPageFinished = (p.customJsOnPageFinished + "\n" + SHARE_BUTTON_JS).trim()
            )
        }
    ),

    POS(
        "Dashboard / Kasir POS", "Terasa seperti aplikasi kasir native", Icons.Rounded.PointOfSale,
        listOf("Layar tetap menyala", "Tanpa seleksi teks", "Orientasi otomatis", "Siap cetak"),
        { p ->
            p.copy(
                keepScreenOn = true,
                disableTextSelection = true,
                enablePullToRefresh = false,
                enableSwipeBackForward = false,
                screenOrientation = ScreenOrientation.UNSPECIFIED,
                confirmExit = true,
                enableZoomControls = false,
                hideScrollbars = true,
                customJsOnPageFinished = (p.customJsOnPageFinished + "\n" + POS_PRINT_JS).trim()
            )
        }
    ),

    NEWS(
        "Portal Berita", "Bebas iklan, gelap otomatis, kontrol ukuran huruf", Icons.Rounded.Newspaper,
        listOf("Blokir iklan", "Gelap otomatis", "A−/A+ font", "Zoom"),
        { p ->
            p.copy(
                adBlockEnabled = true,
                forceDarkMode = true,
                textZoomPercent = 110,
                enableZoomControls = true,
                customCssCode = (p.customCssCode + "\n" + NEWS_AD_CSS).trim(),
                customJsOnPageFinished = (p.customJsOnPageFinished + "\n" + FONT_SIZE_JS).trim()
            )
        }
    );

    companion object {
        private const val BLOG_CSS = """/* Webify: bacaan nyaman */
article, .post, .entry-content { line-height: 1.7 !important; }
img { max-width: 100% !important; height: auto !important; }"""

        private const val SHARE_BUTTON_JS = """(function(){
  if (!window.WebifyBridge || document.getElementById('webify-share-fab')) return;
  var b = document.createElement('button');
  b.id = 'webify-share-fab'; b.setAttribute('aria-label','Bagikan'); b.textContent = '⤴';
  b.style.cssText = 'position:fixed;right:16px;bottom:24px;z-index:2147483647;width:52px;height:52px;border-radius:26px;border:0;'+
    'background:#2563EB;color:#fff;font-size:22px;box-shadow:0 6px 18px rgba(0,0,0,.25)';
  b.onclick = function(){ WebifyBridge.shareText(document.title + '\n' + location.href, document.title); };
  document.body.appendChild(b);
})();"""

        private const val POS_PRINT_JS = """(function(){
  // window.print() → PrintManager native (struk / simpan PDF)
  if (window.WebifyBridge && !window.__webifyPrintPatched) {
    window.__webifyPrintPatched = true;
    window.print = function(){ WebifyBridge.print(); };
  }
})();"""

        private const val NEWS_AD_CSS = """/* Webify: pemblokir elemen iklan umum */
iframe[src*="ads"], iframe[id*="google_ads"], .adsbygoogle, ins.adsbygoogle, .ad-banner, .ad-container, .ads, .advertisement,
[id^="div-gpt-ad"], [class*="sponsor"], .taboola, #taboola-below-article, .OUTBRAIN { display: none !important; }
@media (prefers-color-scheme: dark) { img { opacity: .92; } }"""

        private const val FONT_SIZE_JS = """(function(){
  if (document.getElementById('webify-font-ctl')) return;
  var key = 'webify_font_scale', s = parseFloat(localStorage.getItem(key) || '1');
  function apply(){ document.documentElement.style.fontSize = (s * 100) + '%'; localStorage.setItem(key, s); }
  apply();
  var box = document.createElement('div'); box.id = 'webify-font-ctl';
  box.style.cssText = 'position:fixed;right:12px;bottom:84px;z-index:2147483647;display:flex;gap:6px';
  ['A−','A+'].forEach(function(t, i){
    var b = document.createElement('button'); b.textContent = t;
    b.style.cssText = 'width:44px;height:44px;border-radius:22px;border:0;background:rgba(15,23,42,.85);color:#fff;font-weight:700';
    b.onclick = function(){ s = Math.min(1.6, Math.max(0.8, s + (i ? 0.1 : -0.1))); apply(); };
    box.appendChild(b);
  });
  document.body.appendChild(box);
})();"""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateGallerySheet(selected: ProjectTemplate, onDismiss: () -> Unit, onSelect: (ProjectTemplate) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Dimens.RadiusCard, topEnd = Dimens.RadiusCard)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)
        ) {
            Text("Mulai dari Template", style = MaterialTheme.typography.titleLarge)
            Text(
                "Konfigurasi siap pakai — semua tetap bisa diubah di Builder Studio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ProjectTemplate.entries.forEach { t ->
                val isSel = t == selected
                val bg by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "tplBg"
                )
                Surface(
                    onClick = { onSelect(t) },
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    color = bg,
                    border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(t.icon, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), MaterialTheme.colorScheme.primary, size = 40.dp)
                            Spacer(Modifier.width(Dimens.IconTextGap))
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (isSel) Icon(Icons.Rounded.Check, "Dipilih", Modifier.size(Dimens.IconBar), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (t != ProjectTemplate.BLANK) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                t.highlights.take(3).forEach {
                                    Pill(it, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (t.highlights.size > 3) Pill("+${t.highlights.size - 3}", MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Text(
                "Tip: URL menu & shortcut ditulis relatif (mis. /cart) dan otomatis digabung dengan URL website Anda.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
