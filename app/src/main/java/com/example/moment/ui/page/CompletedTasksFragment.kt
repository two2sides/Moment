package com.example.moment.ui.page

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.moment.R
import java.io.File
import java.util.Calendar

class CompletedTasksFragment : Fragment(R.layout.fragment_completed_tasks) {

    private companion object {
        const val REPEAT_FILTER_REPEATABLE = 1
        const val REPEAT_FILTER_ONE_TIME = 2
    }

    private val adapter = CompletedCardAdapter { record ->
        CompletedTaskDetailDialogFragment.newInstance(record)
            .show(childFragmentManager, "CompletedTaskDetailDialog")
    }

    private lateinit var etNameKeyword: EditText
    private lateinit var etContentKeyword: EditText
    private lateinit var etStartDate: EditText
    private lateinit var etEndDate: EditText
    private lateinit var btnClearDateRange: Button
    private lateinit var spRepeatFilter: Spinner
    private lateinit var rvCompleted: RecyclerView
    private lateinit var tvCompletedEmpty: TextView

    private var startDateMillis: Long? = null
    private var endDateMillis: Long? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etNameKeyword = view.findViewById(R.id.etCompletedNameKeyword)
        etContentKeyword = view.findViewById(R.id.etCompletedContentKeyword)
        etStartDate = view.findViewById(R.id.etCompletedStartDate)
        etEndDate = view.findViewById(R.id.etCompletedEndDate)
        btnClearDateRange = view.findViewById(R.id.btnClearDateRange)
        spRepeatFilter = view.findViewById(R.id.spCompletedRepeatFilter)
        rvCompleted = view.findViewById(R.id.rvCompleted)
        tvCompletedEmpty = view.findViewById(R.id.tvCompletedEmpty)

        rvCompleted.layoutManager = LinearLayoutManager(requireContext())
        rvCompleted.adapter = adapter

        setupRepeatSpinner()
        setupDateFilters()
        setupSearchListeners()
        setupDeleteListener()

        loadCompleted()
    }

    override fun onResume() {
        super.onResume()
        loadCompleted()
    }

    private fun loadCompleted() {
        val titleKeyword = etNameKeyword.text.toString().trim()
        val contentKeyword = etContentKeyword.text.toString().trim()
        val isRepeatable = when (spRepeatFilter.selectedItemPosition) {
            REPEAT_FILTER_REPEATABLE -> true
            REPEAT_FILTER_ONE_TIME -> false
            else -> null
        }
        val timeRange = resolveCompletedAtRange()

        runDbThenUi(
            dbWork = { dao ->
                dao.searchCompletedTasks(
                    titleKeyword = titleKeyword,
                    contentKeyword = contentKeyword,
                    isRepeatable = isRepeatable,
                    completedStartAt = timeRange.first,
                    completedEndAt = timeRange.second
                )
            },
            uiWork = { records ->
                adapter.submitList(records)
                tvCompletedEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        )
    }

    private fun deleteCompletedRecord(recordId: Int, imageUri: String?, submittedMarkdown: String?) {
        runDbThenUi(
            dbWork = { dao ->
                dao.deleteCompletedTaskById(recordId)
                deleteLocalImagesIfOwned(
                    MarkdownContentHelper.collectImageUris(
                        markdown = submittedMarkdown,
                        fallbackImageUri = imageUri
                    )
                )
            },
            uiWork = {
                Toast.makeText(requireContext(), getString(R.string.completed_record_deleted), Toast.LENGTH_SHORT).show()
                loadCompleted()
            }
        )
    }

    private fun deleteLocalImagesIfOwned(imageUris: List<String>) {
        val safeRoot = File(requireContext().filesDir, "submitted_images").absolutePath
        imageUris.forEach { imageUri ->
            val parsed = android.net.Uri.parse(imageUri)
            if (parsed.scheme != "file") return@forEach
            val path = parsed.path ?: return@forEach
            if (!path.startsWith(safeRoot)) return@forEach
            runCatching { File(path).delete() }
        }
    }

    private fun setupSearchListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                loadCompleted()
            }
        }

        etNameKeyword.addTextChangedListener(watcher)
        etContentKeyword.addTextChangedListener(watcher)

        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadCompleted()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        spRepeatFilter.onItemSelectedListener = selectionListener
    }

    private fun setupRepeatSpinner() {
        val repeatAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.completed_repeat_filter_options,
            android.R.layout.simple_spinner_item
        )
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spRepeatFilter.adapter = repeatAdapter
    }

    private fun setupDateFilters() {
        etStartDate.setOnClickListener {
            showDatePicker(startDateMillis) { selectedMillis ->
                startDateMillis = startOfDay(selectedMillis)
                etStartDate.setText(UiTimeFormatters.formatDateOnly(startDateMillis!!))
                loadCompleted()
            }
        }

        etEndDate.setOnClickListener {
            showDatePicker(endDateMillis) { selectedMillis ->
                endDateMillis = startOfDay(selectedMillis)
                etEndDate.setText(UiTimeFormatters.formatDateOnly(endDateMillis!!))
                loadCompleted()
            }
        }

        btnClearDateRange.setOnClickListener {
            startDateMillis = null
            endDateMillis = null
            etStartDate.text?.clear()
            etEndDate.text?.clear()
            loadCompleted()
        }
    }

    private fun showDatePicker(initialMillis: Long?, onPicked: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            if (initialMillis != null) {
                timeInMillis = initialMillis
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onPicked(picked)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun resolveCompletedAtRange(): Pair<Long?, Long?> {
        var start = startDateMillis
        var end = endDateMillis?.let { endOfDay(it) }

        if (start != null && end != null && start > end) {
            val oldStart = start
            val oldEnd = endDateMillis ?: return start to end
            start = startOfDay(oldEnd)
            end = endOfDay(oldStart)
        }

        return start to end
    }

    private fun startOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun endOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    private fun setupDeleteListener() {
        childFragmentManager.setFragmentResultListener(
            CompletedTaskDetailDialogFragment.DELETE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val recordId = bundle.getInt(CompletedTaskDetailDialogFragment.KEY_RECORD_ID)
            val imageUri = bundle.getString(CompletedTaskDetailDialogFragment.KEY_IMAGE_URI)
            val submittedMarkdown = bundle.getString(CompletedTaskDetailDialogFragment.KEY_SUBMITTED_MARKDOWN)
            deleteCompletedRecord(recordId, imageUri, submittedMarkdown)
        }
    }

}

