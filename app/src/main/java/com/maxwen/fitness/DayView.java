package com.maxwen.fitness;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessActivities;
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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class DayView extends FrameLayout {

    private static final String LOG_TAG = "DayView";
    public static final int MIN_ACTIVITY_TIME = 5 * 60 * 1000;
    public static final int MAX_ACTIVITY_PAUSE = 15 * 60 * 1000;
    private long lastTime;
    private RecyclerView mList;
    private DayView.DataPointAdapter mAdapter;
    private List<DataPointItem> mDataPoints = new ArrayList<>();
    private Map<Long, DataPointItem> mDataPointsByDay = new HashMap<>();
    private View mEmptyView;
    private long mStartTime;
    private long mEndTime;
    private TextView mTimePeriod;
    private long mDay;
    private DayViewController mController;
    private TextView mSumSteps;
    private TextView mSumDistance;
    private DataPointItem mLastDataPointItem;

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
        mList = findViewById(R.id.data_point_list);
        mSumDistance = findViewById(R.id.sum_distance);
        mSumSteps = findViewById(R.id.sum_steps);
        mAdapter = new DayView.DataPointAdapter();
        mList.setAdapter(mAdapter);
        mList.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mList.setLayoutManager(layoutManager);
    }

    public void getThisDayFitData() {
        Calendar cal = Calendar.getInstance(Locale.GERMAN);
        cal.setTimeInMillis(mDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        mStartTime = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        mEndTime = cal.getTimeInMillis();
        getFitData(mStartTime, mEndTime);
    }

    private void getFitData(long startTime, long endTime) {
        SimpleDateFormat formatTime = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        SimpleDateFormat formatDate = new SimpleDateFormat("E dd.MM.yyyy");
        Log.d(LOG_TAG, "start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));
        mTimePeriod.setText("" + formatDate.format(startTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByActivitySegment(1, TimeUnit.MINUTES)
                .enableServerQueries()
                .build();

        mDataPoints.clear();
        mLastDataPointItem = null;

        Fitness.getHistoryClient(getContext(), GoogleSignIn.getLastSignedInAccount(getContext()))
                .readData(readRequest)
                .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
                    @Override
                    public void onSuccess(DataReadResponse dataReadResponse) {
                        Log.d(LOG_TAG, "onSuccess()");
                        mEmptyView.setVisibility(View.GONE);
                        mList.setVisibility(View.VISIBLE);
                        for (Bucket b : dataReadResponse.getBuckets()) {
                            for (DataSet d : b.getDataSets()) {
                                loadDataSet(d);
                            }
                        }
                        List<DataPointItem> l = new ArrayList<>(mDataPoints);
                        for (DataPointItem d : l) {
                            if (d.mEndTime - d.mStartTime < MIN_ACTIVITY_TIME) {
                                mDataPoints.remove(d);
                            }
                        }
                        createSummary();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(LOG_TAG, "onFailure()", e);
                        mEmptyView.setVisibility(View.VISIBLE);
                        mList.setVisibility(View.GONE);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                        Log.d(LOG_TAG, "onComplete()");
                        mAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void loadDataSet(DataSet dataSet) {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (DataPoint dp : dataSet.getDataPoints()) {
            long dataPointTime = dp.getStartTime(TimeUnit.MINUTES);
            Log.i(LOG_TAG, format.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " - " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            if (lastTime == 0 || dataPointTime != lastTime) {
                DataPointItem item = new DataPointItem();
                item.mEndTime = dp.getEndTime(TimeUnit.MILLISECONDS);
                item.mStartTime = dp.getStartTime(TimeUnit.MILLISECONDS);
                mDataPointsByDay.put(dataPointTime, item);
            }
            DataPointItem item = mDataPointsByDay.get(dataPointTime);
            if (item == null) {
                continue;
            }
            for (Field field : dp.getDataType().getFields()) {
                if (dp.getDataType().getName().equals(DataType.TYPE_DISTANCE_DELTA.getName())) {
                    Log.i(LOG_TAG, String.format("%.2f", dp.getValue(field).asFloat() / 1000) + " km");
                    item.mDistance = dp.getValue(field).asFloat();
                } else if (dp.getDataType().getName().equals(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                    Log.i(LOG_TAG, dp.getValue(field) + " steps");
                    item.mSteps = dp.getValue(field).asInt();
                } else if (dp.getDataType().getName().equals("com.google.activity.summary")) {
                    if (item.mType == null) {
                        if (isCountedActivity(dp.getValue(field).asActivity())) {
                            Log.i(LOG_TAG, dp.getValue(field).asActivity());
                            item.mType = dp.getValue(field).asActivity();
                        }
                    }
                }
            }
            if (item.mSteps != -1 && item.mDistance != -1 && item.mType != null) {
                if (mLastDataPointItem != null) {
                    if (dp.getStartTime(TimeUnit.MILLISECONDS) - mLastDataPointItem.mEndTime < MAX_ACTIVITY_PAUSE) {
                        mLastDataPointItem.mEndTime = dp.getEndTime(TimeUnit.MILLISECONDS);
                        mLastDataPointItem.mSteps += item.mSteps;
                        mLastDataPointItem.mDistance += item.mDistance;
                    } else {
                        mDataPoints.add(item);
                        mLastDataPointItem = item;
                    }
                } else {
                    mDataPoints.add(item);
                    mLastDataPointItem = item;
                }
            }

            lastTime = dataPointTime;
        }
    }

    private boolean isCountedActivity(String activityType) {
        switch (activityType) {
            case FitnessActivities.WALKING:
                return true;
            default:
                return false;
        }
    }

    private void createSummary() {
        int sumSteps = 0;
        float sumDistance = 0f;

        for (DataPointItem item : mDataPoints) {
            sumSteps += item.mSteps;
            sumDistance += item.mDistance;
        }

        mSumSteps.setText("Steps: " + sumSteps);
        mSumDistance.setText("Distance: " + String.format("%.2f", sumDistance / 1000) + " km");
    }

    private class DataPointItem implements Comparable<DataPointItem> {
        long mStartTime;
        long mEndTime;
        int mSteps = -1;
        float mDistance = -1;
        String mType;

        @Override
        public int compareTo(@NonNull DataPointItem o) {
            if (mStartTime > o.mStartTime) {
                return 1;
            }
            return -1;
        }
    }

    private class DataPointViewHolder extends RecyclerView.ViewHolder {
        TextView mDate;
        TextView mSteps;
        TextView mDistance;
        long mStartTime;
        View mView;
        TextView mType;

        public DataPointViewHolder(View v) {
            super(v);
            mView = v.findViewById(R.id.card_view);
            mDate = v.findViewById(R.id.date);
            mSteps = v.findViewById(R.id.steps);
            mDistance = v.findViewById(R.id.distance);
            mType = v.findViewById(R.id.type);
        }

        public void setData(final DayView.DataPointItem item) {
            SimpleDateFormat format = new SimpleDateFormat("E dd.MM.yyyy ");
            SimpleDateFormat format1 = new SimpleDateFormat("HH:mm");
            mStartTime = item.mStartTime;

            mDate.setText(format.format(item.mStartTime) + "  " + format1.format(item.mStartTime) + "-" + format1.format(item.mEndTime));
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
            if (!TextUtils.isEmpty(item.mType)) {
                mType.setVisibility(View.VISIBLE);
                mType.setText(item.mType);
            }
        }
    }

    private class DataPointAdapter extends RecyclerView.Adapter<DayView.DataPointViewHolder> {
        public DayView.DataPointViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
            DayView.DataPointViewHolder vh = new DayView.DataPointViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.data_point_item, parent, false));
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull DayView.DataPointViewHolder dataPointHolder, int i) {
            dataPointHolder.setData(mDataPoints.get(i));
        }

        @Override
        public int getItemCount() {
            return mDataPoints.size();
        }

    }
}
