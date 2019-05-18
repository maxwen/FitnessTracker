package com.maxwen.fitness;

import android.animation.Animator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;

public class WeekView extends FrameLayout {

    private static final String LOG_TAG = "WeekView";
    private long lastTime;
    private RecyclerView mList;
    private DataPointAdapter mAdapter;
    private List<DataPointItem> mDataPoints = new ArrayList<>();
    private Map<Long, DataPointItem> mDataPointsByDay = new HashMap<>();
    private SwipeRefreshLayout mRefresh;
    private View mEmptyView;
    private TextView mTimePeriod;
    private int mNowWeek;
    private View mForwardButton;
    private View mBackButton;
    private WeekViewController mController;
    private int mCurrentWeek;
    SimpleDateFormat formatTime = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    SimpleDateFormat formatDate = new SimpleDateFormat("E dd.MM.yyyy");
    private TextView mSumSteps;
    private TextView mSumDistance;

    public WeekView(@androidx.annotation.NonNull @NonNull Context context, @Nullable @android.support.annotation.Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setController(WeekViewController controller) {
        mController = controller;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSumDistance = findViewById(R.id.sum_distance);
        mSumSteps = findViewById(R.id.sum_steps);
        mList = findViewById(R.id.data_point_list);
        mAdapter = new DataPointAdapter();
        mList.setAdapter(mAdapter);
        mList.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mList.setLayoutManager(layoutManager);
        mEmptyView = findViewById(R.id.empty_view);
        mRefresh = findViewById(R.id.refresh_list);
        mRefresh.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        getFitDataForWeek(mCurrentWeek);
                    }
                }
        );
        mTimePeriod = findViewById(R.id.time_period);
        mBackButton = findViewById(R.id.time_period_back);
        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentWeek -= 1;
                getFitDataForWeek(mCurrentWeek);
                if (mCurrentWeek == mNowWeek - 1) {
                    setButtonVisible(mForwardButton, true).start();
                }
                mRefresh.setEnabled(mCurrentWeek == mNowWeek);
            }
        });

        mForwardButton = findViewById(R.id.time_period_forward);
        mForwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentWeek < mNowWeek) {
                    mCurrentWeek += 1;
                    getFitDataForWeek(mCurrentWeek);
                    if (mCurrentWeek == mNowWeek) {
                        setButtonVisible(mForwardButton, false).start();
                    }
                }
                mRefresh.setEnabled(mCurrentWeek == mNowWeek);
            }
        });

        Calendar cal = Calendar.getInstance(Locale.GERMAN);
        cal.setTime(new Date());
        mNowWeek = cal.get(Calendar.WEEK_OF_YEAR);
        mCurrentWeek = mNowWeek;
        setButtonVisible(mForwardButton, false).start();
        mRefresh.setEnabled(mCurrentWeek == mNowWeek);
    }

    public void getFitDataForPeriod(long startTime, long endTime) {
        Log.d(LOG_TAG, "getFitDataForPeriod start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));

        Calendar cal = Calendar.getInstance(Locale.GERMAN);
        cal.setTimeInMillis(startTime);
        mCurrentWeek = cal.get(Calendar.WEEK_OF_YEAR);

        if (mCurrentWeek == mNowWeek) {
            setButtonVisible(mForwardButton, false).start();
        } else {
            setButtonVisible(mForwardButton, true).start();
        }
        mRefresh.setEnabled(mCurrentWeek == mNowWeek);
        if (false) {
            getFitData(startTime, endTime);
        } else {
            getByActivityBucket(startTime);
        }
    }

    private void getFitDataForWeek(int weekNumber) {
        Calendar cal = Calendar.getInstance(Locale.GERMAN);
        cal.set(Calendar.WEEK_OF_YEAR, weekNumber);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        long startTime = cal.getTimeInMillis();

        Calendar endCal = Calendar.getInstance(Locale.GERMAN);
        endCal.setTimeInMillis(startTime);
        endCal.add(Calendar.DAY_OF_MONTH, 6);
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        long endTime = endCal.getTimeInMillis();

        mController.setTimePeriod(startTime, endTime);
        mDataPoints.clear();

        if (false) {
            getFitData(startTime, endTime);
        } else {
            getByActivityBucket(cal.getTimeInMillis());
        }
    }

    private void getByActivityBucket(long startTime) {
        long s = startTime;
        long endTime = 0;
        Calendar dayCal = Calendar.getInstance(Locale.GERMAN);
        mDataPointsByDay.clear();
        mDataPoints.clear();
        for (int i = 0; i < 7; i++) {
            dayCal.setTimeInMillis(startTime);
            dayCal.set(Calendar.HOUR_OF_DAY, 23);
            dayCal.set(Calendar.MINUTE, 59);
            endTime = dayCal.getTimeInMillis();

            getFitData2(startTime, endTime);

            dayCal.set(Calendar.HOUR_OF_DAY, 0);
            dayCal.set(Calendar.MINUTE, 0);
            dayCal.add(Calendar.DAY_OF_WEEK, 1);

            startTime = dayCal.getTimeInMillis();
        }

        mTimePeriod.setText(formatDate.format(s) + " - " + formatDate.format(endTime));
    }

    private void getFitData(long startTime, long endTime) {
        Log.d(LOG_TAG, "start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));
        mTimePeriod.setText(formatDate.format(startTime) + " - " + formatDate.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .enableServerQueries()
                .build();

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
                        createSummary();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(LOG_TAG, "onFailure()", e);
                        mRefresh.setRefreshing(false);
                        mEmptyView.setVisibility(View.VISIBLE);
                        mList.setVisibility(View.GONE);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                        Log.d(LOG_TAG, "onComplete()");
                        mAdapter.notifyDataSetChanged();
                        mRefresh.setRefreshing(false);
                    }
                });
    }


    private void getFitData2(long startTime, long endTime) {
        Log.d(LOG_TAG, "getFitData2 start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByActivityType(1, TimeUnit.MINUTES)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .enableServerQueries()
                .build();

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
                                loadDataSet2(d);
                            }
                        }
                        Collections.sort(mDataPoints);
                        createSummary();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(LOG_TAG, "onFailure()", e);
                        mRefresh.setRefreshing(false);
                        mEmptyView.setVisibility(View.VISIBLE);
                        mList.setVisibility(View.GONE);
                    }
                })
                .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<DataReadResponse> task) {
                        Log.d(LOG_TAG, "onComplete()");
                        mAdapter.notifyDataSetChanged();
                        mRefresh.setRefreshing(false);
                    }
                });
    }


    private void loadDataSet(DataSet dataSet) {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (DataPoint dp : dataSet.getDataPoints()) {
            long dataPointTime = dp.getEndTime(TimeUnit.DAYS);
            Log.i(LOG_TAG, format.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " - " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            if (lastTime == 0 || dataPointTime != lastTime) {
                DataPointItem item = new DataPointItem();
                item.mEndTime = dp.getEndTime(TimeUnit.MILLISECONDS);
                item.mStartTime = dp.getStartTime(TimeUnit.MILLISECONDS);
                Calendar dayCal = Calendar.getInstance(Locale.GERMAN);
                dayCal.setTimeInMillis(item.mStartTime);
                item.mDay = dayCal.get(Calendar.DAY_OF_WEEK);
                mDataPointsByDay.put(dataPointTime, item);
                mDataPoints.add(item);
            }
            for (Field field : dp.getDataType().getFields()) {
                DataPointItem item = mDataPointsByDay.get(dataPointTime);
                if (item == null) {
                    continue;
                }
                if (dp.getDataType().getName().equals(DataType.TYPE_DISTANCE_DELTA.getName())) {
                    Log.i(LOG_TAG, String.format("%.2f", dp.getValue(field).asFloat() / 1000) + " km");
                    item.mDistance = dp.getValue(field).asFloat();
                } else if (dp.getDataType().getName().equals(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                    Log.i(LOG_TAG, dp.getValue(field) + " steps");
                    item.mSteps = dp.getValue(field).asInt();
                }
            }

            lastTime = dataPointTime;
        }
    }

    private void loadDataSet2(DataSet dataSet) {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        for (DataPoint dp : dataSet.getDataPoints()) {
            long dataPointTime = dp.getEndTime(TimeUnit.MINUTES);
            Log.i(LOG_TAG, format.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " - " + format.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            if (lastTime == 0 || dataPointTime != lastTime) {
                DataPointItem item = new DataPointItem();
                item.mEndTime = dp.getEndTime(TimeUnit.MILLISECONDS);
                item.mStartTime = dp.getStartTime(TimeUnit.MILLISECONDS);
                Calendar dayCal = Calendar.getInstance(Locale.GERMAN);
                dayCal.setTimeInMillis(item.mStartTime);
                item.mDay = dayCal.get(Calendar.DAY_OF_WEEK);
                mDataPointsByDay.put(dataPointTime, item);
            }
            DataPointItem item = mDataPointsByDay.get(dataPointTime);
            if (item == null) {
                continue;
            }
            boolean typeDone = false;
            for (Field field : dp.getDataType().getFields()) {
                if (dp.getDataType().getName().equals(DataType.TYPE_DISTANCE_DELTA.getName())) {
                    Log.i(LOG_TAG, String.format("%.2f", dp.getValue(field).asFloat() / 1000) + " km");
                    item.mDistance = dp.getValue(field).asFloat();
                } else if (dp.getDataType().getName().equals(DataType.TYPE_STEP_COUNT_DELTA.getName())) {
                    Log.i(LOG_TAG, dp.getValue(field) + " steps");
                    item.mSteps = dp.getValue(field).asInt();
                } else if (dp.getDataType().getName().equals("com.google.activity.summary")) {
                    if (typeDone) {
                        continue;
                    }
                    Log.i(LOG_TAG, dp.getValue(field).asActivity());
                    if (isCountedActivity(dp.getValue(field).asActivity())) {
                        item.mType = dp.getValue(field).asActivity();
                    }
                    typeDone = true;
                }
            }
            if (item.mType != null && item.mSteps != -1 && item.mDistance != -1) {
                int idx = mDataPoints.indexOf(item);
                if (idx != -1) {
                    DataPointItem d = mDataPoints.get(idx);
                    d.mStartTime = item.mStartTime;
                    d.mEndTime = item.mEndTime;
                    d.mDistance = item.mDistance;
                    d.mSteps = item.mSteps;
                    d.mType = item.mType;
                } else {
                    mDataPoints.add(item);
                }
            }

            lastTime = dataPointTime;
        }
    }


    private void createSummary() {
        int sumSteps = 0;
        float sumDistance = 0f;

        for (DataPointItem item : mDataPoints) {
            sumSteps += item.mSteps;
            sumDistance += item.mDistance;
        }

        mSumSteps.setText(String.valueOf(sumSteps));
        mSumDistance.setText(String.format("%.2f", sumDistance / 1000) + " km");
    }

    private boolean isCountedActivity(String activityType) {
        switch (activityType) {
            case FitnessActivities.WALKING:
            case FitnessActivities.RUNNING:
            case FitnessActivities.BIKING:
                return true;
            default:
                return false;
        }
    }

    private class DataPointItem implements Comparable<DataPointItem> {
        long mStartTime;
        long mEndTime;
        int mSteps = -1;
        float mDistance = -1;
        String mType;
        int mDay;

        @Override
        public int compareTo(@NonNull DataPointItem o) {
            if (mStartTime > o.mStartTime) {
                return 1;
            }
            return -1;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof DataPointItem)) {
                return false;
            }
            return mDay == ((DataPointItem) o).mDay && mType == ((DataPointItem) o).mType;
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

        public void setData(final DataPointItem item) {
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
            mView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {

                    mController.switchToDayView(mStartTime);
                }
            });
        }
    }

    private class DataPointAdapter extends RecyclerView.Adapter<DataPointViewHolder> {
        public DataPointViewHolder onCreateViewHolder(ViewGroup parent,
                                                      int viewType) {
            DataPointViewHolder vh = new DataPointViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.data_point_item, parent, false));
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull DataPointViewHolder dataPointHolder, int i) {
            dataPointHolder.setData(mDataPoints.get(i));
        }

        @Override
        public int getItemCount() {
            return mDataPoints.size();
        }

    }


    protected Animator setButtonVisible(View button, boolean show) {
        final Animator buttonAnimator = AnimatorUtils.getScaleAnimator(
                button, show ? 0.0f : 1.0f, show ? 1.0f : 0.0f);

        return buttonAnimator;
    }
}
