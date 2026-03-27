package com.example.moment.ui.page

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import com.example.moment.data.entity.Task

class EditTaskDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val taskId = args.getInt(ARG_TASK_ID)
        val taskType = args.getString(ARG_TASK_TYPE).orEmpty()
        val rewardMinutes = args.getInt(ARG_REWARD_MINUTES)
        val targetSeconds = args.getInt(ARG_TARGET_SECONDS)
        val note = args.getString(ARG_NOTE)

        val isAppTask = taskType == TASK_TYPE_APP_USAGE
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_task, null)
        val etNote = view.findViewById<EditText>(R.id.etEditTaskNote)
        val etRewardMinutes = view.findViewById<EditText>(R.id.etEditTaskRewardMinutes)
        val etTargetMinutes = view.findViewById<EditText>(R.id.etEditTaskTargetMinutes)

        etNote.setText(note.orEmpty())
        etRewardMinutes.setText(rewardMinutes.toString())
        if (isAppTask) {
            etTargetMinutes.visibility = View.VISIBLE
            etTargetMinutes.setText((targetSeconds / 60).toString())
        } else {
            etTargetMinutes.visibility = View.GONE
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.edit_task)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reward = etRewardMinutes.text.toString().trim().toIntOrNull() ?: 0
                        val targetMinutes = if (isAppTask) {
                            etTargetMinutes.text.toString().trim().toIntOrNull() ?: 0
                        } else {
                            0
                        }

                        if (reward <= 0) {
                            Toast.makeText(requireContext(), R.string.reward_minutes_positive, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        if (isAppTask && targetMinutes <= 0) {
                            Toast.makeText(requireContext(), R.string.target_minutes_positive, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        parentFragmentManager.setFragmentResult(
                            REQUEST_KEY,
                            bundleOf(
                                KEY_TASK_ID to taskId,
                                KEY_NOTE to etNote.text.toString().trim().ifBlank { null },
                                KEY_REWARD_MINUTES to reward,
                                KEY_TARGET_MINUTES to targetMinutes
                            )
                        )
                        dismiss()
                    }
                }
            }
    }

    companion object {
        const val REQUEST_KEY = "edit_task_request"
        const val KEY_TASK_ID = "task_id"
        const val KEY_NOTE = "note"
        const val KEY_REWARD_MINUTES = "reward_minutes"
        const val KEY_TARGET_MINUTES = "target_minutes"

        private const val ARG_TASK_ID = "arg_task_id"
        private const val ARG_TASK_TYPE = "arg_task_type"
        private const val ARG_NOTE = "arg_note"
        private const val ARG_REWARD_MINUTES = "arg_reward_minutes"
        private const val ARG_TARGET_SECONDS = "arg_target_seconds"

        fun newInstance(task: Task): EditTaskDialogFragment {
            return EditTaskDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TASK_ID to task.id,
                    ARG_TASK_TYPE to task.taskType,
                    ARG_NOTE to task.note,
                    ARG_REWARD_MINUTES to task.rewardMinutes,
                    ARG_TARGET_SECONDS to task.targetSeconds
                )
            }
        }
    }
}

