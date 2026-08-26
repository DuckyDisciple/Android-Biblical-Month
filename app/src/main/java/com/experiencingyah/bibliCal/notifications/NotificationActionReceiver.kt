package com.experiencingyah.bibliCal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.experiencingyah.bibliCal.data.LunarRepository
import com.experiencingyah.bibliCal.data.settings.SettingsRepository
import com.experiencingyah.bibliCal.widgets.DateWidgetProvider
import com.experiencingyah.bibliCal.widgets.ShabbatWidgetProvider
import com.experiencingyah.bibliCal.widgets.CombinedWidgetProvider
import com.experiencingyah.bibliCal.work.StatusUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val repo = LunarRepository(context)
        val settings = SettingsRepository(context)

        when (intent.action) {
            ACTION_MOON_SEEN, ACTION_MOON_NOT_SEEN, ACTION_MOON_LATER -> {
                val lunar = StatusUpdater.resolveCurrentBiblicalLunarDate(repo, settings)
                val dayFromNotif = intent.getIntExtra(EXTRA_DAY_OF_MONTH, lunar?.dayOfMonth ?: 29)
                val monthStartEpoch = lunar?.monthStart?.toEpochDay()

                when (intent.action) {
                    ACTION_MOON_SEEN -> {
                        val startDate = StatusUpdater.gregorianStartForNextMonthAfterMoonPrompt(
                            moonSeen = true,
                            dayOfMonth = dayFromNotif,
                            settings = settings,
                        )
                        repo.startNextMonthOn(startDate)
                        if (monthStartEpoch != null) {
                            settings.setMoonPromptAck(monthStartEpoch, dayFromNotif.coerceIn(29, 30))
                        }
                    }
                    ACTION_MOON_NOT_SEEN -> {
                        if (dayFromNotif == 30) {
                            val startDate = StatusUpdater.gregorianStartForNextMonthAfterMoonPrompt(
                                moonSeen = false,
                                dayOfMonth = 30,
                                settings = settings,
                            )
                            repo.startNextMonthOn(startDate)
                            if (monthStartEpoch != null) {
                                settings.setMoonPromptAck(monthStartEpoch, 30)
                            }
                        } else {
                            if (lunar != null && lunar.dayOfMonth == 29) {
                                settings.setProjectedMonthLength(lunar.yearNumber, lunar.monthNumber, 30)
                            }
                            if (monthStartEpoch != null) {
                                settings.setMoonPromptAck(monthStartEpoch, 29)
                            }
                        }
                    }
                    ACTION_MOON_LATER -> Unit
                }
                Notifier.cancelMoonPrompt(context)
            }

            ACTION_BARLEY_AVIV -> {
                val year = intent.getIntExtra(EXTRA_YEAR_NUMBER, -1)
                if (year != -1) repo.setBarleyAvivDecision(year, aviv = true, decidedOn = LocalDate.now())
                Notifier.cancelBarleyPrompt(context)
            }
            ACTION_BARLEY_NOT_AVIV -> {
                val year = intent.getIntExtra(EXTRA_YEAR_NUMBER, -1)
                if (year != -1) repo.setBarleyAvivDecision(year, aviv = false, decidedOn = LocalDate.now())
                Notifier.cancelBarleyPrompt(context)
            }
            ACTION_BARLEY_LATER -> Notifier.cancelBarleyPrompt(context)
        }

        // Refresh surfaces quickly.
        val today = repo.getToday()
        if (today != null) {
            val namingMode = settings.monthNamingMode.first()
            val label = com.experiencingyah.bibliCal.util.MonthNames.format(today.monthNumber, namingMode) +
                " ${today.dayOfMonth}, Year ${today.yearNumber}"
            Notifier.ensureChannels(context)
            if (settings.statusNotificationEnabled.first()) {
                Notifier.showOrUpdateStatus(context, today, label)
            } else {
                Notifier.cancelStatus(context)
            }
            DateWidgetProvider.updateAll(context)
            ShabbatWidgetProvider.updateAll(context)
            CombinedWidgetProvider.updateAll(context)
        }
    }

    companion object {
        const val EXTRA_YEAR_NUMBER = "extra_year_number"
        const val EXTRA_DAY_OF_MONTH = "extra_day_of_month"

        const val ACTION_MOON_SEEN = "com.experiencingyah.bibliCal.action.MOON_SEEN"
        const val ACTION_MOON_NOT_SEEN = "com.experiencingyah.bibliCal.action.MOON_NOT_SEEN"
        const val ACTION_MOON_LATER = "com.experiencingyah.bibliCal.action.MOON_LATER"

        const val ACTION_BARLEY_AVIV = "com.experiencingyah.bibliCal.action.BARLEY_AVIV"
        const val ACTION_BARLEY_NOT_AVIV = "com.experiencingyah.bibliCal.action.BARLEY_NOT_AVIV"
        const val ACTION_BARLEY_LATER = "com.experiencingyah.bibliCal.action.BARLEY_LATER"
    }
}
