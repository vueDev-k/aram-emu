package io.github.mirusu400.aram.app;

import android.app.Activity;
import android.hardware.input.InputManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;

import java.util.ArrayList;
import java.util.List;

import io.github.mirusu400.aram.mobile.Mobile;

/**
 * The virtual keypad on the handset's second physical panel.
 *
 * RG DS and similar RK3568 clamshells expose their two panels as two Android
 * displays. The game runs in {@link MainActivity} on one panel; this Activity
 * owns the keypad on the other. Both live in the same process, so a key feeds
 * the shared Go frontend directly through {@link Mobile#pressControl} - there
 * is no second Ebitengine surface, and the game loop keeps running in its own
 * Activity. Control names match the frontend's on-screen deck, so a press here
 * is indistinguishable from a press on the built-in touch layout.
 */
public final class KeypadActivity extends Activity {
    private static final int TOUCH_DEVICE = Integer.MIN_VALUE;
    private final HeldControls heldControls = new HeldControls(Mobile::pressControl);
    private final List<Button> buttons = new ArrayList<>();
    private static final String THEME_PREF = "phone_theme";
    private static final String THEME_KEY = "selected";
    private static final String[] THEME_NAMES = {"Classic · Silver", "Modern · Dark"};
    private int selectedTheme;
    private InputManager inputManager;
    private InputManager.InputDeviceListener inputListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        selectedTheme = getSharedPreferences(THEME_PREF, MODE_PRIVATE).getInt(THEME_KEY, 0);
        setContentView(buildKeypad());
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        if (inputManager != null) {
            inputListener = new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {}

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    heldControls.releaseDevice(deviceId);
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    heldControls.releaseDevice(deviceId);
                }
            };
            inputManager.registerInputDeviceListener(inputListener, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The panel is showing, so the game panel drops its on-screen deck.
        Mobile.setSecondaryKeypadActive(true);
    }

    @Override
    protected void onPause() {
        releaseInputs();
        Mobile.setSecondaryKeypadActive(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        releaseInputs();
        Mobile.setSecondaryKeypadActive(false);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) {
            releaseInputs();
        }
    }

    private void releaseInputs() {
        heldControls.releaseAll();
        for (Button button : buttons) {
            button.setPressed(false);
        }
    }

    // The handset's physical gamepad is not tied to a display, so Android
    // routes it to the top-focused window - this keypad on the second panel -
    // and never to the game on the other panel. Ebitengine reads the pad from
    // events on its own view, so once the events land here the game is deaf to
    // it. These two handlers forward the pad to the game through the same
    // bridge the on-screen keys use, and consume the events so they do not also
    // hop focus between the keypad buttons.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        String control = controlForKeyCode(event.getKeyCode());
        if (control != null && isFromGamepad(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                heldControls.update(
                        "key:" + event.getDeviceId() + ":" + event.getKeyCode(),
                        event.getDeviceId(), control
                );
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldControls.update(
                        "key:" + event.getDeviceId() + ":" + event.getKeyCode(),
                        event.getDeviceId(), null
                );
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (isFromGamepad(event)
                && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            updateHat(
                    event.getDeviceId(),
                    event.getAxisValue(MotionEvent.AXIS_HAT_X),
                    event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            );
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // Many retro handhelds report their d-pad as a hat axis rather than as key
    // events. The axis carries no press/release, so the last direction is held
    // until the value returns to center, and a change releases the old one.
    private void updateHat(int deviceId, float hatX, float hatY) {
        String nextX = hatX < -0.5f ? "left" : hatX > 0.5f ? "right" : null;
        String nextY = hatY < -0.5f ? "up" : hatY > 0.5f ? "down" : null;
        heldControls.update("hat:" + deviceId + ":x", deviceId, nextX);
        heldControls.update("hat:" + deviceId + ":y", deviceId, nextY);
    }

    private static boolean isFromGamepad(KeyEvent event) {
        return isFromGamepad(event.getSource());
    }

    private static boolean isFromGamepad(MotionEvent event) {
        return isFromGamepad(event.getSource());
    }

    private static boolean isFromGamepad(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    // The face buttons follow the frontend's default pad layout: A confirms,
    // B goes back, X and Y are the soft keys, and Start/Select are the call
    // keys. The numeric keys stay on the touch grid.
    private static String controlForKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "down";
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "right";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return "ok";
            case KeyEvent.KEYCODE_BUTTON_B:
                return "back";
            case KeyEvent.KEYCODE_BUTTON_X:
                return "soft-left";
            case KeyEvent.KEYCODE_BUTTON_Y:
                return "soft-right";
            case KeyEvent.KEYCODE_BUTTON_START:
                return "send";
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return "end";
            default:
                return null;
        }
    }

    @Override
    protected void onDestroy() {
        releaseInputs();
        if (inputManager != null && inputListener != null) {
            inputManager.unregisterInputDeviceListener(inputListener);
        }
        // The keypad is gone; let the game panel bring its own deck back so the
        // controls are never left unreachable.
        Mobile.setSecondaryKeypadActive(false);
        super.onDestroy();
    }

    private View buildKeypad() {
        int pad = dp(8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int bezel = selectedTheme == 0 ? Color.rgb(166, 172, 183) : Color.rgb(15, 18, 25);
        root.setBackgroundColor(bezel);
        root.setPadding(pad, pad, pad, pad);
        Button theme = new Button(this);
        theme.setText(THEME_NAMES[selectedTheme] + "  ▾");
        theme.setAllCaps(false);
        theme.setTextSize(12);
        theme.setTextColor(selectedTheme == 0 ? Color.rgb(27, 34, 45) : Color.WHITE);
        theme.setBackground(buttonBackground(selectedTheme == 0 ? Color.rgb(221, 226, 233) : Color.rgb(38, 43, 58), dp(10), selectedTheme == 0 ? Color.rgb(105, 112, 125) : Color.rgb(65, 73, 95)));
        theme.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("피처폰 테마")
                .setSingleChoiceItems(THEME_NAMES, selectedTheme, (dialog, which) -> {
                    releaseInputs();
                    selectedTheme = which;
                    getSharedPreferences(THEME_PREF, MODE_PRIVATE).edit().putInt(THEME_KEY, which).apply();
                    buttons.clear();
                    setContentView(buildKeypad());
                    dialog.dismiss();
                }).setNegativeButton("취소", null).show());
        root.addView(theme, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(0, dp(4), 0, 0);
        panel.setBackground(buttonBackground(selectedTheme == 0 ? Color.rgb(112, 120, 133) : Color.rgb(23, 27, 38), dp(18), selectedTheme == 0 ? Color.rgb(79, 85, 97) : Color.rgb(55, 64, 84)));
        // The physical pad is forwarded to the game, not used to move between
        // these keys, so the buttons stay out of the focus order.
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        panel.addView(buildDirectionalSide(), equalColumn());
        panel.addView(buildNumericSide(), equalColumn());
        root.addView(panel, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    // Left column: the d-pad cross with OK at its center, then the soft keys
    // and the call/menu keys underneath.
    private View buildDirectionalSide() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        column.addView(row(spacer(), control("▲", "up"), spacer()), weightedRow(3));
        column.addView(
                row(control("◀", "left"), control("OK", "ok"), control("▶", "right")),
                weightedRow(3)
        );
        column.addView(row(spacer(), control("▼", "down"), spacer()), weightedRow(3));
        column.addView(
                row(control("Soft L", "soft-left"), control("Menu", "menu"), control("Soft R", "soft-right")),
                weightedRow(2)
        );
        column.addView(
                row(control("Send", "send"), control("Back", "back"), control("End", "end")),
                weightedRow(2)
        );
        return column;
    }

    // Right column: the phone numeric pad, 1-9 then * 0 #.
    private View buildNumericSide() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        column.addView(
                row(control("1", "num1"), control("2", "num2"), control("3", "num3")),
                weightedRow(1)
        );
        column.addView(
                row(control("4", "num4"), control("5", "num5"), control("6", "num6")),
                weightedRow(1)
        );
        column.addView(
                row(control("7", "num7"), control("8", "num8"), control("9", "num9")),
                weightedRow(1)
        );
        column.addView(
                row(control("*", "star"), control("0", "num0"), control("#", "hash")),
                weightedRow(1)
        );
        return column;
    }

    private LinearLayout row(View left, View middle, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, equalCell());
        row.addView(middle, equalCell());
        row.addView(right, equalCell());
        return row;
    }

    // A control button reports its press and release so the guest sees the key
    // held for as long as the finger is down, matching the on-screen deck.
    private View control(String label, String control) {
        Button button = new Button(this);
        buttons.add(button);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(selectedTheme == 0 ? 13 : 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(selectedTheme == 0 ? Color.rgb(25, 30, 40) : Color.rgb(235, 242, 255));
        button.setBackground(buttonBackground(
                selectedTheme == 0 ? Color.rgb(235, 239, 244) : Color.rgb(42, 49, 67),
                dp(selectedTheme == 0 ? 22 : 9),
                selectedTheme == 0 ? Color.rgb(111, 119, 132) : Color.rgb(73, 89, 120)));
        int margin = dp(3);
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    heldControls.update(view, TOUCH_DEVICE, control);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    heldControls.update(view, TOUCH_DEVICE, null);
                    return true;
                default:
                    return false;
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        );
        params.setMargins(margin, margin, margin, margin);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable buttonBackground(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private View spacer() {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ));
        return space;
    }

    private LinearLayout.LayoutParams equalColumn() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        );
    }

    private LinearLayout.LayoutParams equalCell() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        );
    }

    private LinearLayout.LayoutParams weightedRow(int weight) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, weight
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
