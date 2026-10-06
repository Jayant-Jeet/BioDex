package com.biodex.data

import android.content.Context
import org.json.JSONObject
import java.util.Calendar

const val TOTAL_PLANTS = 4271
const val XP_PER_LEVEL = 1000

enum class Rarity(val label: String, val letter: String, val xp: Int) {
    Common("Common", "C", 80),
    Uncommon("Uncommon", "U", 130),
    Rare("Rare", "R", 170),
    ;

    companion object {
        /** Starter species keep their curated rarity; BioCLIP taxa get a stable pseudo-random bucket. */
        fun of(species: Species): Rarity {
            entries.firstOrNull { it.label == species.rarity }?.let { return it }
            val bucket = Math.floorMod(species.scientificName.hashCode(), 100)
            return when {
                bucket < 62 -> Common
                bucket < 88 -> Uncommon
                else -> Rare
            }
        }
    }
}

object LevelNames {
    private val names = listOf(
        "Seedling", "Sprout Seeker", "Trail Scout", "Canopy Seeker",
        "Grove Keeper", "Forest Warden", "Moss Sage", "Master Botanist",
    )

    fun of(level: Int) = names[(level - 1).coerceIn(0, names.lastIndex)]
}

data class Mission(
    val id: Int,
    val title: String,
    val subtitle: String,
    val keywords: Regex,
    val target: Int,
    val rewardXp: Int,
    val badge: String,
)

object Missions {
    private val all = listOf(
        Mission(0, "Find something flowering", "Capture 1 flowering plant before sunset", Regex("flower|bloom|blossom|daisy|rose|lily|orchid|tulip|aster|clover", RegexOption.IGNORE_CASE), 1, 150, "Mystery badge"),
        Mission(1, "Spot a fern or moss", "Capture 1 fern or moss on the trail", Regex("fern|moss|bryum|spore", RegexOption.IGNORE_CASE), 1, 140, "Spore badge"),
        Mission(2, "Meet a tree", "Capture 1 tree or woody plant", Regex("tree|oak|pine|maple|birch|elm|willow|cedar|fir|ash|beech", RegexOption.IGNORE_CASE), 1, 160, "Canopy badge"),
        Mission(3, "Field day double", "Capture any 2 plants today", Regex(".*"), 2, 200, "Explorer badge"),
        Mission(4, "Rare hunter", "Capture 1 uncommon or rare plant", Regex(".*"), 1, 180, "Hunter badge"),
    )

    fun forDay(day: Long) = all[Math.floorMod(day, all.size.toLong()).toInt()]

    fun matches(mission: Mission, species: Species): Boolean = when (mission.id) {
        3 -> true
        4 -> Rarity.of(species) != Rarity.Common
        else -> mission.keywords.containsMatchIn("${species.name} ${species.scientificName} ${species.detail.orEmpty()}")
    }
}

data class Progress(
    val xp: Int = 0,
    val streak: Int = 0,
    val lastDay: Long = -1,
    val missionDay: Long = -1,
    val missionProgress: Int = 0,
    val missionDone: Boolean = false,
    val badges: List<String> = emptyList(),
) {
    val level get() = xp / XP_PER_LEVEL + 1
    val levelXp get() = xp % XP_PER_LEVEL
    val levelName get() = LevelNames.of(level)
    val nextLevelName get() = LevelNames.of(level + 1)
    val xpToNext get() = XP_PER_LEVEL - levelXp

    /** A streak only counts while the last find was today or yesterday. */
    fun activeStreak(today: Long) = if (lastDay >= today - 1) streak else 0

    fun missionFor(today: Long): Pair<Mission, Pair<Int, Boolean>> {
        val mission = Missions.forDay(today)
        return if (missionDay == today) mission to (missionProgress to missionDone) else mission to (0 to false)
    }
}

data class Award(val xp: Int, val bonusXp: Int, val missionTitle: String?, val levelUp: Boolean)

class ProgressStore(context: Context) {
    private val preferences = context.getSharedPreferences("biodex_progress", Context.MODE_PRIVATE)

    fun load(): Progress {
        val json = JSONObject(preferences.getString(KEY, "{}") ?: "{}")
        val badges = json.optJSONArray("badges")
        return Progress(
            xp = json.optInt("xp"),
            streak = json.optInt("streak"),
            lastDay = json.optLong("lastDay", -1),
            missionDay = json.optLong("missionDay", -1),
            missionProgress = json.optInt("missionProgress"),
            missionDone = json.optBoolean("missionDone"),
            badges = List(badges?.length() ?: 0) { badges!!.getString(it) },
        )
    }

    fun record(species: Species, isNewSpecies: Boolean, now: Long = System.currentTimeMillis()): Pair<Progress, Award> {
        val before = load()
        val today = dayOf(now)
        val xp = discoveryXp(species, isNewSpecies)
        val streak = when {
            before.lastDay == today -> before.streak.coerceAtLeast(1)
            before.lastDay == today - 1 -> before.streak + 1
            else -> 1
        }
        val mission = Missions.forDay(today)
        var progress = if (before.missionDay == today) before.missionProgress else 0
        var done = before.missionDay == today && before.missionDone
        var bonus = 0
        var missionTitle: String? = null
        var badges = before.badges
        if (!done && Missions.matches(mission, species)) {
            progress += 1
            if (progress >= mission.target) {
                done = true
                bonus = mission.rewardXp
                missionTitle = mission.title
                badges = badges + mission.badge
            }
        }
        val updated = before.copy(
            xp = before.xp + xp + bonus,
            streak = streak,
            lastDay = today,
            missionDay = today,
            missionProgress = progress,
            missionDone = done,
            badges = badges,
        )
        val badgeArray = org.json.JSONArray().also { array -> updated.badges.forEach { array.put(it) } }
        val json = JSONObject()
            .put("xp", updated.xp).put("streak", updated.streak).put("lastDay", updated.lastDay)
            .put("missionDay", updated.missionDay).put("missionProgress", updated.missionProgress)
            .put("missionDone", updated.missionDone).put("badges", badgeArray)
        check(preferences.edit().putString(KEY, json.toString()).commit()) { "Could not save progress." }
        return updated to Award(xp, bonus, missionTitle, updated.level > before.level)
    }

    companion object {
        private const val KEY = "progress"
        const val DUPLICATE_XP = 25

        fun discoveryXp(species: Species, isNewSpecies: Boolean) =
            if (isNewSpecies) Rarity.of(species).xp else DUPLICATE_XP

        fun dayOf(millis: Long): Long {
            val calendar = Calendar.getInstance().apply { timeInMillis = millis }
            val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
            return Math.floorDiv(millis + offset, 86_400_000L)
        }
    }
}
