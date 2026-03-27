package com.example.moment.ui.page

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.moment.R
import com.example.moment.data.TimeManager
import kotlin.math.ceil

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var etDailyBaseMinutes: EditText
    private lateinit var etBlockedApps: EditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etDailyBaseMinutes = view.findViewById(R.id.etDailyBaseMinutes)
        etBlockedApps = view.findViewById(R.id.etBlockedApps)
        val btnSave = view.findViewById<Button>(R.id.btnSaveSettings)
        val btnAccessibility = view.findViewById<Button>(R.id.btnAccessibility)

        loadCurrentSettings()

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun loadCurrentSettings() {
        val context = requireContext()
        etDailyBaseMinutes.setText(TimeManager.getDailyBaseMinutes(context).toString())
        etBlockedApps.setText(TimeManager.getBlockedApps(context).joinToString("\n"))
    }

    private fun saveSettings() {
        val context = requireContext()
        val baseMinutes = etDailyBaseMinutes.text.toString().trim().toIntOrNull()
        if (baseMinutes == null || baseMinutes < 0) {
            Toast.makeText(context, R.string.daily_base_minutes_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        val apps = etBlockedApps.text.toString()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        TimeManager.setBlockedApps(context, apps)

        val baseUpdateResult = TimeManager.updateDailyBaseMinutesWithCooldown(context, baseMinutes)
        if (baseUpdateResult.success) {
            Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                getString(
                    R.string.daily_base_minutes_cooldown,
                    formatRemainingCooldown(baseUpdateResult.remainingMillis)
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun formatRemainingCooldown(remainingMillis: Long): String {
        val totalMinutes = ceil(remainingMillis / 60000.0).toInt().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) {
            getString(R.string.cooldown_hours_minutes_format, hours, minutes)
        } else {
            getString(R.string.cooldown_minutes_format, minutes)
        }
    }

}

