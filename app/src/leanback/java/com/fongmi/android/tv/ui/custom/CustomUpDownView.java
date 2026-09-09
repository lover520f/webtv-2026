package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.utils.KeyUtil;
import com.google.android.material.textview.MaterialTextView;

public class CustomUpDownView extends MaterialTextView {

    private UpListener upListener;
    private DownListener downListener;

    public CustomUpDownView(@NonNull Context context) {
        super(context);
    }

    public CustomUpDownView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setUpListener(UpListener upListener) {
        this.upListener = upListener;
    }

    public void setDownListener(DownListener downListener) {
        this.downListener = downListener;
    }

    private boolean hasEvent(KeyEvent event) {
        // Consume only key repeats (held key = continuous adjustment). A short press falls
        // through to the default focus search: previously every up/down press was swallowed,
        // so focus could never leave this row vertically with the D-pad.
        return event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0 && ((upListener != null && KeyUtil.isUpKey(event)) || (downListener != null && KeyUtil.isDownKey(event)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (hasEvent(event)) return onKeyDown(event);
        else return super.dispatchKeyEvent(event);
    }

    private boolean onKeyDown(KeyEvent event) {
        if (upListener != null && KeyUtil.isUpKey(event)) upListener.onUp();
        if (downListener != null && KeyUtil.isDownKey(event)) downListener.onDown();
        return true;
    }

    public interface UpListener {

        void onUp();
    }

    public interface DownListener {

        void onDown();
    }
}
