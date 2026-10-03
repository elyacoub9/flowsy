package com.elyacoub9.flowsy.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.domain.category.BuiltInCategory
import com.elyacoub9.flowsy.domain.category.Category
import com.elyacoub9.flowsy.ui.theme.CategoryPalette
import com.elyacoub9.flowsy.ui.theme.OnSurfaceMuted

/** Stored icon keys → drawables. Keys are stable; never store resource IDs. */
object CategoryIcons {
    data class IconOption(val key: String, @param:DrawableRes val drawable: Int, @param:StringRes val label: Int)

    val options = listOf(
        IconOption("cart", R.drawable.ic_cat_cart, R.string.icon_cart),
        IconOption("restaurant", R.drawable.ic_cat_restaurant, R.string.icon_restaurant),
        IconOption("coffee", R.drawable.ic_cat_coffee, R.string.icon_coffee),
        IconOption("bus", R.drawable.ic_cat_bus, R.string.icon_bus),
        IconOption("car", R.drawable.ic_cat_car, R.string.icon_car),
        IconOption("plane", R.drawable.ic_cat_plane, R.string.icon_plane),
        IconOption("health", R.drawable.ic_cat_health, R.string.icon_health),
        IconOption("sport", R.drawable.ic_cat_sport, R.string.icon_sport),
        IconOption("bag", R.drawable.ic_cat_bag, R.string.icon_bag),
        IconOption("gift", R.drawable.ic_cat_gift, R.string.icon_gift),
        IconOption("movie", R.drawable.ic_cat_movie, R.string.icon_movie),
        IconOption("house", R.drawable.ic_cat_house, R.string.icon_house),
        IconOption("bolt", R.drawable.ic_cat_bolt, R.string.icon_bolt),
        IconOption("phone", R.drawable.ic_cat_phone, R.string.icon_phone),
        IconOption("school", R.drawable.ic_cat_school, R.string.icon_school),
        IconOption("heart", R.drawable.ic_cat_heart, R.string.icon_heart),
        IconOption("other", R.drawable.ic_cat_other, R.string.icon_other),
    )

    private val byKey = options.associateBy { it.key }

    @DrawableRes
    fun drawable(key: String): Int = (byKey[key] ?: byKey.getValue("other")).drawable
}

/** Stored color keys → theme palette. Unknown keys fall back to grey. */
object CategoryColors {
    data class ColorOption(val key: String, val color: Color, @param:StringRes val label: Int)

    val options = listOf(
        ColorOption("red", CategoryPalette[0], R.string.color_red),
        ColorOption("orange", CategoryPalette[1], R.string.color_orange),
        ColorOption("yellow", CategoryPalette[2], R.string.color_yellow),
        ColorOption("green", CategoryPalette[3], R.string.color_green),
        ColorOption("teal", CategoryPalette[4], R.string.color_teal),
        ColorOption("cyan", CategoryPalette[5], R.string.color_cyan),
        ColorOption("blue", CategoryPalette[6], R.string.color_blue),
        ColorOption("violet", CategoryPalette[7], R.string.color_violet),
        ColorOption("purple", CategoryPalette[8], R.string.color_purple),
        ColorOption("pink", CategoryPalette[9], R.string.color_pink),
    )

    private val byKey = options.associateBy { it.key }

    fun color(key: String): Color = byKey[key]?.color ?: OnSurfaceMuted
}

@StringRes
fun BuiltInCategory.nameRes(): Int = when (this) {
    BuiltInCategory.GROCERIES -> R.string.category_groceries
    BuiltInCategory.RESTAURANTS -> R.string.category_restaurants
    BuiltInCategory.TRANSPORT -> R.string.category_transport
    BuiltInCategory.BILLS -> R.string.category_bills
    BuiltInCategory.HEALTH -> R.string.category_health
    BuiltInCategory.SHOPPING -> R.string.category_shopping
    BuiltInCategory.ENTERTAINMENT -> R.string.category_entertainment
    BuiltInCategory.HOME -> R.string.category_home
    BuiltInCategory.OTHER -> R.string.category_other
}

/** Custom name if set, otherwise the translated built-in name. */
@Composable
fun Category.displayName(): String =
    name ?: builtIn?.let { stringResource(it.nameRes()) } ?: stringResource(R.string.category_other)

/** Colored circle with the category icon (the icon is the non-color signal). */
@Composable
fun CategoryBadge(category: Category, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    CategoryBadge(category.iconKey, category.colorKey, modifier, size)
}

@Composable
fun CategoryBadge(iconKey: String, colorKey: String, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(CategoryColors.color(colorKey), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CategoryIcons.drawable(iconKey)),
            contentDescription = null,
            tint = Color(0xFF15161C),
            modifier = Modifier.size(size * 0.58f),
        )
    }
}

/** Palette circles; the selected one gets a check mark and a ring, not only a color change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CategoryColors.options.forEach { option ->
            val selected = option.key == selectedKey
            val label = stringResource(option.label)
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .selectable(selected = selected, onClick = { onSelect(option.key) }, role = Role.RadioButton)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(option.color, CircleShape)
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color(0xFF15161C))
                }
            }
        }
    }
}

/** Icon grid; the selected one is filled, not only recolored, and each icon has a spoken label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selectedKey: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CategoryIcons.options.forEach { option ->
            val selected = option.key == selectedKey
            val label = stringResource(option.label)
            Box(
                Modifier
                    .size(MinTouchTarget)
                    .then(
                        if (selected) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        else Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                    )
                    .selectable(selected = selected, onClick = { onSelect(option.key) }, role = Role.RadioButton)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(option.drawable),
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
