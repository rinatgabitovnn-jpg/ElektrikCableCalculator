package ru.om.cablecalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CalculatorScreen() } }
    }
}

private data class CalcResult(
    val current: Double, val section: Double?, val iz: Double?,
    val dropPercent: Double?, val thermalSection: Double?, val voltageSection: Double?,
    val breaker: Int?, val breakerCondition: Boolean?
)

private val sections = listOf(1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0, 300.0)

// ГОСТ Р 50571.5.52-2011, табл. B.52.2 (PVC, 2 нагруженные жилы), медь.
// Используется только колонка B2 как расчётная база для кабеля в трубе,
// закреплённой на поверхности стены при условиях таблицы A.52.3, позиция 5.
private val b52_2_a2 = listOf(14.0, 18.5, 25.0, 32.0, 43.0, 57.0, 75.0, 92.0, 110.0, 139.0, 167.0, 192.0, 219.0, 248.0, 291.0, 334.0)
private val b52_2_b2 = listOf(16.5, 23.0, 30.0, 38.0, 52.0, 69.0, 90.0, 111.0, 133.0, 168.0, 201.0, 232.0, 258.0, 294.0, 344.0, 394.0)

// ГОСТ Р 50571.5.52-2011, табл. B.52.4 (PVC, 3 нагруженные жилы), медь.
private val b52_4_a2 = listOf(13.0, 17.5, 23.0, 29.0, 39.0, 52.0, 68.0, 83.0, 99.0, 125.0, 150.0, 172.0, 196.0, 223.0, 261.0, 298.0)
private val b52_4_b2 = listOf(15.0, 20.0, 27.0, 34.0, 46.0, 62.0, 80.0, 99.0, 118.0, 149.0, 179.0, 206.0, 225.0, 255.0, 297.0, 339.0)

private val tempFactors = mapOf(
    10 to 1.22, 15 to 1.17, 20 to 1.12, 25 to 1.06, 30 to 1.00,
    35 to 0.94, 40 to 0.87, 45 to 0.79, 50 to 0.71, 55 to 0.61, 60 to 0.50
)
private val groupFactors = mapOf(
    1 to 1.00, 2 to 0.80, 3 to 0.70, 4 to 0.65, 5 to 0.60, 6 to 0.57,
    7 to 0.54, 8 to 0.52, 9 to 0.50, 12 to 0.45, 16 to 0.41, 20 to 0.38
)
private val breakerRatings = listOf(6, 10, 13, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400)

private fun calculate(
    threePhase: Boolean, usePower: Boolean, currentInput: Double, powerKw: Double,
    voltage: Double, cosPhi: Double, length: Double, temperature: Int, groups: Int,
    loadedCores: Int, groupedTight: Boolean, maxDrop: Double, autoBreaker: Boolean, manualBreaker: Int?
): CalcResult {
    val ib = if (usePower) {
        val p = powerKw * 1000.0
        if (threePhase) p / (sqrt(3.0) * voltage * cosPhi) else p / (voltage * cosPhi)
    } else currentInput

    // B2 reference case for multicore cable in conduit fixed on a surface.
    // For spaced parallel circuits (>2 cable diameters horizontally), group derating
    // is not applied per the note to table B.52.17.
    val base = if (loadedCores == 2) b52_2_b2 else b52_4_b2
    val groupFactor = if (groupedTight) (groupFactors[groups] ?: 1.0) else 1.0
    val corrected = base.map { it * (tempFactors[temperature] ?: 1.0) * groupFactor }
    val thermalIndex = corrected.indexOfFirst { it + 1e-9 >= ib }
    val thermalSection = if (thermalIndex >= 0) sections[thermalIndex] else null

    // Preliminary voltage-drop screening calculation. Copper resistivity corresponds
    // approximately to hot conductor operation; cable reactance is not included.
    val rho = 0.0225 // ohm*mm²/m
    val rFactor = rho * cosPhi
    // X is omitted deliberately; result is marked preliminary in the UI.
    val drops = sections.map { s ->
        val numerator = if (threePhase) sqrt(3.0) else 2.0
        numerator * ib * length * rFactor / (s * voltage) * 100.0
    }
    val dropIndex = drops.indexOfFirst { it <= maxDrop }
    val voltageSection = if (dropIndex >= 0) sections[dropIndex] else null
    val chosenIndex = if (thermalIndex >= 0 && dropIndex >= 0) max(thermalIndex, dropIndex) else -1
    val chosenSection = if (chosenIndex >= 0) sections[chosenIndex] else null
    val chosenIz = if (chosenIndex >= 0) corrected[chosenIndex] else null
    val chosenDrop = if (chosenIndex >= 0) drops[chosenIndex] else null
    val selectedBreaker = if (autoBreaker) {
        if (chosenIz == null) null else breakerRatings.firstOrNull { it + 1e-9 >= ib && it <= chosenIz }
    } else manualBreaker
    val breakerOk = selectedBreaker != null && chosenIz != null &&
        selectedBreaker + 1e-9 >= ib && selectedBreaker <= chosenIz
    return CalcResult(ib, chosenSection, chosenIz, chosenDrop, thermalSection, voltageSection, selectedBreaker, breakerOk)
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun CalculatorScreen() {
    var threePhase by remember { mutableStateOf(false) }
    var usePower by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("25") }
    var power by remember { mutableStateOf("5") }
    var voltage by remember { mutableStateOf("230") }
    var cosPhi by remember { mutableStateOf("0.95") }
    var length by remember { mutableStateOf("20") }
    var temperature by remember { mutableIntStateOf(30) }
    var groups by remember { mutableIntStateOf(1) }
    var loadedCores by remember { mutableIntStateOf(2) }
    var groupedTight by remember { mutableStateOf(true) }
    var maxDrop by remember { mutableStateOf("3") }
    var breakerAuto by remember { mutableStateOf(true) }
    var breakerManual by remember { mutableStateOf("25") }

    fun number(raw: String): Double = raw.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN
    val v = number(voltage)
    val cos = number(cosPhi)
    val len = number(length)
    val i = number(current)
    val p = number(power)
    val d = number(maxDrop)
    val valid = v.isFinite() && v > 0 && cos.isFinite() && cos in 0.1..1.0 && len.isFinite() && len >= 0 && d.isFinite() && d > 0 &&
        if (usePower) p.isFinite() && p > 0 else i.isFinite() && i > 0
    val result = if (valid) calculate(
        threePhase, usePower, i, p, v, cos, len, temperature, groups,
        loadedCores, groupedTight, d, breakerAuto, breakerManual.toIntOrNull()
    ) else null

    Scaffold(topBar = {
        Surface(color = androidx.compose.ui.graphics.Color(0xFF17212B)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("ЭЛЕКТРИК", color = androidx.compose.ui.graphics.Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                Text("Расчёт сечения кабеля", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
                Text("ВВГнг(А)-LS · медь · гофра", color = androidx.compose.ui.graphics.Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("1. Электрическая сеть", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !threePhase, onClick = { threePhase = false; voltage = "230" }, label = { Text("1 фаза · 230 В") })
                FilterChip(selected = threePhase, onClick = { threePhase = true; voltage = "400" }, label = { Text("3 фазы · 400 В") })
            }
            Text("2. Ввод нагрузки", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !usePower, onClick = { usePower = false }, label = { Text("Амперы") })
                FilterChip(selected = usePower, onClick = { usePower = true }, label = { Text("Киловатты") })
            }
            if (usePower) NumberField("Мощность, кВт", power) { power = it }
            else NumberField("Ток нагрузки, А", current) { current = it }
            NumberField("Напряжение, В", voltage) { voltage = it }
            NumberField("Коэффициент мощности cos φ", cosPhi) { cosPhi = it }

            HorizontalDivider()
            Text("3. Условия прокладки", fontWeight = FontWeight.Bold)
            NumberField("Длина линии в одну сторону, м", length) { length = it }
            Text("Монтаж: открытая гофра, закреплённая непосредственно на поверхности стены или потолка")
            Text("Расчётная база B2 применима к кабелю в трубе, закреплённой на поверхности, при условиях таблицы A.52.3, позиция 5. Для потолочной полости, подвесного потолка, теплоизоляции или закрытого пространства этот расчёт автоматически неприменим — способ прокладки нужно определить отдельно.")
            Text("Количество нагруженных жил")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = loadedCores == 2, onClick = { loadedCores = 2 }, label = { Text("2 жилы") })
                FilterChip(selected = loadedCores == 3, onClick = { loadedCores = 3 }, label = { Text("3 жилы") })
            }
            Text("Температура окружающей среды: $temperature °C")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60).forEach { t ->
                    FilterChip(selected = temperature == t, onClick = { temperature = t }, label = { Text("$t") })
                }
            }
            Text("Количество совместно проложенных однотипных и одинаково нагруженных цепей: $groups")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 16, 20).forEach { n ->
                    FilterChip(selected = groups == n, onClick = { groups = n }, label = { Text("$n") })
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = groupedTight, onCheckedChange = { groupedTight = it })
                Text("Гофры проложены группой / близко друг к другу")
            }
            Text(if (groupedTight) "Применяется коэффициент группы по B.52.17, позиция 1." else "Коэффициент группировки не применяется: предполагается горизонтальный зазор более 2 наружных диаметров кабеля.")
            NumberField("Допустимое падение напряжения, %", maxDrop) { maxDrop = it }

            HorizontalDivider()
            Text("4. Автоматический выключатель", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = breakerAuto, onCheckedChange = { breakerAuto = it })
                Text("Подобрать предварительный номинал")
            }
            if (!breakerAuto) NumberField("Номинал автомата, А", breakerManual) { breakerManual = it }

            HorizontalDivider()
            Text("5. Результат", fontWeight = FontWeight.Bold)
            if (!valid || result == null) {
                Text("Проверь исходные данные: ток/мощность, напряжение, cos φ, длину и допустимое падение.")
            } else {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Расчётный ток: %.2f А".format(result.current), style = MaterialTheme.typography.titleMedium)
                        if (result.section != null) {
                            Text("${result.section} мм²", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("Предварительно выбранное сечение ВВГнг(А)-LS")
                            Text("Допустимый ток после поправок: %.1f А".format(result.iz))
                            Text("Расчётное падение напряжения: %.2f %%".format(result.dropPercent))
                            Text("Минимум по нагреву: ${result.thermalSection} мм²")
                            Text("Минимум по падению: ${result.voltageSection} мм²")
                            Text("Автомат: ${result.breaker?.let { "$it А" } ?: "подходящий номинал не найден"}")
                            Text("Проверка Ib ≤ In ≤ Iz: ${if (result.breakerCondition == true) "условие выполнено" else "не выполнено / требуется проверка"}",
                                color = if (result.breakerCondition == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        } else {
                            Text("Подходящее сечение не найдено в диапазоне 1,5–300 мм².", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ограничения расчёта — прочитай перед применением", fontWeight = FontWeight.Bold)
                    Text("Токовые значения взяты из таблиц B.52.2 (2 нагруженные жилы) и B.52.4 (3 нагруженные жилы), медь, ПВХ-изоляция, колонка B2. Температурная поправка — B.52.14; групповой коэффициент — позиция 1 таблицы B.52.17 для однотипных одинаково нагруженных цепей.")
                    Text("B2 — расчётная база для многожильного кабеля в трубе, закреплённой на поверхности стены/строительной поверхности в условиях таблицы A.52.3, позиция 5. Это не универсальная модель для потолочной полости, подвесного потолка, теплоизоляции, закрытого короба или замкнутого пространства. В таких случаях способ прокладки выбирают отдельно по фактической конструкции; нельзя автоматически переносить B2.")
                    Text("Падение напряжения — упрощённая оценка по активному сопротивлению меди; реактивное сопротивление не учитывается. Номинал автомата проверяется только по Ib ≤ In ≤ Iz. Не проверены I₂, характеристика автомата, ток КЗ, петля «фаза-ноль», время автоматического отключения, термическая стойкость при КЗ, гармоники и баланс фаз. Приложение не выдаёт заключение о полном соответствии ПУЭ/ГОСТ и не заменяет проектную проверку.")
                }
            }
            OutlinedButton(onClick = {
                threePhase = false; usePower = false; current = "25"; power = "5"; voltage = "230"
                cosPhi = "0.95"; length = "20"; temperature = 30; groups = 1
                loadedCores = 2; groupedTight = true; maxDrop = "3"; breakerAuto = true; breakerManual = "25"
            }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить параметры") }
            Spacer(Modifier.height(20.dp))
        }
    }
}
