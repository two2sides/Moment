package com.example.moment.ui.page

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R
import java.io.File
import java.io.FileOutputStream

class SubmitTaskDialogFragment : DialogFragment() {
    private var markdownEditor: EditText? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if provider does not support persistable permissions.
            }

            val localUri = copyImageToPrivateStorage(uri)
            if (!localUri.isNullOrBlank()) {
                insertMarkdownImage(localUri)
            }
        }
        dialog?.findViewById<TextView>(R.id.tvImagePicked)?.apply {
            visibility = if (uri == null) View.GONE else View.VISIBLE
            text = if (uri == null) "" else getString(R.string.markdown_image_inserted)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val taskId = args.getInt(ARG_TASK_ID)
        val taskTitle = args.getString(ARG_TASK_TITLE).orEmpty()

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_submit_task, null)
        val tvSubmitTaskTitle = view.findViewById<TextView>(R.id.tvSubmitTaskTitle)
        val etSubmitText = view.findViewById<EditText>(R.id.etSubmitText)
        val btnPickImage = view.findViewById<Button>(R.id.btnPickImage)
        val tvImagePicked = view.findViewById<TextView>(R.id.tvImagePicked)
        markdownEditor = etSubmitText

        tvSubmitTaskTitle.text = getString(R.string.submit_task_target, taskTitle)
        etSubmitText.visibility = View.VISIBLE
        btnPickImage.visibility = View.VISIBLE
        tvImagePicked.visibility = View.GONE

        btnPickImage.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.submit_task_dialog_title)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.submit, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val markdown = etSubmitText.text.toString().trim().ifBlank { null }
                        val submittedText = MarkdownContentHelper.stripMarkdownToPlainText(markdown)
                        val imageUriText = MarkdownContentHelper.extractFirstImageUri(markdown)

                        if (markdown.isNullOrBlank()) {
                            Toast.makeText(requireContext(), R.string.submit_optional_material_hint, Toast.LENGTH_SHORT).show()
                        }

                        parentFragmentManager.setFragmentResult(
                            REQUEST_KEY,
                            bundleOf(
                                KEY_TASK_ID to taskId,
                                KEY_SUBMITTED_MARKDOWN to markdown,
                                KEY_SUBMITTED_TEXT to submittedText,
                                KEY_SUBMITTED_IMAGE_URI to imageUriText
                            )
                        )
                        dismiss()
                    }
                }
            }
    }

    companion object {
        const val REQUEST_KEY = "submit_task_request"
        const val KEY_TASK_ID = "task_id"
        const val KEY_SUBMITTED_MARKDOWN = "submitted_markdown"
        const val KEY_SUBMITTED_TEXT = "submitted_text"
        const val KEY_SUBMITTED_IMAGE_URI = "submitted_image_uri"

        private const val ARG_TASK_ID = "arg_task_id"
        private const val ARG_TASK_TITLE = "arg_task_title"

        fun newInstance(
            taskId: Int,
            taskTitle: String
        ): SubmitTaskDialogFragment {
            return SubmitTaskDialogFragment().apply {
                arguments = bundleOf(
                    ARG_TASK_ID to taskId,
                    ARG_TASK_TITLE to taskTitle
                )
            }
        }
    }

    private fun insertMarkdownImage(imageUri: String) {
        val editor = markdownEditor ?: return
        val content = editor.text?.toString().orEmpty()
        val appended = MarkdownContentHelper.appendImageSyntax(content, imageUri)
        editor.setText(appended)
        editor.setSelection(appended.length)
    }

    private fun copyImageToPrivateStorage(sourceUri: Uri): String? {
        val context = requireContext()
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            inputStream.use { input ->
                val dir = File(context.filesDir, "submitted_images").apply { mkdirs() }
                val file = File(dir, "md_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { output -> input.copyTo(output) }
                Uri.fromFile(file).toString()
            }
        }.getOrNull()
    }
}

