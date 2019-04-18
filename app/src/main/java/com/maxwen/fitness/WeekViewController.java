package com.maxwen.fitness;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class WeekViewController extends BaseController {

    private WeekView mWeekView;
    private long mStartTime;
    private long mEndTime;

    @NonNull
    @Override
    protected View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        mWeekView = (WeekView) inflater.inflate(R.layout.week_view, container, false);
        mWeekView.setController(this);
        return mWeekView;
    }

    public String getTitle() {
        return "Week";
    }

    public void getFitDataForPeriod() {
        mWeekView.getFitDataForPeriod(mStartTime, mEndTime);
    }

    public void setTimePeriod(long startTime, long endTime) {
        mStartTime = startTime;
        mEndTime = endTime;
    }

    @Override
    protected void onRestoreViewState(@androidx.annotation.NonNull View view, @androidx.annotation.NonNull Bundle savedViewState) {
        mWeekView.getFitDataForPeriod(mStartTime, mEndTime);
    }
}
