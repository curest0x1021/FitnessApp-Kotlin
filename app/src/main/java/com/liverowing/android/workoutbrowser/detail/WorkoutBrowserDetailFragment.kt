package com.liverowing.android.workoutbrowser.detail

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.tabs.TabLayout
import com.hannesdorfmann.mosby3.mvp.MvpFragment
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.LceViewState
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.MvpLceViewStateFragment
import com.hannesdorfmann.mosby3.mvp.viewstate.lce.data.RetainingLceViewState
import com.liverowing.android.MainActivity
import com.liverowing.android.R
import com.liverowing.android.model.parse.User
import com.liverowing.android.model.parse.Workout
import com.liverowing.android.model.parse.WorkoutType
import com.liverowing.android.race.RaceActivity
import kotlinx.android.synthetic.main.fragment_workout_browser_detail.*
import kotlinx.android.synthetic.main.workout_detail_collapsing_toolbar.*
import org.greenrobot.eventbus.EventBus


class WorkoutBrowserDetailFragment : MvpLceViewStateFragment<ViewPager, WorkoutType, WorkoutBrowserDetailView, WorkoutBrowserDetailPresenter>(), WorkoutBrowserDetailView {
    private lateinit var fragmentAdapter: WorkoutBrowserDetailAdapter
    private lateinit var workoutType: WorkoutType

    override fun createPresenter() = WorkoutBrowserDetailPresenter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_workout_browser_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MainActivity).setupToolbar(workout_detail_toolbar, workout_detail_collapsing_toolbar)

        fragmentAdapter = WorkoutBrowserDetailAdapter(childFragmentManager)
        contentView.adapter = fragmentAdapter
        contentView.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(f_workout_browser_detail_tabs))
        f_workout_browser_detail_tabs.addOnTabSelectedListener(TabLayout.ViewPagerOnTabSelectedListener(contentView))

        f_workout_browser_detail_fab.setOnClickListener {
            val intent = Intent(activity, RaceActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.onStart()
    }

    override fun onStop() {
        super.onStop()
        presenter.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.workout_detail, menu)
    }

    override fun loadData(pullToRefresh: Boolean) {
        val workoutTypeId = WorkoutBrowserDetailFragmentArgs.fromBundle(arguments).workoutTypeId
        if (workoutTypeId.isNotEmpty()) {
            presenter.loadWorkoutTypeById(workoutTypeId)
        }
    }

    override fun setData(data: WorkoutType) {
        workoutType = data
        workout_detail_toolbar.title = workoutType.name
        workout_detail_collapsing_toolbar.title = workoutType.name

        Glide
                .with(this@WorkoutBrowserDetailFragment)
                .load(workoutType.image?.url)
                .into(workout_detail_image)

        workout_detail_createdby.text = "Created by | ${workoutType.createdBy?.username}"
        Glide
                .with(this@WorkoutBrowserDetailFragment)
                .load(workoutType.createdBy?.image?.url)
                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                .into(workout_detail_createdby_image)
    }

    override fun createViewState(): LceViewState<WorkoutType, WorkoutBrowserDetailView> = RetainingLceViewState()

    override fun getData(): WorkoutType = workoutType

    override fun getErrorMessage(e: Throwable?, pullToRefresh: Boolean): String {
        return "There was an error loading workout details:\n\n${e?.message}"
    }
}
