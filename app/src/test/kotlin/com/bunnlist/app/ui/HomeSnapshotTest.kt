package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.Facet
import com.bunnlist.core.ForYou
import com.bunnlist.core.Me
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.TrendingLot
import org.junit.Rule
import org.junit.Test

/** The home's sections with sample data, to compare with the iOS home without a device. */
class HomeSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)

    private val lot = RemoteLot(id = "1", slug = "rozitta", name = "Rozitta", nameAr = "روزيتا", roasterNameAr = "بلاك نايت", originNameAr = "كوستاريكا",
        originCode = "CR", process = "NATURAL", flavorNotes = listOf("أناناس", "توت", "كرز"))
    private val lot2 = lot.copy(id = "2", slug = "saddam", nameAr = "صدّام - اليمن", roasterNameAr = "محمصة أسانتي", originNameAr = "اليمن", originCode = "YE", flavorNotes = listOf("الكراميل", "ياسمين"))

    private fun shot(name: String, content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) = paparazzi.snapshot(name) {
        BunnListTheme {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
            }
        }
    }

    @Test fun top() = shot("top") {
        DailyPickCard(ForYou.DailyPick(lot, score = null, rated = 1), startShown = true) {}
        DailyPickCard(ForYou.DailyPick(lot2, score = 92, reason = "لأنك تحب المحاصيل المجففة", rated = 5), startShown = true) {}
    }

    @Test fun middle() = shot("middle") {
        RoasterNewsRow(listOf(ForYou.RoasterNews("بلاك نايت", listOf(ForYou.NewsLot("a", "مسار", null, "2026-10-26T00:00:00Z"), ForYou.NewsLot("b", "ناردوس", null, "2026-10-26T00:00:00Z"), ForYou.NewsLot("c", "لوليت", null, "2026-10-26T00:00:00Z"))))) {}
        TrendingRow(listOf(TrendingLot(id = "t1", slug = "t1", nameAr = "كولومبي - أورلاندو", roasterNameAr = "محمصة كوف", rank = 1, reason = "added", adds = 1, flavorNotes = listOf("توت")),
            TrendingLot(id = "t2", slug = "t2", nameAr = "ملك كولومبيا", roasterNameAr = "محمصة اناناس", rank = 2, reason = "added", adds = 1, flavorNotes = listOf("كاكاو"))), {}, {})
        TasteSection(null, null, null, {}, {}, {})
    }

    @Test fun newsRings() = shot("newsRings") {
        val lots = listOf(ForYou.NewsLot("x", "قهوة", null, "2026-10-26T00:00:00Z"))
        CompositionLocalProvider(LocalStill provides true) {
            RoasterNewsRow(listOf("محمصة أسانتي", "محمصة يوف", "ميلانو لاب", "محمصة ممتد", "محمصة قاف").mapIndexed { i, n -> ForYou.RoasterNews(n, List(i % 3 + 1) { lots[0].copy(slug = "$n$it") }) }) {}
        }
    }

    @Test fun bottom() = shot("bottom") {
        OriginsRow(listOf(Facet("CO", "كولومبيا", 214), Facet("ET", "إثيوبيا", 157), Facet("BR", "البرازيل", 59))) {}
        ExploreTiles({}, {})
        HomeSectionHeader("وصلت حديثًا", "آخر قهوة من كل محمصة", "عرض الكل") {}
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DiscoverLotCard(lot2, Modifier.weight(1f)) {}
            DiscoverLotCard(lot, Modifier.weight(1f)) {}
        }
    }

    /** Dark mode: titles must be cream, not black, and the notes must read. */
    @Test fun bottomDark() {
        ThemeMode.mode = "dark"
        try {
            shot("bottomDark") {
                ExploreTiles({}, {})
                HomeSectionHeader("وصلت حديثًا", "آخر قهوة من كل محمصة", "عرض الكل") {}
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscoverLotCard(lot2, Modifier.weight(1f)) {}
                    DiscoverLotCard(lot, Modifier.weight(1f)) {}
                }
                SectionTitle("ما فيه شي هنا للحين")
            }
        } finally { ThemeMode.mode = "system" }
    }

    @Test fun end() = shot("end") {
        CatalogEntryCard(736) {}
        Spacer(Modifier.weight(1f))
        FloatingTabBar(Tab.Discover, {})
    }
}
