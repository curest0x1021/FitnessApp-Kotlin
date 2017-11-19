package com.liverowing.liverowing.activity.workouttype


import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.liverowing.liverowing.LiveRowing.Companion.eventBus
import com.liverowing.liverowing.R
import com.liverowing.liverowing.adapter.SegmentAdapter
import com.liverowing.liverowing.extensions.toggle
import com.liverowing.liverowing.model.parse.Segment
import com.liverowing.liverowing.model.parse.Workout
import com.liverowing.liverowing.model.parse.WorkoutType
import com.squareup.otto.Subscribe
import kotlinx.android.synthetic.main.activity_workout_type_detail.*
import kotlinx.android.synthetic.main.fragment_workout_type_details.*

private const val ARGUMENT_WORKOUT_TYPE = "workout_type"

class WorkoutTypeDetailsFragment : Fragment() {
    private lateinit var workoutType: WorkoutType
    private var segments = mutableListOf<Segment>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_workout_type_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        f_workout_type_details_interval_recyclerview.apply {
            isNestedScrollingEnabled = false
            layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
            adapter = SegmentAdapter(segments, Glide.with(this), { image, segment ->
                run {
                    Log.d("LiveRowing", "Clicked Segment row")
                }
            })
        }

        f_workout_type_details_interval_toggle.setOnClickListener {
            f_workout_type_details_interval_friendly.toggle()
            f_workout_type_details_interval_recyclerview.toggle()
            TransitionManager.beginDelayedTransition(f_workout_type_details_interval_group)
            f_workout_type_details_interval_toggle.rotation += 180f
        }

        eventBus.register(this)
    }

    @Subscribe
    fun onReceiveWorkoutType(workoutType: WorkoutType) {
        this.workoutType = workoutType
        f_workout_type_details_createdby_username.text = workoutType.createdBy!!.username
        f_workout_type_details_description_text.text = workoutType.descriptionText.toString()

        val friendlySegmentDescription = workoutType.friendlySegmentDescription
        if (!friendlySegmentDescription.isNullOrEmpty()) {
            f_workout_type_details_interval_group.visibility = View.VISIBLE
            f_workout_type_details_interval_friendly.text = friendlySegmentDescription

            segments.clear()
            segments.addAll(workoutType.segments!!)
            f_workout_type_details_interval_recyclerview.adapter.notifyDataSetChanged()
        } else {
            f_workout_type_details_interval_group.visibility = View.GONE
        }

        if (workoutType.createdBy!!.image != null && workoutType.createdBy!!.image?.url != null) {
            Glide.with(activity)
                    .load(workoutType.createdBy?.image?.url)
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .into(f_workout_type_details_createdby_image)
        }
    }

    override fun onStop() {
        super.onStop()
        eventBus.unregister(this)
    }

    companion object {
        fun newInstance(): WorkoutTypeDetailsFragment {
            return WorkoutTypeDetailsFragment()
        }
    }
}
