package com.maxwen.fitness;

import android.animation.Animator;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

    public WeekView(@androidx.annotation.NonNull @NonNull Context context, @Nullable @android.support.annotation.Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setController(WeekViewController controller) {
        mController = controller;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

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

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        mNowWeek = cal.get(Calendar.WEEK_OF_YEAR);
        mCurrentWeek = mNowWeek;
        setButtonVisible(mForwardButton, false).start();
        mRefresh.setEnabled(mCurrentWeek == mNowWeek);
    }

    public void getFitDataForPeriod(long startTime, long endTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(startTime);
        mCurrentWeek = cal.get(Calendar.WEEK_OF_YEAR);

        if (mCurrentWeek == mNowWeek) {
            setButtonVisible(mForwardButton, false).start();
        } else {
            setButtonVisible(mForwardButton, true).start();
        }
        mRefresh.setEnabled(mCurrentWeek == mNowWeek);
        getFitData(startTime, endTime);
    }

    private void getFitDataForWeek(int weekNumber) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.WEEK_OF_YEAR, weekNumber);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        long startTime = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_MONTH, 6);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        long endTime = cal.getTimeInMillis();

        mController.setTimePeriod(startTime, endTime);
        getFitData(startTime, endTime);
    }

    private void getFitData(long startTime, long endTime) {
        SimpleDateFormat formatTime = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat formatDate = new SimpleDateFormat("yyyy-MM-dd");
        Log.d(LOG_TAG, "start = " + formatTime.format(startTime) + " end = " + formatTime.format(endTime));
        mTimePeriod.setText("Week " + String.valueOf(mCurrentWeek) + " - " + formatDate.format(startTime) + " - " + formatDate.format(endTime));

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
                        mList.setVisibility(View.VISIBLE);
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

    private class DataPointViewHolder extends RecyclerView.ViewHolder {
        TextView mDate;
        TextView mSteps;
        TextView mDistance;
        long mStartTime;
        View mView;

        public DataPointViewHolder(View v) {
            super(v);
            mView = v.findViewById(R.id.card_view);
            mDate = v.findViewById(R.id.date);
            mSteps = v.findViewById(R.id.steps);
            mDistance = v.findViewById(R.id.distance);
        }

        public void setData(final DataPointItem item) {
            SimpleDateFormat format = new SimpleDateFormat("E");
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
