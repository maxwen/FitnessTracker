package com.maxwen.fitness;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler;

abstract class BaseController extends Controller {
    protected BaseController() { }

    protected BaseController(Bundle args) {
        super(args);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        // Quick way to access the toolbar for demo purposes. Production app needs to have this done properly
        MainActivity activity = (MainActivity) getActivity();

        // Activity should have already been set after the conductor is attached.
        assert activity != null;

        activity.getSupportActionBar().setTitle(getTitle());
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(getRouter().getBackstackSize() > 1);
    }

    public abstract String getTitle();

    @Override
    protected void onDestroyView(@NonNull View view) {
        super.onDestroyView(view);
    }

    public void switchToDayView(long day) {
        DayViewController controller = new DayViewController();
        controller.setDay(day);

        getRouter().pushController(RouterTransaction
                .with(controller)
                .pushChangeHandler(new FadeChangeHandler())
                .popChangeHandler(new FadeChangeHandler()));
    }

}
