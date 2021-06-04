/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;

import android.animation.ObjectAnimator;
import android.annotation.DrawableRes;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.util.Property;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.taskbar.contextual.RotationButton;
import com.android.launcher3.taskbar.contextual.RotationButtonController;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.AnimatedFloat;

import java.util.ArrayList;
import java.util.function.IntPredicate;

/**
 * Controller for managing nav bar buttons in taskbar
 */
public class NavbarButtonUIController {

    private final Rect mTempRect = new Rect();

    private static final int FLAG_SWITCHER_SUPPORTED = 1 << 0;
    private static final int FLAG_IME_VISIBLE = 1 << 1;
    private static final int FLAG_ROTATION_BUTTON_VISIBLE = 1 << 2;

    private static final int MASK_IME_SWITCHER_VISIBLE = FLAG_SWITCHER_SUPPORTED | FLAG_IME_VISIBLE;

    private final ArrayList<StatePropertyHolder> mPropertyHolders = new ArrayList<>();
    private final ArrayList<View> mAllButtons = new ArrayList<>();
    private int mState;

    private final TaskbarActivityContext mContext;

    public NavbarButtonUIController(TaskbarActivityContext context) {
        mContext = context;
    }

    /**
     * Initializes the controller
     */
    public void init(TaskbarDragLayer dragLayer,
            TaskbarNavButtonController navButtonController,
            RotationButtonController rotationButtonController,
            AnimatedFloat taskbarBackgroundAlpha, AlphaProperty taskbarIconAlpha) {
        FrameLayout buttonController = dragLayer.findViewById(R.id.navbuttons_view);
        buttonController.getLayoutParams().height = mContext.getDeviceProfile().taskbarSize;

        if (mContext.canShowNavButtons()) {
            ViewGroup startContainer = buttonController.findViewById(R.id.start_nav_buttons);
            ViewGroup endContainer = buttonController.findViewById(R.id.end_nav_buttons);

            initButtons(startContainer, endContainer, navButtonController);

            // Animate taskbar background when IME shows
            mPropertyHolders.add(new StatePropertyHolder(taskbarBackgroundAlpha,
                    flags -> (flags & FLAG_IME_VISIBLE) == 0,
                    AnimatedFloat.VALUE, 0, 1));
            mPropertyHolders.add(new StatePropertyHolder(
                    taskbarIconAlpha, flags -> (flags & FLAG_IME_VISIBLE) == 0,
                    MultiValueAlpha.VALUE, 1, 0));

            // Rotation button
            RotationButton rotationButton = new RotationButtonImpl(addButton(endContainer));
            rotationButton.hide();
            rotationButtonController.setRotationButton(rotationButton);
        } else {
            rotationButtonController.setRotationButton(new RotationButton() { });
        }

        applyState();
        mPropertyHolders.forEach(StatePropertyHolder::endAnimation);
    }

    private void initButtons(ViewGroup startContainer, ViewGroup endContainer,
            TaskbarNavButtonController navButtonController) {

        View backButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                startContainer, navButtonController);
        // Rotate when Ime visible
        mPropertyHolders.add(new StatePropertyHolder(backButton,
                flags -> (flags & FLAG_IME_VISIBLE) == 0, View.ROTATION, 0,
                Utilities.isRtl(mContext.getResources()) ? 90 : -90));

        // home and recents buttons
        View homeButton = addButton(R.drawable.ic_sysbar_home, BUTTON_HOME, startContainer,
                navButtonController);
        mPropertyHolders.add(new StatePropertyHolder(homeButton,
                flags -> (flags & FLAG_IME_VISIBLE) == 0));
        View recentsButton = addButton(R.drawable.ic_sysbar_recent, BUTTON_RECENTS,
                startContainer, navButtonController);
        mPropertyHolders.add(new StatePropertyHolder(recentsButton,
                flags -> (flags & FLAG_IME_VISIBLE) == 0));

        // IME switcher
        View imeSwitcherButton = addButton(R.drawable.ic_ime_switcher, BUTTON_IME_SWITCH,
                endContainer, navButtonController);
        mPropertyHolders.add(new StatePropertyHolder(imeSwitcherButton,
                flags -> ((flags & MASK_IME_SWITCHER_VISIBLE) == MASK_IME_SWITCHER_VISIBLE)
                        && ((flags & FLAG_ROTATION_BUTTON_VISIBLE) == 0)));
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setImeIsVisible(boolean isImeVisible) {
        if (isImeVisible) {
            mState |= FLAG_IME_VISIBLE;
        } else {
            mState &= ~FLAG_IME_VISIBLE;
        }
        applyState();
    }

    /**
     * Returns true if IME bar is visible
     */
    public boolean isImeVisible() {
        return (mState & FLAG_IME_VISIBLE) != 0;
    }

    /**
     * Adds the bounds corresponding to all visible buttons to provided region
     */
    public void addVisibleButtonsRegion(TaskbarDragLayer parent, Region outRegion) {
        int count = mAllButtons.size();
        for (int i = 0; i < count; i++) {
            View button = mAllButtons.get(i);
            if (button.getVisibility() == View.VISIBLE) {
                parent.getDescendantRectRelativeToSelf(button, mTempRect);
                outRegion.op(mTempRect, Op.UNION);
            }
        }
    }

    /**
     * Sets if ime switcher is visible or not when ime is visible
     */
    public void setImeSwitcherVisible(boolean imeSwitcherVisible) {
        if (imeSwitcherVisible) {
            mState |= FLAG_SWITCHER_SUPPORTED;
        } else {
            mState &= ~FLAG_SWITCHER_SUPPORTED;
        }
        applyState();
    }

    private void applyState() {
        int count = mPropertyHolders.size();
        for (int i = 0; i < count; i++) {
            mPropertyHolders.get(i).setState(mState);
        }
    }

    private ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController) {
        ImageView buttonView = addButton(parent);
        buttonView.setImageResource(drawableId);
        buttonView.setOnClickListener(view -> navButtonController.onButtonClick(buttonType));
        return buttonView;
    }

    private ImageView addButton(ViewGroup parent) {
        ImageView buttonView = (ImageView) mContext.getLayoutInflater()
                .inflate(R.layout.taskbar_nav_button, parent, false);
        parent.addView(buttonView);
        mAllButtons.add(buttonView);
        return buttonView;
    }

    private class RotationButtonImpl implements RotationButton {

        private final ImageView mButton;
        private AnimatedVectorDrawable mImageDrawable;

        RotationButtonImpl(ImageView button) {
            mButton = button;
        }

        @Override
        public void setRotationButtonController(RotationButtonController rotationButtonController) {
            // TODO(b/187754252) UI polish, different icons based on light/dark context, etc
            mImageDrawable = (AnimatedVectorDrawable) mButton.getContext()
                    .getDrawable(rotationButtonController.getIconResId());
            mButton.setImageDrawable(mImageDrawable);
            mImageDrawable.setCallback(mButton);
        }

        @Override
        public View getCurrentView() {
            return mButton;
        }

        @Override
        public void show() {
            mButton.setVisibility(View.VISIBLE);
            mState |= FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
        }

        @Override
        public void hide() {
            mButton.setVisibility(View.GONE);
            mState &= ~FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
        }

        @Override
        public boolean isVisible() {
            return mButton.getVisibility() == View.VISIBLE;
        }

        @Override
        public void updateIcon(int lightIconColor, int darkIconColor) {
            // TODO(b/187754252): UI Polish
        }

        @Override
        public void setOnClickListener(OnClickListener onClickListener) {
            mButton.setOnClickListener(onClickListener);
        }

        @Override
        public void setOnHoverListener(OnHoverListener onHoverListener) {
            mButton.setOnHoverListener(onHoverListener);
        }

        @Override
        public AnimatedVectorDrawable getImageDrawable() {
            return mImageDrawable;
        }

        @Override
        public void setDarkIntensity(float darkIntensity) {
            // TODO(b/187754252) UI polish
        }

        @Override
        public boolean acceptRotationProposal() {
            return mButton.isAttachedToWindow();
        }
    }

    private static class StatePropertyHolder {

        private final float mEnabledValue, mDisabledValue;
        private final ObjectAnimator mAnimator;
        private final IntPredicate mEnableCondition;

        private boolean mIsEnabled = true;

        StatePropertyHolder(View view, IntPredicate enableCondition) {
            this(view, enableCondition, LauncherAnimUtils.VIEW_ALPHA, 1, 0);
            mAnimator.addListener(new AlphaUpdateListener(view));
        }

        <T> StatePropertyHolder(T target, IntPredicate enabledCondition,
                Property<T, Float> property, float enabledValue, float disabledValue) {
            mEnableCondition = enabledCondition;
            mEnabledValue = enabledValue;
            mDisabledValue = disabledValue;
            mAnimator = ObjectAnimator.ofFloat(target, property, enabledValue, disabledValue);
        }

        public void setState(int flags) {
            boolean isEnabled = mEnableCondition.test(flags);
            if (mIsEnabled != isEnabled) {
                mIsEnabled = isEnabled;
                mAnimator.cancel();
                mAnimator.setFloatValues(mIsEnabled ? mEnabledValue : mDisabledValue);
                mAnimator.start();
            }
        }

        public void endAnimation() {
            if (mAnimator.isRunning()) {
                mAnimator.end();
            }
        }
    }
}