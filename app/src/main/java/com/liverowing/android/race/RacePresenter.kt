package com.liverowing.android.race

import android.os.Handler
import android.util.Base64
import com.liverowing.android.LiveRowing
import com.liverowing.android.base.EventBusPresenter
import com.liverowing.android.extensions.roundToDecimals
import com.liverowing.android.model.messages.*
import com.liverowing.android.model.parse.Affiliate
import com.liverowing.android.model.parse.User
import com.liverowing.android.model.parse.Workout
import com.liverowing.android.model.parse.WorkoutType
import com.liverowing.android.model.parse.WorkoutType.Companion.VALUE_TYPE_CUSTOM
import com.liverowing.android.model.pm.*
import com.parse.ParseException
import com.parse.ParseFile
import com.parse.ParseQuery
import com.parse.ParseUser
import kotlinx.serialization.json.JSON
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.util.*
import kotlin.math.roundToInt

class RacePresenter : EventBusPresenter<RaceView>() {
    private val mMetricsHolder = MetricsHolder()
    private var mWorkoutType: WorkoutType? = null

    private var mWorkout: Workout = Workout()
    private var mWorkoutStarted = false
    private var mResting = false
    private var mWorkoutFinished = false
    private var mCurrentIntervalOrSplit = 0

    private var mDeviceReady = false
    private var mWorkoutProgrammed = false
    private var mProgrammingWorkout = false
    private var mTerminatingWorkout = false

    private var mOpponentLastIndex = 0
    private var mOpponentWorkout: Workout? = null
    private var mPersonalBestLastIndex = 0
    private var mPersonalBestWorkout: Workout? = null

    private var mDataPoints = arrayListOf<Workout.DataPoint>()
    private var mStrokes = arrayListOf<Workout.Stroke>()
    private var mSplits = arrayListOf<Workout.Split>()

    init {
        mWorkout.createdBy = ParseUser.getCurrentUser() as User
        mWorkout.isDone = false
        mWorkout.withPersonalBest = false
        mWorkout.isChallenge = false
    }

    fun loadWorkoutTypeById(id: String) {
        ifViewAttached {
            it.setLoadingMessage("Loading workout..")
            it.showLoading(false)
        }

        val query = ParseQuery.getQuery(WorkoutType::class.java)
        query.include("createdBy")
        query.include("segments")
        query.getInBackground(id) { workoutType: WorkoutType, e: ParseException? ->
            if (e !== null) {
                if (e.code != ParseException.CACHE_MISS) {
                    ifViewAttached { it.showError(e, false) }
                }
            } else {
                setWorkoutType(workoutType)
            }
        }
    }

    fun setWorkoutType(workoutType: WorkoutType) {
        this.mWorkoutType = workoutType
        ifViewAttached {
            it.setData(workoutType)
            preFlightCheck()
        }
    }

    fun switchPrimaryMetricLeft() {
        mMetricsHolder.switchPrimaryMetricLeft()
        ifViewAttached { it.primaryMetricLeftUpdated(mMetricsHolder.primaryMetricLeft, true) }
    }

    fun switchPrimaryMetricRight() {
        mMetricsHolder.switchPrimaryMetricRight()
        ifViewAttached { it.primaryMetricRightUpdated(mMetricsHolder.primaryMetricRight, true) }
    }

    fun switchSecondaryMetricLeft() {
        mMetricsHolder.switchSecondaryMetricLeft()
        ifViewAttached { it.secondaryMetricLeftUpdated(mMetricsHolder.secondaryMetricLeft) }
    }

    fun switchSecondaryMetricCenter() {
        mMetricsHolder.switchSecondaryMetricCenter()
        updateStrokeRatio()
        ifViewAttached { it.secondaryMetricCenterUpdated(mMetricsHolder.secondaryMetricCenter) }
    }

    fun switchSecondaryMetricRight() {
        mMetricsHolder.switchSecondaryMetricRight()
        ifViewAttached { it.secondaryMetricRightUpdated(mMetricsHolder.secondaryMetricRight) }
    }

    private fun updateStrokeRatio() {
        val strokeRatio = mMetricsHolder.strokeRatio
        ifViewAttached {
            if (strokeRatio == null) {
                it.setStrokeRatioVisible(false)
            } else {
                it.strokeRatioUpdated(strokeRatio / 100)
                it.setStrokeRatioVisible(true)
            }
        }
    }

    fun setAffiliate(affiliate: Affiliate) {
        mWorkout.affiliate = affiliate
    }

    fun setPersonalBestWorkout(workout: Workout) {
        mPersonalBestWorkout = workout
        mWorkout.pbWorkout = workout
        mWorkout.withPersonalBest = true
    }

    fun setOpponentWorkout(workout: Workout) {
        mOpponentWorkout = workout
        mWorkout.isChallenge = true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWorkoutProgrammedMainThread(data: WorkoutProgrammed) {
        if (data.success) {
            Timber.d("** onWorkoutProgrammedMainThread")
            mWorkoutProgrammed = true
            preFlightCheck()
        } else {
            ifViewAttached {
                it.setLoadingMessage("Error programming workout")
                it.showLoading(false)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWorkoutTerminatedMainThread(data: WorkoutTerminated) {
        Timber.d("*** WORKOUT TERMINATED, SHOULD WAIT 1s BEFORE PROGRAMMING NEW")
        Handler().postDelayed({
            Timber.d("*** SAFE TO PROGRAM NEW WORKOUT!")
            mTerminatingWorkout = false
        }, 3000)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onDeviceReadyMainThread(data: DeviceReady) {
        mDeviceReady = true
        Timber.d("** onDeviceReadyMainThread")
        preFlightCheck()
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onDeviceDisconnectedMainThread(data: DeviceDisconnected) {
        mDeviceReady = false
        Timber.d("** onDeviceDisconnectedMainThread")
        preFlightCheck()
    }

    private fun preFlightCheck() {
        if (!mDeviceReady) {
            Timber.d("*** DEVICE IS NOT READY")
            ifViewAttached {
                it.setLoadingMessage("Click to connect")
                it.showLoading(false)
            }
        } else if (!mWorkoutProgrammed && LiveRowing.workoutState != WorkoutState.WAITTOBEGIN) {
            Timber.d("*** MUST TERMINATE CURRENT WORKOUT")
            ifViewAttached {
                it.setLoadingMessage("Terminating workout..")
                it.showLoading(false)

                if (!mTerminatingWorkout) {
                    mTerminatingWorkout = true
                    eventBus.post(WorkoutTerminateRequest(true))
                }
            }
        } else if (mTerminatingWorkout) {
            Timber.d("*** STILL TERMINATING WORKOUT")
        } else if (!mWorkoutProgrammed && !mProgrammingWorkout) {
            Timber.d("*** SHOULD PROGRAM WORKOUT")
            if (mWorkoutType is WorkoutType) {
                ifViewAttached {
                    it.setLoadingMessage("Please wait..")
                    it.showLoading(false)

                    mProgrammingWorkout = true
                    eventBus.post(WorkoutProgramRequest(mWorkoutType!!, null))
                }
            }
        } else if (!mWorkoutStarted) {
            Timber.d("*** ROW TO START")
            ifViewAttached {
                it.setLoadingMessage("Row to start!")
                it.showLoading(false)
            }
        } else if (mWorkoutFinished) {
            ifViewAttached {
                it.setLoadingMessage("Saving workout..")
                it.showLoading(false)
            }
        } else {
            Timber.d("*** SHOW CONTENT")
            ifViewAttached {
                it.showContent()
            }
        }
    }

    private fun updateViewMetrics() {
        ifViewAttached {
            it.primaryMetricLeftUpdated(mMetricsHolder.primaryMetricLeft, false)
            it.primaryMetricRightUpdated(mMetricsHolder.primaryMetricRight, false)

            it.secondaryMetricLeftUpdated(mMetricsHolder.secondaryMetricLeft)
            it.secondaryMetricCenterUpdated(mMetricsHolder.secondaryMetricCenter)
            it.secondaryMetricRightUpdated(mMetricsHolder.secondaryMetricRight)
        }
        updateStrokeRatio()
    }

    private fun workoutStarting(data: RowingStatus) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MILLISECOND, -(data.elapsedTime * 1000).toInt())
        mWorkout.startTime = calendar.time

        ifViewAttached {
            it.workoutStarting()
            it.showContent()
        }
    }

    private fun workoutResting() {
        ifViewAttached { it.workoutResting() }
    }

    private fun workoutContinuing() {
        ifViewAttached { it.workoutContinuing() }
    }

    private fun workoutFinished() {
        var json = "["
        mStrokes.forEach {
            json += "{\"d\": ${it.d}, \"p\": ${it.p}, \"hr\": ${it.hr}, \"spm\": ${it.spm}, \"t\": ${it.t}},"
        }
        json = json.substring(0, json.length - 1) + "]"

        mWorkoutFinished = true
        mWorkout.apply {
            finishTime = Calendar.getInstance().time
            isDone = true
        }

        ifViewAttached { it.workoutFinishing() }
    }

    private fun workoutTerminated() {
        mWorkoutFinished = true
        mWorkout.apply {
            finishTime = Calendar.getInstance().time
            isDone = false
        }

        /*
        EventBus.getDefault().unregister(this)
        workoutListener?.workoutTerminated()
        */
    }

    private fun logDataPoint(fromStrokeData: Boolean = false) {
        if (lastAdditionalRowingStatus1!!.currentPace.toDouble() > 0) {
            val dp = Workout.DataPoint(
                    workoutState = lastRowingStatus!!.workoutState,
                    caloriesBurned = lastAdditionalStrokeData!!.calories,
                    interval = mCurrentIntervalOrSplit,
                    meters = lastRowingStatus!!.distance,
                    splitTime = lastAdditionalRowingStatus1!!.currentPace.toDouble(),
                    strokesPerMinute = lastAdditionalRowingStatus1!!.strokeRate,
                    strokeLength = (lastStrokeData.strokeDistance * 10).toInt(),
                    watts = lastAdditionalStrokeData!!.power,
                    split = mCurrentIntervalOrSplit,
                    dragFactor = lastRowingStatus!!.dragFactor,
                    heartRate = if (lastAdditionalRowingStatus1!!.heartRate == 255) 0 else lastAdditionalRowingStatus1!!.heartRate,
                    strokeTime = (lastStrokeData.driveTime * 100).toInt(),
                    time = lastRowingStatus!!.elapsedTime,
                    timestamp = System.currentTimeMillis() / 1000,
                    isRow = fromStrokeData
            )

            mDataPoints.add(dp)
        }
    }

    private fun getPlaybackPosition(workout: Workout, lastIndex: Int, data: RowingStatus): Int {
        for (index in lastIndex until workout.playbackData.size) {
            val playbackData = workout.playbackData[index]
            if (playbackData.time > data.elapsedTime) {
                return index
            }
        }

        return lastIndex
    }

    private fun normalizePlaybackDistance(time: Double, data: List<Workout.DataPoint>, index: Int): Workout.DataPoint {
        return data[index]
        /*
        val lastData = if (index == 0) data[0].copy(time = 0.0) else data[index - 1]
        val normalizedMeters = lastData.meters + (((data[index].time - lastData.time) / (data[index].meters - lastData.meters)) * (time - lastData.time))
        return data[index].copy(meters = normalizedMeters)
        */
    }

    private var lastRowingStatus: RowingStatus? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRowingStatusMainThread(data: RowingStatus) {
        preFlightCheck()
        if (!mWorkoutStarted && mWorkoutProgrammed) {
            Timber.d("** onRowingStatusMainThread")
            if (data.workoutState == WorkoutState.WORKOUTROW || data.workoutState == WorkoutState.INTERVALWORKTIME || data.workoutState == WorkoutState.INTERVALWORKDISTANCE) {
                mWorkoutStarted = true
                workoutStarting(data)
            }
        }

        if (mWorkoutStarted && !mWorkoutFinished) {
            lastRowingStatus = data
            when (data.workoutState) {
                WorkoutState.WORKOUTROW -> logDataPoint()

                WorkoutState.INTERVALREST -> {
                    if (!mResting) {
                        mResting = true
                        workoutResting()
                    }
                    logDataPoint()
                }

                WorkoutState.INTERVALWORKTIME,
                WorkoutState.INTERVALWORKDISTANCE -> {
                    if (mResting) {
                        mResting = false
                        workoutContinuing()
                    }
                    logDataPoint()
                }

                WorkoutState.WORKOUTEND -> {
                    logDataPoint()
                    workoutFinished()
                }

                WorkoutState.TERMINATE -> workoutTerminated()

                else -> {}
            }

            var opponentPlaybackData: Workout.DataPoint? = null
            var personalBestPlaybackData: Workout.DataPoint? = null
            if (mPersonalBestWorkout != null && mPersonalBestWorkout!!.playbackData.size > 0) {
                mPersonalBestLastIndex = getPlaybackPosition(mPersonalBestWorkout!!, mPersonalBestLastIndex, data)
                personalBestPlaybackData = normalizePlaybackDistance(data.elapsedTime, mPersonalBestWorkout!!.playbackData, mPersonalBestLastIndex)
            }

            if (mOpponentWorkout is Workout && mOpponentWorkout!!.playbackData.size > 0) {
                mOpponentLastIndex = getPlaybackPosition(mOpponentWorkout!!, mOpponentLastIndex, data)
                opponentPlaybackData = normalizePlaybackDistance(data.elapsedTime, mOpponentWorkout!!.playbackData, mOpponentLastIndex)
            }

            //workoutListener?.workoutRowingStatus(mCurrentIntervalOrSplit, data, opponentPlaybackData, personalBestPlaybackData)
        }
    }

    private var lastAdditionalRowingStatus1: ExtraRowingStatus1? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdditionalRowingStatus1(data: ExtraRowingStatus1) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onAdditionalRowingStatus1")

        lastAdditionalRowingStatus1 = data

        if (mWorkoutStarted) {
            mMetricsHolder.onAdditionalRowingStatus1(data)
            updateViewMetrics()
        }
    }

    private var lastAdditionalRowingStatus2: ExtraRowingStatus2? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdditionalRowingStatus2(data: ExtraRowingStatus2) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onAdditionalRowingStatus2")

        lastAdditionalRowingStatus2 = data

        if (mCurrentIntervalOrSplit != data.intervalCount) {
            mCurrentIntervalOrSplit = data.intervalCount
        }

        if (mWorkoutStarted) {
            mMetricsHolder.onAdditionalRowingStatus2(data)
            updateViewMetrics()
        }
    }

    private var lastStrokeCountInWorkPeriod = 0
    private lateinit var lastStrokeData: StrokeData
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onStrokeData(data: StrokeData) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onStrokeData")

        lastStrokeData = data

        if (lastRowingStatus!!.workoutState != WorkoutState.INTERVALREST) {
            lastStrokeCountInWorkPeriod = data.strokeCount
        }
        logDataPoint(true)

        // TODO: If custom workout, use total time, not elapsedTime
        mStrokes.add(
                Workout.Stroke(
                        (data.elapsedTime * 10).toInt(),
                        (data.distance * 10).toInt(),
                        (lastAdditionalRowingStatus2!!.splitIntAvgPace * 10).toInt(),
                        lastAdditionalRowingStatus1!!.strokeRate,
                        if (lastAdditionalRowingStatus1!!.heartRate == 255) 0 else lastAdditionalRowingStatus1!!.heartRate
                )
        )

        if (mWorkoutStarted) {
            mMetricsHolder.onStrokeData(data)
            updateViewMetrics()
        }
    }

    private var lastAdditionalStrokeData: ExtraStrokeData? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdditionalStrokeData(data: ExtraStrokeData) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onAdditionalStrokeData")

        lastAdditionalStrokeData = data

        if (mWorkoutStarted) {
            mMetricsHolder.onAdditionalStrokeData(data)
            updateViewMetrics()
        }
    }

    private var lastSplitIntervalData: SplitIntervalData? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSplitIntervalData(data: SplitIntervalData) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onSplitIntervalData")

        lastSplitIntervalData = data
    }

    private var lastAdditionalSplitIntervalData: ExtraSplitIntervalData? = null
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAdditionalSplitIntervalData(data: ExtraSplitIntervalData) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onAdditionalSplitIntervalData")

        lastAdditionalSplitIntervalData = data

        var splitStrokeCount = 0
        if (mWorkoutType!!.valueType == VALUE_TYPE_CUSTOM) {
            splitStrokeCount = lastStrokeCountInWorkPeriod
        } else {
            val previousStrokeCount = mSplits.sumBy { it.splitStrokeCount }
            splitStrokeCount = lastStrokeCountInWorkPeriod - previousStrokeCount
        }

        val splitNumber = if (mWorkoutType!!.valueType == VALUE_TYPE_CUSTOM) data.intervalNumber else data.intervalNumber - 1
        val splitAvgDPS = lastSplitIntervalData!!.distance / splitStrokeCount

        // Distance workouts: elapsedTime, Time workouts: meters
        val splitTimeDistance = when (lastSplitIntervalData!!.intervalType) {
            IntervalType.TIME -> lastSplitIntervalData!!.distance
            IntervalType.DIST -> lastSplitIntervalData!!.splitTime
            else -> 0.0
        }

        val splitAvgDriveLength =
                mDataPoints
                        .filter { it.workoutState != WorkoutState.INTERVALREST && it.isRow && it.split == data.intervalNumber - 1 }
                        .map { it.strokeLength }
                        .average()
                        .toString()

        mSplits.add(Workout.Split(
                splitDistance = lastSplitIntervalData!!.splitDistance,
                splitHeartRate = data.workHeartRate.toDouble(),
                splitStrokeRate = data.spm,
                splitTimeDistance = splitTimeDistance,
                splitRestDistance = lastSplitIntervalData!!.restDistance.toDouble(),
                splitRestTime = lastSplitIntervalData!!.restTime,
                splitAvgDPS = splitAvgDPS.roundToDecimals(1),
                splitCals = data.calories.toDouble(),
                splitTime = lastSplitIntervalData!!.splitTime,
                splitAvgWatts = data.power.toDouble(),
                splitAvgDragFactor = data.averageDragFactor,
                splitAvgPace = data.pace.toDouble().roundToDecimals(1),
                splitAvgDriveLength = splitAvgDriveLength.toDouble().roundToDecimals(1),
                splitStrokeCount = splitStrokeCount,
                splitNumber = splitNumber
        ))

        mMetricsHolder.onAdditionalSplitIntervalData(data)
        updateViewMetrics()
    }

    private lateinit var workoutSummary: RowingSummary
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRowingSummary(data: RowingSummary) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onRowingSummary")

        workoutSummary = data
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onExtraRowingSummary(data: ExtraRowingSummary) {
        if (!mWorkoutStarted || mWorkoutFinished) return
        Timber.d("** onExtraRowingSummary")

        // TODO: Unregister EventBus here?

        val workTimeDataPoints = mDataPoints.filter { it.workoutState != WorkoutState.INTERVALREST }

        val maxSPM = workTimeDataPoints.maxBy { it.strokesPerMinute }!!.strokesPerMinute
        val maxWatt = workTimeDataPoints.maxBy { it.watts }!!.watts
        val fastestPace = workTimeDataPoints.minBy { it.splitTime }!!.splitTime
        val mSplitsDistance = mSplits.sumByDouble { it.splitDistance.toDouble() }
        val mSplitsStrokeCount = mSplits.sumBy { it.splitStrokeCount }
        val splitAvgDPS = (mSplitsDistance / mSplitsStrokeCount)
        val maxHeartRate = workTimeDataPoints.maxBy { it.heartRate }!!.heartRate
        val avgDriveLength =
                mDataPoints
                        .filter { it.isRow }
                        .map { it.strokeLength }
                        .average()


        val workoutData = Workout.WorkoutData(
                maxSPM = maxSPM,
                maxWatt = maxWatt,
                heartRate = workoutSummary.averageHeartRate,
                SplitsWatts = data.watts.toDouble(),
                splitType = data.type.value, // TODO: Really?
                fastestPace = fastestPace.roundToDecimals(1),
                SplitsAvgDPS = splitAvgDPS.roundToDecimals(1),
                strokeRate = workoutSummary.averageSpm,
                SplitsAvgDrag = workoutSummary.averageDragFactor.toDouble(),
                SplitsCals = data.calories.toDouble(),
                maxHeartRate = maxHeartRate.toDouble(),
                splitsAvgPace = workoutSummary.averagePace.toDouble().roundToDecimals(1),
                strokeCount = lastStrokeData.strokeCount,
                workTime = workoutSummary.elapsedTime,
                SplitsAvgDriveLength = avgDriveLength.roundToDecimals(1),
                workDistance = workoutSummary.distance.roundToInt(),
                heartRateNormalZoneTime = 0.0, // TODO: Implement
                heartRateZone1Time = 0.0, // TODO: Implement
                heartRateZone2Time = 0.0, // TODO: Implement
                heartRateZone3Time = 0.0,  // TODO: Implement
                splitSize = mWorkoutType!!.calculatedSplitLength,
                splits = mSplits
        )

        val dataPointWrapper = Workout.DataPointWrapper(mDataPoints)
        val dataPoints = Base64.encode(JSON.stringify(dataPointWrapper).toByteArray(), Base64.DEFAULT)
        val file = ParseFile(dataPoints)
        file.save()

        val avgSplitTime =
                mSplits
                        .map { it.splitTime.toDouble() }
                        .average()

        val totalTime =
                mSplits
                        .sumByDouble { it.splitRestTime.toDouble() + it.splitTime.toDouble() }

        val calendar = Calendar.getInstance()
        val endTime = calendar.time

        mWorkout.dataPoints = file
        mWorkout.dragFactor = workoutSummary.averageDragFactor
        mWorkout.averageHeartRate = workoutSummary.averageHeartRate
        mWorkout.meters = workoutSummary.distance.roundToInt()
        mWorkout.averageSplitTime = workoutSummary.averagePace.toDouble().roundToDecimals(1)
        mWorkout.averageWatts = data.watts
        mWorkout.isDone = true
        mWorkout.totalTime = totalTime //TimeUnit.MILLISECONDS.toSeconds(endTime.time - mWorkout.startTime!!.time).toInt()
        mWorkout.averageSPM = workoutSummary.averageSpm
        mWorkout.totalStrokeCount = lastStrokeCountInWorkPeriod // TODO: Probably wrong
        mWorkout.caloriesBurned = data.calories
        mWorkout.duration = workoutSummary.elapsedTime

        mWorkout.data = Workout.Data(workoutData, mStrokes, "standard")
        mWorkout.save()
    }
}