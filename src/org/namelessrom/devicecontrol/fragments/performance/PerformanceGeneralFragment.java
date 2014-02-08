/*
 *  Copyright (C) 2013 Alexander "Evisceration" Martinz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.namelessrom.devicecontrol.fragments.performance;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.SeekBarPreference;
import android.preference.SwitchPreference;

import org.namelessrom.devicecontrol.Application;
import org.namelessrom.devicecontrol.R;
import org.namelessrom.devicecontrol.threads.WriteAndForget;
import org.namelessrom.devicecontrol.utils.PreferenceHelper;
import org.namelessrom.devicecontrol.utils.Scripts;
import org.namelessrom.devicecontrol.utils.Utils;
import org.namelessrom.devicecontrol.utils.constants.DeviceConstants;
import org.namelessrom.devicecontrol.utils.constants.FileConstants;

import java.util.ArrayList;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class PerformanceGeneralFragment extends PreferenceFragment
        implements DeviceConstants, FileConstants, Preference.OnPreferenceChangeListener {

    //==============================================================================================
    // Fields
    //==============================================================================================
    private static boolean IS_LOW_RAM_DEVICE = false;
    private static final String FORCE_HIGHEND_GFX_PREF = "pref_force_highend_gfx";

    private CheckBoxPreference mForceHighEndGfx;

    public static final String sLcdPowerReduceFile = Utils.checkPaths(FILES_LCD_POWER_REDUCE);
    public static final boolean sLcdPowerReduce = !sLcdPowerReduceFile.equals("");

    public static final String sIntelliPlugEcoFile = Utils.checkPaths(FILES_INTELLI_PLUG_ECO);
    public static final boolean sIntelliPlugEco = !sIntelliPlugEcoFile.equals("");

    public static final String sMcPowerSchedulerFile = Utils.checkPaths(FILES_MC_POWER_SCHEDULER);
    public static final boolean sMcPowerScheduler = !sMcPowerSchedulerFile.equals("");

    private SwitchPreference mLcdPowerReduce;
    private SwitchPreference mIntelliPlugEco;
    private SeekBarPreference mMcPowerScheduler;
    private Preference mMcPowerSchedulerExtendedSummary;

    //==============================================================================================
    // Overridden Methods
    //==============================================================================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.performance_general);

        IS_LOW_RAM_DEVICE = Utils.getLowRamDevice(getActivity());

        if (IS_LOW_RAM_DEVICE) {
            mForceHighEndGfx = (CheckBoxPreference) findPreference(FORCE_HIGHEND_GFX_PREF);
        } else {
            getPreferenceScreen().removePreference(findPreference(FORCE_HIGHEND_GFX_PREF));
        }

        PreferenceGroup preferenceGroup = (PreferenceGroup) findPreference(CATEGORY_POWERSAVING);

        mLcdPowerReduce = (SwitchPreference) findPreference(KEY_LCD_POWER_REDUCE);
        if (sLcdPowerReduce) {
            mLcdPowerReduce.setChecked(Utils.readOneLine(sLcdPowerReduceFile).equals("1"));
            mLcdPowerReduce.setOnPreferenceChangeListener(this);
        } else {
            preferenceGroup.removePreference(mLcdPowerReduce);
        }

        mIntelliPlugEco = (SwitchPreference) findPreference(KEY_INTELLI_PLUG_ECO);
        if (sIntelliPlugEco) {
            mIntelliPlugEco.setChecked(Utils.readOneLine(sIntelliPlugEcoFile).equals("1"));
            mIntelliPlugEco.setOnPreferenceChangeListener(this);
        } else {
            preferenceGroup.removePreference(mIntelliPlugEco);
        }

        mMcPowerScheduler = (SeekBarPreference) findPreference(KEY_MC_POWER_SCHEDULER);
        mMcPowerSchedulerExtendedSummary = findPreference("sched_mc_power_savings_extended_summary");
        if (sMcPowerScheduler) {
            mMcPowerScheduler.setProgress(Integer.parseInt(Utils.readOneLine(sMcPowerSchedulerFile)));
            mMcPowerScheduler.setOnPreferenceChangeListener(this);
            mMcPowerSchedulerExtendedSummary
                    .setSummary(getMcPowerSchedulerSummary(mMcPowerScheduler.getProgress()));
        } else {
            preferenceGroup.removePreference(mMcPowerScheduler);
            preferenceGroup.removePreference(mMcPowerSchedulerExtendedSummary);
        }

        if (preferenceGroup.getPreferenceCount() == 0) {
            getPreferenceScreen().removePreference(preferenceGroup);
        }

        new PerformanceCpuTask().execute();

    }

    private String getMcPowerSchedulerSummary(final int progress) {
        String s;

        switch (progress) {
            default:
            case 0:
                s = getString(R.string.cpu_powersaving_sched_mc_summary
                        , getString(R.string.cpu_powersaving_sched_mc_zero));
                break;
            case 1:
                s = getString(R.string.cpu_powersaving_sched_mc_summary
                        , getString(R.string.cpu_powersaving_sched_mc_one));
                break;
            case 2:
                s = getString(R.string.cpu_powersaving_sched_mc_summary
                        , getString(R.string.cpu_powersaving_sched_mc_two));
                break;
        }

        return s;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        boolean changed = false;

        if (preference == mForceHighEndGfx) {
            Shell.SU.run(Scripts.toggleForceHighEndGfx());
            changed = true;
        } else if (preference == mLcdPowerReduce) {
            boolean value = (Boolean) o;
            new WriteAndForget(sLcdPowerReduceFile, (value ? "1" : "0")).run();
            PreferenceHelper.setBoolean(KEY_LCD_POWER_REDUCE, value);
            changed = true;
        } else if (preference == mIntelliPlugEco) {
            boolean value = (Boolean) o;
            new WriteAndForget(sIntelliPlugEcoFile, (value ? "1" : "0")).run();
            PreferenceHelper.setBoolean(KEY_INTELLI_PLUG_ECO, value);
            changed = true;
        } else if (preference == mMcPowerScheduler) {
            int value = (Integer) o;
            new WriteAndForget(sMcPowerSchedulerFile, "" + o).run();
            PreferenceHelper.setInt(KEY_MC_POWER_SCHEDULER, value);
            mMcPowerSchedulerExtendedSummary.setSummary(getMcPowerSchedulerSummary(value));
            changed = true;
        }

        return changed;
    }

    //==============================================================================================
    // Methods
    //==============================================================================================

    public void setResult(List<Boolean> paramResult) {
        int i = 0;
        if (IS_LOW_RAM_DEVICE && Application.HAS_ROOT) {
            mForceHighEndGfx.setChecked(paramResult.get(i));
            mForceHighEndGfx.setOnPreferenceChangeListener(this);
            i++;
        }
    }

    public static boolean isSupported(Context context) {
        return (sLcdPowerReduce || sIntelliPlugEco || Utils.getLowRamDevice(context));
    }

    //==============================================================================================
    // Internal Classes
    //==============================================================================================

    class PerformanceCpuTask extends AsyncTask<Void, Integer, List<Boolean>>
            implements DeviceConstants {

        @Override
        protected List<Boolean> doInBackground(Void... voids) {
            List<Boolean> tmpList = new ArrayList<Boolean>();

            if (IS_LOW_RAM_DEVICE && Application.HAS_ROOT) {
                tmpList.add(Scripts.getForceHighEndGfx());
            }

            return tmpList;
        }

        @Override
        protected void onPostExecute(List<Boolean> booleans) {
            setResult(booleans);
        }
    }
}