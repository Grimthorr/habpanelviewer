package de.vier_bier.habpanelviewer.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

import com.jakewharton.processphoenix.ProcessPhoenix;

import de.vier_bier.habpanelviewer.R;
import de.vier_bier.habpanelviewer.UiUtil;

public class PreferencesUi extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_ui);

        ListPreference themePreference = (ListPreference) findPreference("pref_theme");
        themePreference.setOnPreferenceChangeListener((preference, o) -> {
            if (getActivity() != null && !getActivity().isFinishing()) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

                if (UiUtil.themeChanged(prefs, getActivity())) {
                    UiUtil.showSnackBar(getActivity().findViewById(R.id.myCoordinatorLayout),
                            R.string.themeChangedRestartRequired, R.string.action_restart,
                            view -> {
                                getActivity().finish();
                                ProcessPhoenix.triggerRebirth(getActivity().getApplication());
                            });
                }
            }

            return true;
        });
    }
}