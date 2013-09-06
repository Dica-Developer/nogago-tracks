/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.apps.mytracks;

import com.google.android.apps.mytracks.fragments.InstallMapsDialogFragment;
import com.google.android.apps.mytracks.fragments.MapFragment;
import com.google.android.apps.mytracks.io.file.SaveActivity;
import com.google.android.apps.mytracks.io.file.TrackWriterFactory.TrackFileFormat;
import com.google.android.apps.mytracks.util.IntentUtils;
import com.google.android.apps.mytracks.util.PreferencesUtils;
import com.nogago.bb10.tracks.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.Toast;

import java.util.HashMap;

/**
 * This is a helper class that implements a generic mechanism for associating
 * fragments with the tabs in a tab host. It relies on a trick. Normally a tab
 * host has a simple API for supplying a View or Intent that each tab will show.
 * This is not sufficient for switching between fragments. So instead we make
 * the content part of the tab host 0dp high (it is not shown) and the
 * TabManager supplies its own dummy view to show as the tab content. It listens
 * to changes in tabs, and takes care of switch to the correct fragment shown in
 * a separate content area whenever the selected tab changes.
 * <p>
 * Copied from the Fragment Tabs example in the API 4+ Support Demos.
 * 
 * @author Jimmy Shih
 */
public class TabManager implements TabHost.OnTabChangeListener {

  private final TrackDetailActivity fragmentActivity;
  private final TabHost tabHost;
  private final int containerId;
  private final HashMap<String, TabInfo> tabs = new HashMap<String, TabInfo>();
  private TabInfo lastTabInfo;

  /**
   * An object to hold a tab's info.
   * 
   * @author Jimmy Shih
   */
  private static final class TabInfo {

    private final String tag;
    private final Class<?> clss;
    private final Bundle bundle;
    private Fragment fragment;

    public TabInfo(String tag, Class<?> clss, Bundle bundle) {
      this.tag = tag;
      this.clss = clss;
      this.bundle = bundle;
    }
  }

  /**
   * A dummy {@link TabContentFactory} that creates an empty view to satisfy the
   * {@link TabHost} API.
   * 
   * @author Jimmy Shih
   */
  private static class DummyTabContentFactory implements TabContentFactory {

    private final Context context;

    public DummyTabContentFactory(Context context) {
      this.context = context;
    }

    @Override
    public View createTabContent(String tag) {
      View view = new View(context);
      view.setMinimumWidth(0);
      view.setMinimumHeight(0);
      return view;
    }
  }

  public TabManager(TrackDetailActivity fragmentActivity, TabHost tabHost, int containerId) {
    this.fragmentActivity = fragmentActivity;
    this.tabHost = tabHost;
    this.containerId = containerId;
    tabHost.setOnTabChangedListener(this);
  }

  public void addTab(TabSpec tabSpec, Class<?> clss, Bundle bundle) {
    tabSpec.setContent(new DummyTabContentFactory(fragmentActivity));

    String tag = tabSpec.getTag();
    TabInfo tabInfo = new TabInfo(tag, clss, bundle);

    /*
     * Check to see if we already have a fragment for this tab, probably from a
     * previously saved state. If so, deactivate it, because our initial state
     * is that a tab isn't shown.
     */
    tabInfo.fragment = fragmentActivity.getSupportFragmentManager().findFragmentByTag(tag);
    if (tabInfo.fragment != null && !tabInfo.fragment.isDetached()) {
      FragmentTransaction fragmentTransaction = fragmentActivity.getSupportFragmentManager()
          .beginTransaction();
      fragmentTransaction.detach(tabInfo.fragment);
      fragmentTransaction.commit();
    }
    tabs.put(tag, tabInfo);
    tabHost.addTab(tabSpec);
  }

  /**
   * Returns true if nogago Map app is installed.
   */
  public static boolean isMapsInstalled(Activity a) {
    try {
      a.getPackageManager().getActivityInfo(Constants.MAPS_COMPONENT,
          PackageManager.GET_META_DATA);
      return true;
    } catch (NameNotFoundException nnfe) {
      return false;
    }
  }

  @Override
  public void onTabChanged(String tabId) {
    TabInfo newTabInfo = tabs.get(tabId);
    if (PreferencesUtils.getBoolean(fragmentActivity, R.string.settings_mapsprovider, true)
        && (tabId.compareTo(MapFragment.MAP_FRAGMENT_TAG) == 0)) {
      if(!isMapsInstalled(fragmentActivity)) {
        Fragment fragment =fragmentActivity.getSupportFragmentManager().findFragmentByTag(InstallMapsDialogFragment.INSTALL_MAPS_DIALOG_TAG);
        if (fragment == null) {
          InstallMapsDialogFragment.newInstance()
             .show(fragmentActivity.getSupportFragmentManager(), InstallMapsDialogFragment.INSTALL_MAPS_DIALOG_TAG); 
          }    
      } else {
        // Open Maps
        if(Constants.IS_BLACKBERRY) {
          Toast.makeText(fragmentActivity, R.string.track_detail_maps_blackberry_msg, Toast.LENGTH_LONG);
        }
        // Need to save the track first
        Intent intent = IntentUtils.newIntent(fragmentActivity, SaveActivity.class)
            .putExtra(SaveActivity.EXTRA_TRACK_ID, fragmentActivity.trackId)
            .putExtra(SaveActivity.EXTRA_TRACK_FILE_FORMAT, (Parcelable) TrackFileFormat.GPX)
            .putExtra(SaveActivity.EXTRA_SHOW_TRACK, true);
        // Then automatically opens nogago Maps based on the extras
        fragmentActivity.startActivity(intent);
      }
     } else {
      if (lastTabInfo != newTabInfo) {
        FragmentTransaction fragmentTransaction = fragmentActivity.getSupportFragmentManager()
            .beginTransaction();
        if (lastTabInfo != null) {
          if (lastTabInfo.fragment != null) {
            fragmentTransaction.detach(lastTabInfo.fragment);
          }
        }
        if (newTabInfo != null) {
          if (newTabInfo.fragment == null) {
            newTabInfo.fragment = Fragment.instantiate(fragmentActivity, newTabInfo.clss.getName(),
                newTabInfo.bundle);
            fragmentTransaction.add(containerId, newTabInfo.fragment, newTabInfo.tag);
          } else {
            fragmentTransaction.attach(newTabInfo.fragment);
          }
        }

        lastTabInfo = newTabInfo;
        fragmentTransaction.commitAllowingStateLoss();
        fragmentActivity.getSupportFragmentManager().executePendingTransactions();
      }
    }
  }

  public Fragment getCurrentFragment() {
    return lastTabInfo.fragment;
  }
}
