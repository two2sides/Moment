package com.example.moment.ui.page

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import com.example.moment.data.entity.Task

class TaskDetailDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val taskId = args.getInt(ARG_TASK_ID)
        val title = args.getString(ARG_TITLE).orEmpty()
        val note = args.getString(ARG_NOTE)
        val type = args.getString(ARG_TASK_TYPE).orEmpty()
        val createdAt = args.getLong(ARG_CREATED_AT)
        val rewardMinutes = args.getInt(ARG_REWARD_MINUTES)
        val targetSeconds = args.getInt(ARG_TARGET_SECONDS)
        val progressSeconds = args.getInt(ARG_PROGRESS_SECONDS)
        val targetPackage = args.getString(ARG_TARGET_PACKAGE)

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_task_detail, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvTaskDetailTitle)
        val tvType = view.findViewById<TextView>(R.id.tvTaskDetailType)
        val tvReward = view.findViewById<TextView>(R.id.tvTaskDetailReward)
        val tvCreatedAt = view.findViewById<TextView>(R.id.tvTaskDetailCreatedAt)
        val tvNote = view.findViewById<TextView>(R.id.tvTaskDetailNote)
        val tvExtra = view.findViewById<TextView>(R.id.tvTaskDetailExtra)

        val isAppTask = type == TASK_TYPE_APP_USAGE

        tvTitle.text = title
        tvType.text = getString(
            R.string.task_type_format,
            if (isAppTask) getString(R.string.task_type_app_usage) else getString(R.string.task_type_manual_submit)
        )
        tvReward.text = getString(R.string.reward_minutes_label_format, rewardMinutes)
        tvCreatedAt.text = getString(R.string.created_time_format, UiTimeFormatters.formatDateTime(createdAt))

        if (note.isNullOrBlank()) {
            tvNote.visibility = View.GONE
        } else {
            tvNote.visibility = View.VISIBLE
            tvNote.text = getString(R.string.task_note_full_format, note)
        }

        tvExtra.text = if (isAppTask) {
            getString(
                R.string.task_detail_usage_format,
                UiTimeFormatters.formatDurationMmSs(progressSeconds),
                UiTimeFormatters.formatDurationMmSs(targetSeconds),
                targetPackage ?: "-"
            )
        } else {
            getString(R.string.task_submit_optional_materials)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_detail_dialog_title)
            .setView(view)
            .setNegativeButton(R.string.delete) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    DELETE_REQUEST_KEY,
                    bundleOf(KEY_TASK_ID to taskId)
                )
            }
            .setNeutralButton(R.string.edit_task) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    EDIT_REQUEST_KEY,
                    bundleOf(KEY_TASK_ID to taskId)
                )
            }
            .setPositiveButton(R.string.close, null)
            .create()
    }

    companion object {
        const val DELETE_REQUEST_KEY = "task_detail_delete_request"
        const val EDIT_REQUEST_KEY = "task_detail_edit_request"
        const val KEY_TASK_ID = "task_id"

        private const val ARG_TASK_ID = "arg_task_id"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_NOTE = "arg_note"
        private const val ARG_TASK_TYPE = "arg_task_type"
        private const val ARG_CREATED_AT = "arg_created_at"
        private const val ARG_REWARD_MINUTES = "arg_reward_minutes"
        private const val ARG_TARGET_SECONDS = "arg_target_seconds"
        private const val ARG_PROGRESS_SECONDS = "arg_progress_seconds"
        private const val ARG_TARGET_PACKAGE = "arg_target_package"

        fun newInstance(task: Task): TaskDetailDialogFragment {
            return TaskDetailDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TASK_ID to task.id,
                    ARG_TITLE to task.title,
                    ARG_NOTE to task.note,
                    ARG_TASK_TYPE to task.taskType,
                    ARG_CREATED_AT to task.createdAt,
                    ARG_REWARD_MINUTES to task.rewardMinutes,
                    ARG_TARGET_SECONDS to task.targetSeconds,
                    ARG_PROGRESS_SECONDS to task.currentProgressSeconds,
                    ARG_TARGET_PACKAGE to task.targetPackage
                )
            }
        }
    }
}

