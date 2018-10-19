package de.vier_bier.habpanelviewer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * UI utility methods.
 */
public class UiUtil {
    static synchronized String formatDateTime(Date d) {
        return d == null ? "-" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(d);
    }

    public static void showDialog(final Activity activity, final String title, final String text) {
        activity.runOnUiThread(() -> {
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(title);
            builder.setMessage(text);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        });
    }

    static void showScrollDialog(Context ctx, String title, String text, String scrollText) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setPositiveButton(android.R.string.yes, (dialog1, which) -> dialog1.dismiss())
                .setView(LayoutInflater.from(ctx).inflate(R.layout.scrollable_text_dialog, null))
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();

        TextView titleView = dialog.findViewById(R.id.release_notes_title);
        titleView.setText(text);
        TextView textView = dialog.findViewById(R.id.release_notes_text);
        textView.setText(scrollText);
    }

    public static void showSnackBar(View view, int textId, int actionTextId, View.OnClickListener clickListener) {
        showSnackBar(view, view.getContext().getString(textId), view.getContext().getString(actionTextId), clickListener);
    }

    public static void showSnackBar(View view, int textId) {
        showSnackBar(view, view.getContext().getString(textId), null, null);
    }

    public static void showSnackBar(View view, String text) {
        showSnackBar(view, text, null, null);
    }

    private static void showSnackBar(View view, String text, String actionText, View.OnClickListener clickListener) {
        Snackbar sb = Snackbar.make(view, text, Snackbar.LENGTH_LONG);
        View sbV = sb.getView();
        TextView textView = sbV.findViewById(android.support.design.R.id.snackbar_text);
        textView.setMaxLines(3);
        if (actionText != null && clickListener != null) {
            sb.setAction(actionText, clickListener);
        }

        sb.show();
    }

    public static int getThemeId(String theme) {
        if ("dark".equals(theme)) {
            return R.style.Theme_AppCompat_NoActionBar;
        }

        return R.style.Theme_AppCompat_Light_NoActionBar;
    }

    public static boolean themeChanged(SharedPreferences prefs, Activity ctx) {
        Resources.Theme dummy = ctx.getResources().newTheme();
        dummy.applyStyle(getThemeId(prefs.getString("pref_theme", "dark")), true);

        TypedValue a = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.windowBackground, a, true);
        TypedValue b = new TypedValue();
        dummy.resolveAttribute(android.R.attr.windowBackground, b, true);

        return a.data == b.data;
    }
}
