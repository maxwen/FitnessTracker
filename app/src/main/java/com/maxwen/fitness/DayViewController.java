package com.maxwen.fitness;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class DayViewController extends BaseController {

    private DayView mDayView;
    private long mDay;

    @NonNull
    @Override
    protected View onCreateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        mDayView = (DayView) inflater.inflate(R.layout.day_view, container, false);
        mDayView.setController(this);
        mDayView.setDay(mDay);
        return mDayView;
    }

    public String getTitle() {
        return "Day";
    }

    public void setDay(long day) {
        mDay = day;
    }
}
