package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.bunnlist.core.CollectionInsights
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.RemoteLot
import com.bunnlist.core.RemoteLotDetail
import org.junit.Rule
import org.junit.Test

class LotSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 100.0)
    private val lot = RemoteLotDetail(id = "1", slug = "massar", name = "Massar", nameAr = "مسار", roasterNameAr = "بلاك نايت", roasterSlug = "bk", originNameAr = "اليمن", originCode = "YE",
        process = "EXPERIMENTAL", flavorNotes = listOf("عنب", "تفاح", "مانجو", "عسل"), variety = "Typica", altitudeMeters = 2100)

    private fun shot(name: String, content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit) = paparazzi.snapshot(name) {
        BunnListTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalStill provides true) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        } }
    }

    @Test fun hero() = shot("hero") { FloatingBagHero(lot) }
    @Test fun facts() = shot("facts") { CoffeeFactsGrid(lot) }
    @Test fun cards() = shot("cards") {
        BuyButton("اشترِ من المحمصة", "b-k.coffee · الموقع الرسمي", true) {}
        RatingCard(0.0, 0, null, true) {}
        LotSection("مجموعتي") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle("لديّ", Icons.Filled.CheckCircle, true, Modifier.weight(1f)) {}
                Toggle("مفضّل", Icons.Filled.Favorite, false, Modifier.weight(1f)) {}
            }
        }
    }
    @Test fun collection() = shot("collection") {
        val items = listOf(CollectionItem(lotId = "1", slug = "a", nameAr = "أدهم", roasterNameAr = "بلاك نايت", originCode = "YE", originNameAr = "اليمن", owned = true, favorite = true, flavorNotes = listOf("توت", "شوكولا")),
            CollectionItem(lotId = "2", slug = "b", nameAr = "مسار", roasterNameAr = "أسانتي", originCode = "ET", flavorNotes = listOf("ياسمين")),
            CollectionItem(lotId = "3", slug = "c", nameAr = "كوف", roasterNameAr = "كوف", originCode = "CO", tried = true, flavorNotes = listOf("كراميل")))
        val ins = CollectionInsights(items)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountTile(3, "قهوة", Modifier.weight(1f)); CountTile(3, "دول", Modifier.weight(1f)); CountTile(3, "محامص", Modifier.weight(1f)); CountTile(1, "مفضّلة", Modifier.weight(1f))
        }
        Passport(ins.countryCodes, 24, true) {}
        TasteSpread(ins.shares, true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { CollectionCard(items[0]) {} }
            Box(Modifier.weight(1f)) { CollectionCard(items[2]) {} }
        }
    }

    @Test fun laurel() = shot("laurel") {
        RatingCard(4.5, 12, 5, true) {}
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stamp(com.bunnlist.core.Passport.Stamp("YE", "اليمن", "arabia", 3, "2026-10-01T00:00:00Z"), -8f)
            Stamp(com.bunnlist.core.Passport.Stamp("ET", "إثيوبيا", "africa", 1, "2026-11-01T00:00:00Z"), 5f)
            Stamp(com.bunnlist.core.Passport.Stamp("CO", "كولومبيا", "americas", 2, "2026-09-01T00:00:00Z"), -3f)
        }
    }
}
