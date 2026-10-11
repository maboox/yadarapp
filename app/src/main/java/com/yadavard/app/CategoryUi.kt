@file:OptIn(ExperimentalLayoutApi::class)

package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Category chips with a trailing "new category" chip. */
@Composable
fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Categories.all.forEach { c ->
            FilterChip(selected = c.key == selected, onClick = { onSelect(c.key) }, label = { Text(c.name(AppDisplay.language)) },
                leadingIcon = { Icon(c.key.catIcon(), null, Modifier.size(18.dp), tint = Color(c.color)) })
        }
        AssistChip(onClick = { adding = true }, label = { Text(t("دستهٔ جدید", "New category")) },
            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
    }
    if (adding) CategoryDialog(null, onDismiss = { adding = false }) { name, color, icon ->
        onSelect(Categories.add(context, name, color, icon).key); adding = false
    }
}

/** Settings card listing all categories; user-made ones can be edited or deleted. */
@Composable
fun CategoriesCard() {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }
    SettingsCard(t("دسته‌بندی‌ها", "Categories"), Icons.Rounded.Category) {
        Text(t("دسته‌های خودت را بساز (مثلاً دارو، باشگاه، دکتر بچه). یادار از روی متن، دستهٔ مناسب را خودش انتخاب می‌کند؛ اگر نشد «عمومی» می‌گذارد.",
            "Create your own categories. Yadar picks the right one from the text by itself, or “General” if unsure."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Categories.all.forEach { c ->
                InputChip(selected = false, onClick = { if (!c.builtIn) editing = c },
                    label = { Text(c.name(AppDisplay.language)) },
                    leadingIcon = { Icon(c.key.catIcon(), null, Modifier.size(18.dp), tint = Color(c.color)) },
                    trailingIcon = if (c.builtIn) null else {
                        { Icon(Icons.Rounded.Close, t("حذف", "Delete"), Modifier.size(18.dp).clickable { deleting = c }) }
                    })
            }
        }
        FilledTonalButton(onClick = { adding = true }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("دستهٔ جدید", "New category"))
        }
    }
    if (adding) CategoryDialog(null, { adding = false }) { name, color, icon -> Categories.add(context, name, color, icon); adding = false }
    editing?.let { def ->
        CategoryDialog(def, { editing = null }) { name, color, icon ->
            Categories.update(context, def.copy(nameFa = name.trim().take(40), color = color, icon = icon)); editing = null
        }
    }
    deleting?.let { def ->
        AlertDialog(onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(t("حذف «${def.nameFa}»؟", "Delete “${def.nameFa}”?")) },
            text = { Text(t("یادآوری‌های این دسته حذف نمی‌شوند و به «عمومی» منتقل می‌شوند.", "Its reminders are kept and moved to “General”.")) },
            confirmButton = { TextButton({ Categories.remove(context, def.key); deleting = null }) { Text(t("حذف", "Delete"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = null }) { Text(t("انصراف", "Cancel")) } })
    }
}

@Composable
fun CategoryDialog(initial: CategoryDef?, onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.nameFa.orEmpty()) }
    var color by remember { mutableLongStateOf(initial?.color ?: Categories.palette[Categories.all.size % Categories.palette.size]) }
    var icon by remember { mutableStateOf(initial?.icon ?: "star") }
    val duplicate = Categories.all.any { it.key != initial?.key && (it.nameFa == name.trim() || it.nameEn.equals(name.trim(), true)) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("دستهٔ جدید", "New category") else t("ویرایش دسته", "Edit category")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(t("نام", "Name")) }, placeholder = { Text(t("مثلاً دارو", "e.g. Pills")) },
                    isError = duplicate, supportingText = if (duplicate) { { Text(t("این نام وجود دارد", "Already exists")) } } else null,
                    leadingIcon = { Icon(Categories.icons[icon] ?: Icons.Rounded.Star, null, tint = Color(color)) },
                    shape = RoundedCornerShape(14.dp))
                Text(t("رنگ", "Color"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.palette.forEach { c ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c })
                    }
                }
                Text(t("آیکن", "Icon"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Categories.icons.forEach { (k, v) ->
                        val sel = k == icon
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Color(color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { icon = k }, contentAlignment = Alignment.Center) {
                            Icon(v, null, Modifier.size(20.dp), tint = if (sel) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), color, icon) }, enabled = name.isNotBlank() && !duplicate) { Text(t("ذخیره", "Save")) } },
        dismissButton = { TextButton(onDismiss) { Text(t("انصراف", "Cancel")) } })
}
