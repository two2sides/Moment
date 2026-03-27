package com.example.moment.ui.page

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ImageButton
import android.graphics.Matrix
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.moment.R

class ImagePreviewDialogFragment : DialogFragment() {

    private val imageMatrix = Matrix()
    private var currentScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_image_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imageUri = requireArguments().getString(ARG_IMAGE_URI).orEmpty()

        val photoView = view.findViewById<ImageView>(R.id.pvPreview)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClosePreview)
        val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (currentScale * detector.scaleFactor).coerceIn(1f, 4f)
                val delta = newScale / currentScale
                imageMatrix.postScale(delta, delta, detector.focusX, detector.focusY)
                photoView.imageMatrix = imageMatrix
                currentScale = newScale
                return true
            }
        })

        photoView.setImageURI(Uri.parse(imageUri))
        photoView.imageMatrix = imageMatrix
        photoView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            if (!scaleDetector.isInProgress && currentScale > 1f) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        imageMatrix.postTranslate(dx, dy)
                        photoView.imageMatrix = imageMatrix
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
            true
        }
        btnClose.setOnClickListener { dismiss() }
    }

    companion object {
        private const val ARG_IMAGE_URI = "arg_image_uri"

        fun newInstance(imageUri: String): ImagePreviewDialogFragment {
            return ImagePreviewDialogFragment().apply {
                arguments = bundleOf(ARG_IMAGE_URI to imageUri)
            }
        }
    }
}

