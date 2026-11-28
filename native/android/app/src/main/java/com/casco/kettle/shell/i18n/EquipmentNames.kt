package com.casco.kettle.shell.i18n

import androidx.annotation.StringRes
import com.casco.kettle.shell.R

/**
 * Known item [key]'s name (#393), `equipment.<key>` in the bundles, or null for a key the vocabulary
 * doesn't have. Spelled out rather than looked up by name, which lint refuses; a key added to
 * `content/equipment.json` without a line here fails `EquipmentVocabularyTest`. The key is the
 * vocabulary's, never the user's spelling: match it with `knownEquipment` first, and show anything
 * that isn't known exactly as written.
 */
@StringRes
fun equipmentName(key: String): Int? = EQUIPMENT_NAMES[key]

private val EQUIPMENT_NAMES: Map<String, Int> =
    mapOf(
        "barbell" to R.string.equipment_barbell,
        "dumbbell" to R.string.equipment_dumbbell,
        "kettlebell" to R.string.equipment_kettlebell,
        "plates" to R.string.equipment_plates,
        "ez_bar" to R.string.equipment_ez_bar,
        "trap_bar" to R.string.equipment_trap_bar,
        "bench" to R.string.equipment_bench,
        "rack" to R.string.equipment_rack,
        "smith_machine" to R.string.equipment_smith_machine,
        "cable_machine" to R.string.equipment_cable_machine,
        "leg_press" to R.string.equipment_leg_press,
        "lat_pulldown" to R.string.equipment_lat_pulldown,
        "landmine" to R.string.equipment_landmine,
        "dip_belt" to R.string.equipment_dip_belt,
        "weight_vest" to R.string.equipment_weight_vest,
        "sandbag" to R.string.equipment_sandbag,
        "ankle_weights" to R.string.equipment_ankle_weights,
        "pull_up_bar" to R.string.equipment_pull_up_bar,
        "dip_bars" to R.string.equipment_dip_bars,
        "rings" to R.string.equipment_rings,
        "parallettes" to R.string.equipment_parallettes,
        "suspension_trainer" to R.string.equipment_suspension_trainer,
        "resistance_band" to R.string.equipment_resistance_band,
        "mini_band" to R.string.equipment_mini_band,
        "ab_wheel" to R.string.equipment_ab_wheel,
        "climbing_rope" to R.string.equipment_climbing_rope,
        "mat" to R.string.equipment_mat,
        "reformer" to R.string.equipment_reformer,
        "pilates_ring" to R.string.equipment_pilates_ring,
        "stability_ball" to R.string.equipment_stability_ball,
        "foam_roller" to R.string.equipment_foam_roller,
        "yoga_block" to R.string.equipment_yoga_block,
        "strap" to R.string.equipment_strap,
        "balance_board" to R.string.equipment_balance_board,
        "sliders" to R.string.equipment_sliders,
        "chair" to R.string.equipment_chair,
        "wall" to R.string.equipment_wall,
        "step" to R.string.equipment_step,
        "box" to R.string.equipment_box,
        "towel" to R.string.equipment_towel,
        "table" to R.string.equipment_table,
        "jump_rope" to R.string.equipment_jump_rope,
        "medicine_ball" to R.string.equipment_medicine_ball,
        "slam_ball" to R.string.equipment_slam_ball,
        "wall_ball" to R.string.equipment_wall_ball,
        "battle_ropes" to R.string.equipment_battle_ropes,
        "sled" to R.string.equipment_sled,
        "tire" to R.string.equipment_tire,
        "treadmill" to R.string.equipment_treadmill,
        "bike" to R.string.equipment_bike,
        "rower" to R.string.equipment_rower,
        "ski_erg" to R.string.equipment_ski_erg,
        "elliptical" to R.string.equipment_elliptical,
        "stair_climber" to R.string.equipment_stair_climber,
        "ball" to R.string.equipment_ball,
        "cones" to R.string.equipment_cones,
        "agility_ladder" to R.string.equipment_agility_ladder,
        "hurdles" to R.string.equipment_hurdles,
        "racket" to R.string.equipment_racket,
        "goal" to R.string.equipment_goal,
    )
