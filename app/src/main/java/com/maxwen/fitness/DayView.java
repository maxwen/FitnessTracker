package com.maxwen.fitness;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class DayView extends FrameLayout {

    private static final String LOG_TAG = "DayView";
    private long lastTime;
    private List<DataPointItem> mDataPoints = new ArrayList<>();
    private Map<Long, DataPointItem> mDataPointsByDay = new HashMap<>();
    private View mEmptyView;
    private long mStartTime;
    private long mEndTime;
    private TextView mTimePeriod;
    private long mDay;
    private DayViewController mController;
    private CardView mDayView;
    private TextView mDate;
    private TextView mSteps;
    private TextView mDistance;

    public DayView(@androidx.annotation.NonNull @NonNull Context context, @Nullable @android.support.annotation.Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setController(DayViewController controller) {
        mController = controller;
    }

    public void setDay(long day) {
        mDay = day;
        getThisDayFitData();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEmptyView = findViewById(R.id.empty_view);
        mTimePeriod = findViewById(R.id.time_period);
        mDayView = findViewById(R.id.card_view);
        mDate = findViewById(R.id.date);
        mSteps = findViewById(R.id.steps);
        mDistance = findViewById(R.id.distance);
    }

    public void getThisDayFitData() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        mStartTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        mEndTime = cal.getTimeInMillis();
        getFitData(mStartTime, mEndTime);
    }

    private void getFitData(long startTime, long endTime) {
        SimpleDateFormat formatTime = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat formatDate = new SimpleDateFormat("E yyyy-MM-dd");
        Log.d(LOG_TAG, "start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));
        mTimePeriod.setText("" + formatDate.format(startTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        mDataPoints.clear();
        Fitness.getHistoryClient(getContext(), GoogleSignIn.getLastSignedInAccount(getContext()))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.d(LOG_TAG, "onSuccess()");
                        mEmptyView.setVisibility(View.GONE);
                        mDayView.setVisibility(View.VISIBLE);
                        for (Bucket b : dataReadResponse.getBuckets()) {
                            for (DataSet d : b.getDataSets()) {
                                loadDataSet(d);
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(LOG_TAG, "onFailure()", e);
                        mEmptyView.setVisibility(View.VISIBLE);
                        mDayView.setVisibility(View.GONE);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                        Log.d(LOG_TAG, "onComplete()");
                        setData(mDataPoints.get(0));
                    }
                });
    }

    private void loadDataSet(DataSet dataSet) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (DataPoint dp : dataSet.getDataPoints()) {
            long dataPointTime = dp.getEndTime(TimeUnit.DAYS);
            Log.i(LOG_TAG, format.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " - " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            if (lastTime == 0 || dataPointTime != lastTime) {
                DataPointItem item = new DataPointItem();
                item.mEndTime = dp.getEndTime(TimeUnit.MILLISECONDS);
                item.mStartTime = dp.getStartTime(TimeUnit.MILLISECONDS);
                mDataPointsByDay.put(dataPointTime, item);
                mDataPoints.add(item);
            }
            for (Field field : dp.getDataType().getFields()) {
                if (dp.getDataType().getName().equals(DataType.TYPE_DISTANCE_DELTA.getName())) {
                    Log.i(LOG_TAG, String.format("%.2f", dp.getValue(field).asFloat() / 1000) + " km");

                    DataPointItem item = mDataPointsByDay.get(dataPointTime);
                    item.mDistance = dp.getValue(field).asFloat();

                } else if (dp.getDataType().getName().equals(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                    Log.i(LOG_TAG, dp.getValue(field) + " steps");

                    DataPointItem item = mDataPointsByDay.get(dataPointTime);
                    item.mSteps = dp.getValue(field).asInt();
                }
            }

            lastTime = dataPointTime;
        }
    }

    private class DataPointItem {
        long mStartTime;
        long mEndTime;
        int mSteps;
        float mDistance;
    }

    public void setData(DataPointItem item) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm");

        mDate.setText(format.format(item.mStartTime) + "-" + format.format(item.mEndTime));
        mSteps.setVisibility(View.GONE);
        mDistance.setVisibility(View.GONE);

        if (item.mSteps != 0) {
            mSteps.setVisibility(View.VISIBLE);
            mSteps.setText(item.mSteps + " steps");
        }
        if (item.mDistance != 0) {
            mDistance.setVisibility(View.VISIBLE);
            mDistance.setText(String.format("%.2f", item.mDistance / 1000) + " km");
        }
    }
}
