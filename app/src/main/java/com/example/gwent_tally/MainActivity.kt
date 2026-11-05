package com.example.gwent_tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gwent_tally.ui.theme.GwentTallyTheme

private enum class RowType(val label: String) {
    CLOSE("Close Combat"),
    RANGED("Ranged"),
    SIEGE("Siege")
}

private data class UnitCard(
    val id: Int,
    val name: String,
    val baseStrength: Int,
    val row: RowType,
    val hero: Boolean = false,
    val tightBond: Boolean = false,
    val bondGroup: Int? = null,
    val berserker: Boolean = false,
    val spy: Boolean = false,
    val moraleBoost: Boolean = false
)

private data class Effects(
    val bitingFrost: Boolean = false,
    val impenetrableFog: Boolean = false,
    val torrentialRain: Boolean = false,
    val skelligeStorm: Boolean = false,
    val closeHorn: Boolean = false,
    val rangedHorn: Boolean = false,
    val siegeHorn: Boolean = false,
    val mardroeme: Boolean = false,
    val spyBoost: Boolean = false,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GwentTallyTheme(dynamicColor = false) {
                GwentTallyApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GwentTallyApp() {
    var nextId by remember { mutableStateOf(1) }
    val units = remember { mutableStateListOf<UnitCard>() }
    var effects by remember { mutableStateOf(Effects()) }
    var optionsUnitId by remember { mutableStateOf<Int?>(null) }

    fun addUnit(row: RowType) {
        units += UnitCard(nextId, "Unit $nextId", 1, row)
        nextId++
    }

    fun changeStrength(id: Int, amount: Int) {
        val index = units.indexOfFirst { it.id == id }
        if (index >= 0) {
            val unit = units[index]
            units[index] = unit.copy(baseStrength = (unit.baseStrength + amount).coerceAtLeast(0))
        }
    }

    fun rowTotal(row: RowType): Int {
        val rowUnits = units.filter { it.row == row }
        val mardroeme = effects.mardroeme
        fun effectiveBondGroup(unit: UnitCard): Int? =
            unit.bondGroup ?: if (mardroeme && unit.berserker && unit.baseStrength == 2) 3 else null
        val bondGroupCounts = rowUnits
            .mapNotNull(::effectiveBondGroup)
            .groupingBy { it }
            .eachCount()
        val weather = when {
            row == RowType.CLOSE -> effects.bitingFrost
            row == RowType.RANGED -> effects.impenetrableFog || effects.skelligeStorm
            else -> effects.torrentialRain || effects.skelligeStorm
        }
        val horn = when (row) {
            RowType.CLOSE -> effects.closeHorn
            RowType.RANGED -> effects.rangedHorn
            RowType.SIEGE -> effects.siegeHorn
        }
        val moraleCards = rowUnits.count {
            it.moraleBoost || (mardroeme && it.berserker && it.baseStrength == 4)
        }
        return rowUnits.sumOf { unit ->
            val transformedStrength = if (mardroeme && unit.berserker) {
                when (unit.baseStrength) {
                    2 -> 8
                    4 -> 14
                    else -> unit.baseStrength
                }
            } else unit.baseStrength
            val strength = if (weather && !unit.hero) 1 else transformedStrength
            // Tight Bond only applies when another bonded card is in this row.
            // Each bonded card adds one copy of the base strength to the group.
            val group = effectiveBondGroup(unit)
            val bondedCount = group?.let { bondGroupCounts[it] ?: 0 } ?: 0
            val bonded = if (group != null && bondedCount > 1) {
                bondedCount
            } else {
                1
            }
            val boosted = if (horn && !unit.hero) 2 else 1
            val isMoraleSource = unit.moraleBoost ||
                (mardroeme && unit.berserker && unit.baseStrength == 4)
            val moraleBonus = if (!unit.hero && !isMoraleSource) moraleCards else 0
            val spyMultiplier = if (effects.spyBoost && unit.spy && !unit.hero) 2 else 1
            strength * bonded * boosted * spyMultiplier + moraleBonus
        }
    }

    fun effectiveUnitStrength(unit: UnitCard): Int {
        val rowUnits = units.filter { it.row == unit.row }
        val mardroeme = effects.mardroeme
        val group = unit.bondGroup ?: if (mardroeme && unit.berserker && unit.baseStrength == 2) 3 else null
        val bondCount = rowUnits.count {
            (it.bondGroup ?: if (mardroeme && it.berserker && it.baseStrength == 2) 3 else null) == group
        }.takeIf { group != null } ?: 0
        val weather = when (unit.row) {
            RowType.CLOSE -> effects.bitingFrost
            RowType.RANGED -> effects.impenetrableFog || effects.skelligeStorm
            RowType.SIEGE -> effects.torrentialRain || effects.skelligeStorm
        }
        val base = if (mardroeme && unit.berserker) when (unit.baseStrength) {
            2 -> 8
            4 -> 14
            else -> unit.baseStrength
        } else unit.baseStrength
        val strength = if (weather && !unit.hero) 1 else base
        val bonded = if (group != null && bondCount > 1) bondCount else 1
        val horn = when (unit.row) {
            RowType.CLOSE -> effects.closeHorn
            RowType.RANGED -> effects.rangedHorn
            RowType.SIEGE -> effects.siegeHorn
        }
        val moraleSources = rowUnits.count {
            it.moraleBoost || (mardroeme && it.berserker && it.baseStrength == 4)
        }
        val moraleSource = unit.moraleBoost ||
            (mardroeme && unit.berserker && unit.baseStrength == 4)
        val morale = if (!unit.hero && !moraleSource) moraleSources else 0
        val spy = if (effects.spyBoost && unit.spy && !unit.hero) 2 else 1
        return strength * bonded * (if (horn && !unit.hero) 2 else 1) * spy + morale
    }

    fun executeScorch() {
        val eligibleRows = RowType.entries.filter { rowTotal(it) >= 10 }.toSet()
        val highestStrength = units
            .filter { it.row in eligibleRows && !it.hero }
            .maxOfOrNull(::effectiveUnitStrength)
            ?: return
        val removedIds = units
            .filter {
                it.row in eligibleRows &&
                    !it.hero &&
                    effectiveUnitStrength(it) == highestStrength
            }
            .map { it.id }
            .toSet()
        units.removeAll { it.id in removedIds }
    }

    fun clearWeather() {
        effects = effects.copy(
            bitingFrost = false,
            impenetrableFog = false,
            torrentialRain = false,
            skelligeStorm = false
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gwent Tally", fontWeight = FontWeight.Bold)
                        Text("Your battlefield", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        units.clear()
                        effects = Effects()
                        nextId = 1
                    }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("TOTAL STRENGTH", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${rowTotal(RowType.CLOSE) + rowTotal(RowType.RANGED) + rowTotal(RowType.SIEGE)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Track only your side. Your opponent uses their own phone.")
                    }
                }
            }
            item {
                Text("Battlefield", style = MaterialTheme.typography.titleLarge)
            }
            items(RowType.entries) { row ->
                BattlefieldRow(
                    row = row,
                    units = units.filter { it.row == row },
                    total = rowTotal(row),
                    effects = effects,
                    onAdd = { addUnit(row) },
                    onStrengthChange = ::changeStrength,
                    onToggleHorn = {
                        effects = when (row) {
                            RowType.CLOSE -> effects.copy(closeHorn = !effects.closeHorn)
                            RowType.RANGED -> effects.copy(rangedHorn = !effects.rangedHorn)
                            RowType.SIEGE -> effects.copy(siegeHorn = !effects.siegeHorn)
                        }
                    },
                    onToggleBond = { id ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] = units[index].copy(tightBond = !units[index].tightBond)
                    },
                    onToggleHero = { id ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] = units[index].copy(hero = !units[index].hero)
                    },
                    onToggleBerserker = { id ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] = units[index].copy(berserker = !units[index].berserker)
                    },
                    onToggleSpy = { id ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] = units[index].copy(spy = !units[index].spy)
                    },
                    onToggleMoraleBoost = { id ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] =
                            units[index].copy(moraleBoost = !units[index].moraleBoost)
                    },
                    onSelectBondGroup = { id, group ->
                        val index = units.indexOfFirst { it.id == id }
                        if (index >= 0) units[index] =
                            units[index].copy(bondGroup = group)
                    },
                    onOpenOptions = { optionsUnitId = it },
                    onRemove = { id -> units.removeAll { it.id == id } }
                )
            }
            item {
                EffectsPanel(
                    effects = effects,
                    onEffectsChange = { effects = it },
                    onScorch = ::executeScorch,
                    onClearWeather = ::clearWeather
                )
                Spacer(Modifier.height(16.dp))
            }
        }
        val optionsUnit = units.firstOrNull { it.id == optionsUnitId }
        if (optionsUnit != null) {
            UnitOptionsDialog(
                unit = optionsUnit,
                selectedBondGroup = optionsUnit.bondGroup,
                derivedBondGroup = if (effects.mardroeme && optionsUnit.berserker &&
                    optionsUnit.baseStrength == 2) 3 else null,
                onDismiss = { optionsUnitId = null },
                onToggleHero = {
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] = units[index].copy(hero = !units[index].hero)
                },
                onToggleBond = {
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] = units[index].copy(tightBond = !units[index].tightBond)
                },
                onToggleBerserker = {
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] = units[index].copy(berserker = !units[index].berserker)
                },
                onToggleSpy = {
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] = units[index].copy(spy = !units[index].spy)
                },
                onToggleMoraleBoost = {
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] =
                        units[index].copy(moraleBoost = !units[index].moraleBoost)
                },
                onSelectBondGroup = { group ->
                    val index = units.indexOfFirst { it.id == optionsUnit.id }
                    if (index >= 0) units[index] =
                        units[index].copy(bondGroup = group)
                }
            )
        }
    }
}

@Composable
private fun BattlefieldRow(
    row: RowType,
    units: List<UnitCard>,
    total: Int,
    effects: Effects,
    onAdd: () -> Unit,
    onStrengthChange: (Int, Int) -> Unit,
    onToggleHorn: () -> Unit,
    onToggleBond: (Int) -> Unit,
    onToggleHero: (Int) -> Unit,
    onToggleBerserker: (Int) -> Unit,
    onToggleSpy: (Int) -> Unit,
    onToggleMoraleBoost: (Int) -> Unit,
    onSelectBondGroup: (Int, Int?) -> Unit,
    onOpenOptions: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val horn = when (row) {
        RowType.CLOSE -> effects.closeHorn
        RowType.RANGED -> effects.rangedHorn
        RowType.SIEGE -> effects.siegeHorn
    }
    val weatherActive = when {
        row == RowType.CLOSE -> effects.bitingFrost
        row == RowType.RANGED -> effects.impenetrableFog || effects.skelligeStorm
        else -> effects.torrentialRain || effects.skelligeStorm
    }
    val mardroeme = effects.mardroeme
    fun effectiveBondGroup(unit: UnitCard): Int? =
        unit.bondGroup ?: if (mardroeme && unit.berserker && unit.baseStrength == 2) 3 else null
    val bondGroupCounts = units.mapNotNull(::effectiveBondGroup)
        .groupingBy { it }
        .eachCount()
    val moraleCards = units.count {
        it.moraleBoost || (mardroeme && it.berserker && it.baseStrength == 4)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$total strength", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onAdd) { Text("+ Add unit") }
            }
            if (units.isEmpty()) {
                Text("No units yet", modifier = Modifier.padding(vertical = 12.dp))
            } else {
                units.forEach { unit ->
                    val weatherReduced = weatherActive && !unit.hero
                    val group = effectiveBondGroup(unit)
                    val bondedCount = group?.let { bondGroupCounts[it] ?: 0 } ?: 0
                    val bondActive = group != null && bondedCount > 1
                    val transformedStrength = if (mardroeme && unit.berserker) {
                        when (unit.baseStrength) {
                            2 -> 8
                            4 -> 14
                            else -> unit.baseStrength
                        }
                    } else unit.baseStrength
                    val strengthAfterWeatherAndBond = (if (weatherReduced) {
                        1
                    } else {
                        transformedStrength
                    }) * if (bondActive) bondedCount else 1
                    val hornMultiplier = if (horn && !unit.hero) 2 else 1
                    val spyMultiplier = if (effects.spyBoost && unit.spy && !unit.hero) 2 else 1
                    val isMoraleSource = unit.moraleBoost ||
                        (mardroeme && unit.berserker && unit.baseStrength == 4)
                    val moraleBonus = if (!unit.hero && !isMoraleSource) moraleCards else 0
                    val effectiveStrength =
                        strengthAfterWeatherAndBond * hornMultiplier * spyMultiplier + moraleBonus
                    val rowEffectSummary = buildList {
                        if (horn && !unit.hero) add("Horn ×2 row")
                    }.joinToString(" · ")
                    UnitEditor(
                        unit = unit,
                        effectiveStrength = effectiveStrength,
                        effectSummary = buildList {
                            if (unit.hero) add("Hero")
                            if (bondActive) add("Bond $group ×$bondedCount")
                            if (unit.berserker) add("Berserker")
                            if (mardroeme && unit.berserker) add("Mardroeme")
                            if (group != null) add("Bond group $group")
                            if (unit.spy) add("Spy")
                            if (spyMultiplier > 1) add("Spy ×2")
                            if (unit.moraleBoost) add("Morale Boost")
                            if (mardroeme && unit.berserker && unit.baseStrength == 4) {
                                add("Morale effect")
                            }
                            if (moraleBonus > 0) add("Morale +$moraleBonus")
                            if (rowEffectSummary.isNotEmpty()) add(rowEffectSummary)
                            if (weatherReduced) add("Weather")
                        }.joinToString(" · "),
                        boosted = (bondActive || horn || spyMultiplier > 1 || moraleBonus > 0) &&
                            !weatherReduced,
                        reduced = weatherReduced,
                        onStrengthChange = onStrengthChange,
                        onToggleBond = onToggleBond,
                        onToggleHero = onToggleHero,
                        onToggleBerserker = onToggleBerserker,
                        onToggleSpy = onToggleSpy,
                        onToggleMoraleBoost = onToggleMoraleBoost,
                        onSelectBondGroup = onSelectBondGroup,
                        onOpenOptions = onOpenOptions,
                        onRemove = onRemove
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = horn, onClick = onToggleHorn, label = { Text("Commander's Horn") })
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun UnitEditor(
    unit: UnitCard,
    effectiveStrength: Int,
    effectSummary: String,
    boosted: Boolean,
    reduced: Boolean,
    onStrengthChange: (Int, Int) -> Unit,
    onToggleBond: (Int) -> Unit,
    onToggleHero: (Int) -> Unit,
    onToggleBerserker: (Int) -> Unit,
    onToggleSpy: (Int) -> Unit,
    onToggleMoraleBoost: (Int) -> Unit,
    onSelectBondGroup: (Int, Int?) -> Unit,
    onOpenOptions: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$effectiveStrength",
            style = MaterialTheme.typography.headlineSmall,
            color = when {
                reduced -> Color(0xFFC62828)
                boosted -> Color(0xFF2E7D32)
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(48.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(unit.name, fontWeight = FontWeight.Medium)
            Text("Base strength ${unit.baseStrength}", style = MaterialTheme.typography.labelSmall)
            if (effectSummary.isNotEmpty()) {
                Text(
                    effectSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }
        Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
        ) {
        Text("Base", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { onStrengthChange(unit.id, -1) }) { Text("-") }
        Text("${unit.baseStrength}", fontWeight = FontWeight.Bold)
        IconButton(onClick = { onStrengthChange(unit.id, 1) }) { Text("+") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onOpenOptions(unit.id) }) { Text("Options") }
        TextButton(onClick = { onRemove(unit.id) }) { Text("Remove") }
        }
    }
}

@Composable
private fun UnitOptionsDialog(
    unit: UnitCard,
    selectedBondGroup: Int?,
    derivedBondGroup: Int?,
    onDismiss: () -> Unit,
    onToggleHero: () -> Unit,
    onToggleBond: () -> Unit,
    onToggleBerserker: () -> Unit,
    onToggleSpy: () -> Unit,
    onToggleMoraleBoost: () -> Unit,
    onSelectBondGroup: (Int?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${unit.name} options") },
        text = {
            Column {
                OptionToggle("Hero", unit.hero, onToggleHero)
                OptionToggle("Tight Bond", unit.tightBond, onToggleBond)
                Text("Bond group", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { group ->
                        FilterChip(
                            selected = (selectedBondGroup ?: derivedBondGroup) == group,
                            onClick = {
                                onSelectBondGroup(if (selectedBondGroup == group) null else group)
                            },
                            label = { Text("$group") }
                        )
                    }
                }
                OptionToggle("Berserker", unit.berserker, onToggleBerserker)
                OptionToggle("Spy", unit.spy, onToggleSpy)
                OptionToggle("Morale Boost", unit.moraleBoost, onToggleMoraleBoost)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun OptionToggle(label: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun EffectsPanel(
    effects: Effects,
    onEffectsChange: (Effects) -> Unit,
    onScorch: () -> Unit,
    onClearWeather: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Weather & effects", style = MaterialTheme.typography.titleLarge)
            Text("Tap an effect to apply it to your battlefield.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClearWeather, modifier = Modifier.fillMaxWidth()) {
                Text("Clear Weather")
            }
            EffectSwitch("Biting Frost", effects.bitingFrost) {
                onEffectsChange(effects.copy(bitingFrost = it))
            }
            EffectSwitch("Impenetrable Fog", effects.impenetrableFog) {
                onEffectsChange(effects.copy(impenetrableFog = it))
            }
            EffectSwitch("Torrential Rain", effects.torrentialRain) {
                onEffectsChange(effects.copy(torrentialRain = it))
            }
            EffectSwitch("Skellige Storm · Ranged + Siege", effects.skelligeStorm) {
                onEffectsChange(effects.copy(skelligeStorm = it))
            }
            EffectSwitch("Mardroeme", effects.mardroeme) {
                onEffectsChange(effects.copy(mardroeme = it))
            }
            EffectSwitch("Double Spy strength", effects.spyBoost) {
                onEffectsChange(effects.copy(spyBoost = it))
            }
            Button(onClick = onScorch, modifier = Modifier.fillMaxWidth()) {
                Text("Scorch")
            }
        }
    }
}

@Composable
private fun EffectSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
