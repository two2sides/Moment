package com.example.moment.ui.page

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.moment.R
import com.example.moment.data.TimeManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var tvBalance: TextView
    private lateinit var cgManagedApps: ChipGroup
    private lateinit var tvManagedAppsEmpty: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvBalance = view.findViewById(R.id.tvBalance)
        cgManagedApps = view.findViewById(R.id.cgManagedApps)
        tvManagedAppsEmpty = view.findViewById(R.id.tvManagedAppsEmpty)
    }

    override fun onResume() {
        super.onResume()
        refreshMainInfo()
    }

    private fun refreshMainInfo() {
        val context = requireContext()
        TimeManager.checkAndPerformDailyReset(context)

        val balance = TimeManager.getBalanceSeconds(context)
        val minutes = balance / 60
        val seconds = balance % 60
        tvBalance.text = getString(R.string.home_balance_format, minutes, seconds)

        val blockedApps = TimeManager.getBlockedApps(context)
        if (blockedApps.isEmpty()) {
            cgManagedApps.removeAllViews()
            tvManagedAppsEmpty.visibility = View.VISIBLE
        } else {
            tvManagedAppsEmpty.visibility = View.GONE
            cgManagedApps.removeAllViews()
            blockedApps.sorted().forEach { packageName ->
                cgManagedApps.addView(createPackageChip(packageName))
            }
        }

    }

    private fun createPackageChip(packageName: String): Chip {
        return Chip(requireContext()).apply {
            text = packageName
            isClickable = false
            isCheckable = false
            setChipBackgroundColorResource(R.color.sky_200)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            chipStrokeWidth = 1f
            setChipStrokeColorResource(R.color.card_stroke)
        }
    }
}

