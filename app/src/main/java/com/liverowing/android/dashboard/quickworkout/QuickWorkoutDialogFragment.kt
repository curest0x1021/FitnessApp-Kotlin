package com.liverowing.android.dashboard.quickworkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.liverowing.android.R
import com.liverowing.android.base.MvpLceBottomSheetDialogFragment
import kotlinx.android.synthetic.main.fragment_quick_workout_dialog.*
import kotlinx.android.synthetic.main.fragment_quick_workout_settings.*


class QuickWorkoutDialogFragment : MvpLceBottomSheetDialogFragment<RecyclerView, List<QuickWorkoutDialogPresenter.QuickWorkoutItem>, QuickWorkoutDialogView, QuickWorkoutDialogPresenter>(), QuickWorkoutDialogView {
    companion object {
        fun newInstance(listener: QuickWorkoutDialogListener): QuickWorkoutDialogFragment{
            val dialog = QuickWorkoutDialogFragment()
            dialog.listener = listener

            return dialog
        }
    }

    lateinit var listener: QuickWorkoutDialogListener
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    private val items = mutableListOf<QuickWorkoutDialogPresenter.QuickWorkoutItem>()
    private val workouts = mutableListOf<QuickWorkoutDialogPresenter.QuickWorkoutItem>()

    override fun createPresenter() = QuickWorkoutDialogPresenter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog.setOnShowListener { dialog ->
            val d = dialog as BottomSheetDialog
            val bottomSheetInternal = d.findViewById<View>(R.id.design_bottom_sheet)
            val behavior = BottomSheetBehavior.from(bottomSheetInternal!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isFitToContents = true
            behavior.peekHeight = 1000
        }
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimationEnterExit

        return inflater.inflate(R.layout.fragment_quick_workout_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        f_quick_workout_viewswitcher.inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
        f_quick_workout_viewswitcher.outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.slide_out_right)

        setupRecyclerView()
        presenter.loadQuickWorkouts()
    }

    private fun setupRecyclerView() {
        viewManager = LinearLayoutManager(activity)
        viewAdapter = QuickWorkoutDialogAdapter(items, Glide.with(this)) { _, item ->
            if (item.type == "group" && item.items != null) {
                setupQuickWorkoutSettings(item.items)
                f_quick_workout_viewswitcher.showNext()
            } else if (item.workoutType != null) {
                listener.onWorkoutTypeChoosen(this.dialog, item.workoutType)
            }
        }

        recyclerView = contentView.apply {
            setHasFixedSize(true)
            layoutManager = viewManager
            adapter = viewAdapter
        }
    }

    private fun setupQuickWorkoutSettings(items: List<QuickWorkoutDialogPresenter.QuickWorkoutItem>) {
        workouts.clear()
        workouts.addAll(items)

        f_quick_workout_back.setOnClickListener {
            f_quick_workout_viewswitcher.showPrevious()
        }

        f_quick_workout_cancel.setOnClickListener {
            listener.onCancel(this.dialog)
        }

        f_quick_workout_start_button.setOnClickListener {
            listener.onWorkoutTypeChoosen(this.dialog, items[f_quick_workout_seekbar.progress].workoutType!!)
        }

        f_quick_workout_seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (workouts.size >= progress) {
                    f_quick_workout_value.text = workouts[progress].name
                }
            }
        })

        f_quick_workout_seekbar.max = workouts.size - 1
        f_quick_workout_seekbar.progress = 0
    }

    override fun loadData(pullToRefresh: Boolean) {
        presenter.loadQuickWorkouts()
    }

    override fun setData(data: List<QuickWorkoutDialogPresenter.QuickWorkoutItem>) {
        items.clear()
        items.addAll(data)
        viewAdapter.notifyDataSetChanged()
    }

    override fun getErrorMessage(e: Throwable?, pullToRefresh: Boolean): String {
        return "There was an error fetching quick workouts:\n\n${e?.message}"
    }

}
