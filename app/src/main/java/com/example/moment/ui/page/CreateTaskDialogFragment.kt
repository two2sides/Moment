package com.example.moment.ui.page

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import com.example.moment.data.entity.TASK_TYPE_MANUAL_SUBMIT

class CreateTaskDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_task, null)

        val etTitle = view.findViewById<EditText>(R.id.etTitle)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val rgTaskType = view.findViewById<RadioGroup>(R.id.rgTaskType)
        val rbManualTask = view.findViewById<RadioButton>(R.id.rbManualTask)
        val rbAppTask = view.findViewById<RadioButton>(R.id.rbAppTask)
        val etTargetMinutes = view.findViewById<EditText>(R.id.etTargetMinutes)
        val etRewardMinutes = view.findViewById<EditText>(R.id.etRewardMinutes)
        val etTargetPackage = view.findViewById<EditText>(R.id.etTargetPackage)
        val switchRepeatable = view.findViewById<Switch>(R.id.switchRepeatable)

        fun updateTypeUi() {
            val isAppTask = rbAppTask.isChecked
            etTargetMinutes.visibility = if (isAppTask) android.view.View.VISIBLE else android.view.View.GONE
            etTargetPackage.visibility = if (isAppTask) android.view.View.VISIBLE else android.view.View.GONE
            switchRepeatable.visibility = android.view.View.VISIBLE
        }

        rgTaskType.setOnCheckedChangeListener { _, _ -> updateTypeUi() }
        rbManualTask.isChecked = true
        updateTypeUi()

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_task)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = etTitle.text.toString().trim()
                        val rewardMinutes = etRewardMinutes.text.toString().trim().toIntOrNull() ?: 0
                        val note = etNote.text.toString().trim().ifBlank { null }
                        val isAppTask = rbAppTask.isChecked
                        val taskType = if (isAppTask) TASK_TYPE_APP_USAGE else TASK_TYPE_MANUAL_SUBMIT
                        val targetMinutes = if (isAppTask) {
                            etTargetMinutes.text.toString().trim().toIntOrNull() ?: 0
                        } else {
                            0
                        }
                        val targetPackage = if (isAppTask) {
                            etTargetPackage.text.toString().trim().ifBlank { null }
                        } else {
                            null
                        }

                        if (title.isBlank()) {
                            Toast.makeText(requireContext(), R.string.enter_task_title, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (rewardMinutes <= 0) {
                            Toast.makeText(requireContext(), R.string.reward_minutes_positive, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (isAppTask && targetMinutes <= 0) {
                            Toast.makeText(requireContext(), R.string.target_minutes_positive, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (isAppTask && targetPackage.isNullOrBlank()) {
                            Toast.makeText(requireContext(), R.string.target_package_required, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        parentFragmentManager.setFragmentResult(
                            REQUEST_KEY,
                            bundleOf(
                                KEY_TITLE to title,
                                KEY_NOTE to note,
                                KEY_TASK_TYPE to taskType,
                                KEY_TARGET_MINUTES to targetMinutes,
                                KEY_REWARD_MINUTES to rewardMinutes,
                                KEY_TARGET_PACKAGE to targetPackage,
                                KEY_REPEATABLE to switchRepeatable.isChecked
                            )
                        )
                        dismiss()
                    }
                }
            }
    }

    companion object {
        const val REQUEST_KEY = "create_task_request"
        const val KEY_TITLE = "title"
        const val KEY_NOTE = "note"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_TARGET_MINUTES = "target_minutes"
        const val KEY_REWARD_MINUTES = "reward_minutes"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_REPEATABLE = "repeatable"
    }
}

