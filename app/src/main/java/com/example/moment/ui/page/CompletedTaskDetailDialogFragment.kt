package com.example.moment.ui.page

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R
import com.example.moment.data.entity.CompletedTask
import com.example.moment.data.entity.TASK_TYPE_APP_USAGE
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin

class CompletedTaskDetailDialogFragment : DialogFragment() {
    private val markwon by lazy {
        Markwon.builder(requireContext())
            .usePlugin(ImagesPlugin.create())
            .usePlugin(CoilImagesPlugin.create(requireContext()))
            .build()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val recordId = args.getInt(ARG_RECORD_ID)
        val title = args.getString(ARG_TITLE).orEmpty()
        val note = args.getString(ARG_NOTE)
        val type = args.getString(ARG_TASK_TYPE).orEmpty()
        val submittedMarkdown = args.getString(ARG_SUBMITTED_MARKDOWN)
        val submittedText = args.getString(ARG_SUBMITTED_TEXT)
        val submittedImageUri = args.getString(ARG_SUBMITTED_IMAGE_URI)
        val rewardMinutes = args.getInt(ARG_REWARD_MINUTES)
        val createdAt = args.getLong(ARG_CREATED_AT)
        val completedAt = args.getLong(ARG_COMPLETED_AT)

        val isAppTask = type == TASK_TYPE_APP_USAGE

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_completed_task_detail, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvCompletedDetailTitle)
        val tvType = view.findViewById<TextView>(R.id.tvCompletedDetailType)
        val tvReward = view.findViewById<TextView>(R.id.tvCompletedDetailReward)
        val tvCreatedAt = view.findViewById<TextView>(R.id.tvCompletedDetailCreatedAt)
        val tvCompletedAt = view.findViewById<TextView>(R.id.tvCompletedDetailCompletedAt)
        val tvNote = view.findViewById<TextView>(R.id.tvCompletedDetailNote)
        val tvSubmittedText = view.findViewById<TextView>(R.id.tvCompletedDetailSubmittedText)
        val ivSubmittedImage = view.findViewById<ImageView>(R.id.ivCompletedDetailImage)

        tvTitle.text = title
        tvType.text = getString(
            R.string.task_type_format,
            if (isAppTask) getString(R.string.task_type_app_usage) else getString(R.string.task_type_manual_submit)
        )
        tvReward.text = getString(R.string.reward_minutes_label_format, rewardMinutes)
        tvCreatedAt.text = getString(R.string.created_time_format, UiTimeFormatters.formatDateTime(createdAt))
        tvCompletedAt.text = getString(R.string.completed_time_format, UiTimeFormatters.formatDateTime(completedAt))

        if (note.isNullOrBlank()) {
            tvNote.visibility = View.GONE
        } else {
            tvNote.visibility = View.VISIBLE
            tvNote.text = getString(R.string.task_note_full_format, note)
        }

        val displayText = if (submittedMarkdown.isNullOrBlank()) submittedText else submittedMarkdown
        if (isAppTask || displayText.isNullOrBlank()) {
            tvSubmittedText.visibility = View.GONE
        } else {
            tvSubmittedText.visibility = View.VISIBLE
            if (!submittedMarkdown.isNullOrBlank()) {
                markwon.setMarkdown(tvSubmittedText, submittedMarkdown)
            } else {
                tvSubmittedText.text = getString(R.string.submitted_text_full_format, displayText)
            }
        }

        val imageUriForPreview = MarkdownContentHelper.extractFirstImageUri(
            markdown = submittedMarkdown,
            fallbackImageUri = submittedImageUri
        )
        val shouldShowBottomImage = !isAppTask && submittedMarkdown.isNullOrBlank() && !imageUriForPreview.isNullOrBlank()
        if (!shouldShowBottomImage) {
            ivSubmittedImage.visibility = View.GONE
            ivSubmittedImage.setImageDrawable(null)
            ivSubmittedImage.setOnClickListener(null)
        } else {
            ivSubmittedImage.visibility = View.VISIBLE
            ivSubmittedImage.setImageURI(Uri.parse(imageUriForPreview))
            ivSubmittedImage.setOnClickListener {
                ImagePreviewDialogFragment.newInstance(imageUriForPreview)
                    .show(parentFragmentManager, "ImagePreviewDialog")
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.completed_task_detail_dialog_title)
            .setView(view)
            .setNegativeButton(R.string.delete) { _, _ ->
                parentFragmentManager.setFragmentResult(
                    DELETE_REQUEST_KEY,
                    bundleOf(
                        KEY_RECORD_ID to recordId,
                        KEY_IMAGE_URI to imageUriForPreview,
                        KEY_SUBMITTED_MARKDOWN to submittedMarkdown
                    )
                )
            }
            .setPositiveButton(R.string.close, null)
            .create()
    }

    companion object {
        const val DELETE_REQUEST_KEY = "completed_task_detail_delete_request"
        const val KEY_RECORD_ID = "record_id"
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_SUBMITTED_MARKDOWN = "submitted_markdown"

        private const val ARG_RECORD_ID = "arg_record_id"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_NOTE = "arg_note"
        private const val ARG_TASK_TYPE = "arg_task_type"
        private const val ARG_SUBMITTED_MARKDOWN = "arg_submitted_markdown"
        private const val ARG_SUBMITTED_TEXT = "arg_submitted_text"
        private const val ARG_SUBMITTED_IMAGE_URI = "arg_submitted_image_uri"
        private const val ARG_REWARD_MINUTES = "arg_reward_minutes"
        private const val ARG_CREATED_AT = "arg_created_at"
        private const val ARG_COMPLETED_AT = "arg_completed_at"

        fun newInstance(record: CompletedTask): CompletedTaskDetailDialogFragment {
            return CompletedTaskDetailDialogFragment().apply {
                arguments = bundleOf(
                    ARG_RECORD_ID to record.id,
                    ARG_TITLE to record.taskTitleSnapshot,
                    ARG_NOTE to record.taskNoteSnapshot,
                    ARG_TASK_TYPE to record.taskTypeSnapshot,
                    ARG_SUBMITTED_MARKDOWN to record.submittedMarkdown,
                    ARG_SUBMITTED_TEXT to record.submittedText,
                    ARG_SUBMITTED_IMAGE_URI to record.submittedImageUri,
                    ARG_REWARD_MINUTES to record.rewardGrantedMinutes,
                    ARG_CREATED_AT to record.taskCreatedAtSnapshot,
                    ARG_COMPLETED_AT to record.completedAt
                )
            }
        }
    }
}

