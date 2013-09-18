/* 
 * enviroCar 2013
 * Copyright (C) 2013  
 * Martin Dueren, Jakob Moellers, Gerald Pape, Christopher Stephan
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 * 
 */

package org.envirocar.app.activity;

import org.envirocar.app.R;
import org.envirocar.app.activity.DialogUtil.DialogCallback;
import org.envirocar.app.application.CarManager;
import org.envirocar.app.application.ECApplication;
import org.envirocar.app.application.NavMenuItem;
import org.envirocar.app.application.UserManager;
import org.envirocar.app.application.service.AbstractBackgroundServiceStateReceiver;
import org.envirocar.app.application.service.BackgroundServiceImpl;
import org.envirocar.app.application.service.AbstractBackgroundServiceStateReceiver.ServiceState;
import org.envirocar.app.application.service.DeviceInRangeService;
import org.envirocar.app.logging.Logger;
import org.envirocar.app.storage.DbAdapterImpl;
import org.envirocar.app.views.TypefaceEC;
import org.envirocar.app.views.Utils;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;


/**
 * Main UI application that cares about the auto-upload, auto-connect and global
 * UI elements
 * 
 * @author jakob
 * @author gerald
 * 
 * @param <AndroidAlarmService>
 */
public class MainActivity<AndroidAlarmService> extends SherlockFragmentActivity implements OnItemClickListener {

	public static final int TRACK_MODE_SINGLE = 0;
	public static final int TRACK_MODE_AUTO = 1;
	
	private int actionBarTitleID = 0;
	private ActionBar actionBar;

	public ECApplication application;

	private FragmentManager manager;
	//Navigation Drawer
	private DrawerLayout drawer;
	private ListView drawerList;
	private NavAdapter navDrawerAdapter;

	// Menu Items
	private NavMenuItem[] navDrawerItems;

	static final int DASHBOARD = 0;
	static final int LOGIN = 1;
	static final int MY_TRACKS = 2;
	static final int START_STOP_MEASUREMENT = 3;
	static final int SETTINGS = 4;
	static final int HELP = 5;
	static final int SEND_LOG = 6;
	
	static final String DASHBOARD_TAG = "DASHBOARD";
	static final String LOGIN_TAG = "LOGIN";
	static final String MY_TRACKS_TAG = "MY_TRACKS";
	static final String HELP_TAG = "HELP";
	static final String SEND_LOG_TAG = "SEND_LOG";

	public static final int REQUEST_MY_GARAGE = 1336;
	public static final int REQUEST_REDIRECT_TO_GARAGE = 1337;
	
	private static final Logger logger = Logger.getLogger(MainActivity.class);
	private static final String SERVICE_STATE = "serviceState";
	private static final String TRACK_MODE = "trackMode";
	
	// Include settings for auto upload and auto-connect
	
	private SharedPreferences preferences = null;
	boolean alwaysUpload = false;
	boolean uploadOnlyInWlan = true;
	private BroadcastReceiver serviceStateReceiver;
	private OnSharedPreferenceChangeListener settingsReceiver;
	protected ServiceState serviceState = ServiceState.SERVICE_STOPPED;
	private BroadcastReceiver bluetoothStateReceiver;
	private int trackMode = TRACK_MODE_SINGLE;
		
	private void prepareNavDrawerItems(){
		if(this.navDrawerItems == null){
			navDrawerItems = new NavMenuItem[7];
			navDrawerItems[LOGIN] = new NavMenuItem(LOGIN, getResources().getString(R.string.menu_login),R.drawable.device_access_accounts);
			navDrawerItems[SETTINGS] = new NavMenuItem(SETTINGS, getResources().getString(R.string.menu_settings),R.drawable.action_settings);
			navDrawerItems[START_STOP_MEASUREMENT] = new NavMenuItem(START_STOP_MEASUREMENT, getResources().getString(R.string.menu_start),R.drawable.av_play);
			navDrawerItems[DASHBOARD] = new NavMenuItem(DASHBOARD, getResources().getString(R.string.dashboard), R.drawable.dashboard);
			navDrawerItems[MY_TRACKS] = new NavMenuItem(MY_TRACKS, getResources().getString(R.string.my_tracks),R.drawable.device_access_storage);
			navDrawerItems[HELP] = new NavMenuItem(HELP, getResources().getString(R.string.menu_help), R.drawable.action_help);
			navDrawerItems[SEND_LOG] = new NavMenuItem(SEND_LOG, getResources().getString(R.string.menu_send_log), R.drawable.action_report);
		}
		
		if (UserManager.instance().isLoggedIn()) {
			navDrawerItems[LOGIN].setTitle(getResources().getString(R.string.menu_logout));
			navDrawerItems[LOGIN].setSubtitle(String.format(getResources().getString(R.string.logged_in_as),UserManager.instance().getUser().getUsername()));
		} else {
			navDrawerItems[LOGIN].setTitle(getResources().getString(R.string.menu_login));
			navDrawerItems[LOGIN].setSubtitle("");		
		}

		navDrawerAdapter.notifyDataSetChanged();

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		readSavedState(savedInstanceState);
		
		this.setContentView(R.layout.main_layout);

		application = ((ECApplication) getApplication());
		
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		alwaysUpload = preferences.getBoolean(SettingsActivity.ALWAYS_UPLOAD, false);
        uploadOnlyInWlan = preferences.getBoolean(SettingsActivity.WIFI_UPLOAD, true);

        checkKeepScreenOn();
        
		actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setHomeButtonEnabled(true);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setTitle("");

		// font stuff
		actionBarTitleID = Utils.getActionBarId();
		if (Utils.getActionBarId() != 0) {
			((TextView) this.findViewById(actionBarTitleID))
					.setTypeface(TypefaceEC.Newscycle(this));
		}

		actionBar.setLogo(getResources().getDrawable(R.drawable.actionbarlogo_with_padding));
		
		manager = getSupportFragmentManager();

		DashboardFragment initialFragment = new DashboardFragment();
		manager.beginTransaction().replace(R.id.content_frame, initialFragment, DASHBOARD_TAG)
		.commit();
		
		drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawerList = (ListView) findViewById(R.id.left_drawer);
		navDrawerAdapter = new NavAdapter();
		prepareNavDrawerItems();
		updateStartStopButton();
		drawerList.setAdapter(navDrawerAdapter);
		ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(
				this, drawer, R.drawable.ic_drawer, R.string.open_drawer,
				R.string.close_drawer) {
		
			@Override
			public void onDrawerOpened(View drawerView) {
				prepareNavDrawerItems();
				super.onDrawerOpened(drawerView);
			}
		};

		drawer.setDrawerListener(actionBarDrawerToggle);
		drawerList.setOnItemClickListener(this);
		
		manager.executePendingTransactions();

		serviceStateReceiver = new AbstractBackgroundServiceStateReceiver() {
			@Override
			public void onStateChanged(ServiceState state) {
				serviceState = state;
				updateStartStopButton();
			}
		};
		
		registerReceiver(serviceStateReceiver, new IntentFilter(AbstractBackgroundServiceStateReceiver.SERVICE_STATE));

		bluetoothStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				updateStartStopButton();
			}
		};
		
		registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		
		settingsReceiver = new OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(
					SharedPreferences sharedPreferences, String key) {
				if (key.equals(SettingsActivity.BLUETOOTH_NAME)) {
					updateStartStopButton();
				}
				else if (key.equals(CarManager.PREF_KEY_SENSOR_ID)) {
					updateStartStopButton();
				}
			}
		};
		
		preferences.registerOnSharedPreferenceChangeListener(settingsReceiver);
		
//		/*
//		 * Auto-Uploader of tracks. Uploads complete tracks every 10 minutes.
//		 */
//
//		ScheduledExecutorService uploadTaskExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory(getClass().getName()+"-Factory"));
//		uploadTaskExecutor.scheduleAtFixedRate(new Runnable() {
//
//			@Override
//			public void run() {
//				ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
//				NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
//
//				if (alwaysUpload) {
//					logger.info("Automatic upload will start");
//					if (UserManager.instance().isLoggedIn()) {
//						try {
//							if (!serviceRunning) {
//								logger.info("Service connector not running");
//								if (uploadOnlyInWlan == true) {
//									if (mWifi.isConnected()) {
//										logger.info("Uploading tracks 1");
//										handler_upload.post(new Runnable() {
//
//											@Override
//											public void run() {
//												uploadTracks();
//											}
//										});
//									}
//								} else {
//									logger.info("Uploading tracks 2");
//									handler_upload.post(new Runnable() {
//
//										@Override
//										public void run() {
//											uploadTracks();
//										}
//									});
//								}
//							}
//						} catch (NullPointerException e) {
//							logger.warn(e.getMessage(), e);
//							if (uploadOnlyInWlan == true) {
//								if (mWifi.isConnected()) {
//									logger.info("Uploading tracks 3 ");
//									handler_upload.post(new Runnable() {
//
//										@Override
//										public void run() {
//											uploadTracks();
//										}
//									});
//								}
//							} else {
//								logger.info("Uploading tracks 4");
//								handler_upload.post(new Runnable() {
//
//									@Override
//									public void run() {
//										uploadTracks();
//									}
//								});
//							}
//						}
//					}
//				} else {
//					logger.info("automatic upload not wanted by user");
//				}
//			}
//		}, 0, 10, TimeUnit.MINUTES);
		
	}

//	/**
//	 * Helper method for the automatic upload of local tracks via the scheduler.
//	 */
//    private void uploadTracks() {
//        DbAdapter dbAdapter = DbAdapterImpl.instance();
//            try {
//                if (dbAdapter.getNumberOfLocalTracks() > 0
//                        && dbAdapter.getLastUsedTrack()
//                                .getNumberOfMeasurements() > 0) {
//                    UploadManager uploadManager = new UploadManager(
//                            application.getApplicationContext());
//                    uploadManager.uploadAllTracks();
//                } else {
//                	logger.info("Uploading does not make sense right now");
//                }
//            } catch (TracksException e) {
//            	logger.warn("Upload Failed!", e);
//            }
//        
//    }
	
	private void readSavedState(Bundle savedInstanceState) {
		if (savedInstanceState == null) return;
		
		this.serviceState = (ServiceState) savedInstanceState.getSerializable(SERVICE_STATE);
		this.trackMode = savedInstanceState.getInt(TRACK_MODE);
		
		BackgroundServiceImpl.requestServiceStateBroadcast(getApplicationContext());
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		outState.putSerializable(SERVICE_STATE, serviceState);
		outState.putInt(TRACK_MODE, trackMode);
	}

	protected void updateStartStopButton() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter != null && adapter.isEnabled()) { // was requirementsFulfilled
			try {
				switch (serviceState) {
				case SERVICE_STARTED:
					navDrawerItems[START_STOP_MEASUREMENT].setTitle(getResources().getString(R.string.menu_stop));
					switch (trackMode) {
					case TRACK_MODE_SINGLE:
						navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.track_mode_single));
						break;
					case TRACK_MODE_AUTO:
						navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.track_mode_auto));
						break;
					default:
						break;
					}
						
					navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.av_stop);
					navDrawerItems[START_STOP_MEASUREMENT].setEnabled(true);
					break;
				case SERVICE_STARTING:
					navDrawerItems[START_STOP_MEASUREMENT].setTitle(getResources().getString(R.string.menu_cancel));
					navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.menu_starting));
					navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.av_cancel);
					navDrawerItems[START_STOP_MEASUREMENT].setEnabled(true);
					break;
				case SERVICE_STOPPED:
					navDrawerItems[START_STOP_MEASUREMENT].setTitle(getResources().getString(R.string.menu_start));
					// Only enable start button when adapter is selected
	
					SharedPreferences preferences = PreferenceManager
							.getDefaultSharedPreferences(this);
	
					String remoteDevice = preferences.getString(
							org.envirocar.app.activity.SettingsActivity.BLUETOOTH_KEY,
							null);
	
					if (remoteDevice != null) {
						if(!CarManager.instance().isCarSet()){
							navDrawerItems[START_STOP_MEASUREMENT].setEnabled(false);
							navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.not_available);
							navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.no_sensor_selected));
						} else {
							navDrawerItems[START_STOP_MEASUREMENT].setEnabled(true);
							navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.av_play);
							navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(preferences.getString(SettingsActivity.BLUETOOTH_NAME, ""));
						}
					} else {
						navDrawerItems[START_STOP_MEASUREMENT].setEnabled(false);
						navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.not_available);
						navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.pref_summary_chose_adapter));
					}
					break;
				default:
					break;
				}
				
			} catch (NullPointerException e) {
				logger.warn(e.getMessage(), e);
				navDrawerItems[START_STOP_MEASUREMENT].setEnabled(false);
				navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.not_available);
			}
		} else {
			navDrawerItems[START_STOP_MEASUREMENT].setTitle(getResources().getString(R.string.menu_start));
			navDrawerItems[START_STOP_MEASUREMENT].setSubtitle(getResources().getString(R.string.pref_bluetooth_disabled));
			navDrawerItems[START_STOP_MEASUREMENT].setEnabled(false);
			navDrawerItems[START_STOP_MEASUREMENT].setIconRes(R.drawable.not_available);
		}
		
		navDrawerAdapter.notifyDataSetChanged();
	}


	private class NavAdapter extends BaseAdapter {
		

		@Override
		public boolean isEnabled(int position) {
			//to allow things like start bluetooth or go to settings from "disabled" action
			return (position == START_STOP_MEASUREMENT ? true : navDrawerItems[position].isEnabled());
		}
		
		@Override
		public int getCount() {
			return navDrawerItems.length;
		}

		@Override
		public Object getItem(int position) {
			return navDrawerItems[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			NavMenuItem currentItem = ((NavMenuItem) getItem(position));
			View item;
			if(currentItem.getSubtitle().equals("")){
				item = View.inflate(MainActivity.this,R.layout.nav_item_1, null);
				
			} else {
				item = View.inflate(MainActivity.this,R.layout.nav_item_2, null);
				TextView textView2 = (TextView) item.findViewById(android.R.id.text2);
				textView2.setText(currentItem.getSubtitle());
				if(!currentItem.isEnabled()) textView2.setTextColor(Color.GRAY);
			}
			ImageView icon = ((ImageView) item.findViewById(R.id.nav_item_icon));
			icon.setImageResource(currentItem.getIconRes());
			TextView textView = (TextView) item.findViewById(android.R.id.text1);
			textView.setText(currentItem.getTitle());
			if(!currentItem.isEnabled()){
				textView.setTextColor(Color.GRAY);
				icon.setColorFilter(Color.GRAY);
			}
			TypefaceEC.applyCustomFont((ViewGroup) item, TypefaceEC.Raleway(MainActivity.this));
			return item;
		}

	}
	
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        openFragment(position);
    }

    private void openFragment(int position) {
        FragmentManager manager = getSupportFragmentManager();

        switch (position) {
        
        // Go to the dashboard
        
        case DASHBOARD:
        	
        	if(isFragmentVisible(DASHBOARD_TAG)){
            	break;
            }
        	Fragment dashboardFragment = getSupportFragmentManager().findFragmentByTag(DASHBOARD_TAG);
        	if (dashboardFragment == null) {
        		dashboardFragment = new DashboardFragment();
        	}
        	manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            manager.beginTransaction().replace(R.id.content_frame, dashboardFragment, DASHBOARD_TAG).commit();
            break;
            
        //Start the Login activity
            
        case LOGIN:
        	if(UserManager.instance().isLoggedIn()){
        		UserManager.instance().logOut();
    			ListTracksFragment listMeasurementsFragment = (ListTracksFragment) getSupportFragmentManager().findFragmentByTag("MY_TRACKS");
    			// check if this fragment is initialized
    			if (listMeasurementsFragment != null) {
    				listMeasurementsFragment.clearRemoteTracks();
    			}else{
    				//the remote tracks need to be removed in any case
            		DbAdapterImpl.instance().deleteAllRemoteTracks();
    			}
        		Crouton.makeText(this, R.string.bye_bye, Style.CONFIRM).show();
        	} else {
            	if(isFragmentVisible(LOGIN_TAG)){
                	break;
                }
                LoginFragment loginFragment = new LoginFragment();
                manager.beginTransaction().replace(R.id.content_frame, loginFragment, LOGIN_TAG).addToBackStack(null).commit();
        	}
            break;
            
        // Go to the settings
            
        case SETTINGS:
			Intent configIntent = new Intent(this, SettingsActivity.class);
			startActivity(configIntent);
            break;
            
        // Go to the track list
            
        case MY_TRACKS:
        	
        	if(isFragmentVisible(MY_TRACKS_TAG)){
            	break;
            }
            ListTracksFragment listMeasurementFragment = new ListTracksFragment();
            manager.beginTransaction().replace(R.id.content_frame, listMeasurementFragment, MY_TRACKS_TAG).addToBackStack(null).commit();
            break;
            
        // Start or stop the measurement process
            
		case START_STOP_MEASUREMENT:
			if (!navDrawerItems[position].isEnabled()) return;
			
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

			String remoteDevice = preferences.getString(org.envirocar.app.activity.SettingsActivity.BLUETOOTH_KEY,null);

			BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
			if (adapter != null && adapter.isEnabled() && remoteDevice != null) {
				if(!CarManager.instance().isCarSet()){
			        MyGarage garageFragment = new MyGarage();
			        getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, garageFragment).addToBackStack(null).commit();
				} else {
					switch (serviceState) {
					case SERVICE_STOPPED:
						createStartTrackDialog();
						break;
					case SERVICE_STARTING:
						application.stopConnection();
						Crouton.makeText(this, R.string.stop_connection, Style.INFO).show();
						break;
					case SERVICE_STARTED:
						createStopTrackDialog();
						break;
					default:
						break;
					}
				}
			} else {
				Intent settingsIntent = new Intent(this, SettingsActivity.class);
				startActivity(settingsIntent);
			}
			break;
		case HELP:
        	
        	if(isFragmentVisible(HELP_TAG)){
            	break;
            }
			HelpFragment helpFragment = new HelpFragment();
            manager.beginTransaction().replace(R.id.content_frame, helpFragment, HELP_TAG).addToBackStack(null).commit();
			break;
		case SEND_LOG:
        	
        	if(isFragmentVisible(SEND_LOG_TAG)){
            	break;
            }
			SendLogFileFragment logFragment = new SendLogFileFragment();
			manager.beginTransaction().replace(R.id.content_frame, logFragment, SEND_LOG_TAG).addToBackStack(null).commit();
        default:
            break;
        }
        drawer.closeDrawer(drawerList);

    }

    private void createStopTrackDialog() {
    	Intent intent;
		switch (trackMode) {
		case TRACK_MODE_SINGLE:
			DialogUtil.createTitleMessageDialog(
					R.string.finish_track,
					R.string.finish_track_long,
					new DialogUtil.PositiveNegativeCallback() {
						@Override
						public void positive() {
							application.stopConnection();
							application.finishTrack();
						}
						
						@Override
						public void negative() {
						}
					}, MainActivity.this);	
			break;
		case TRACK_MODE_AUTO:
			/*
			 * TODO DIALOG!!
			 */
			intent = new Intent(DeviceInRangeService.STATE_CHANGE);
			intent.putExtra(DeviceInRangeService.STATE_CHANGE, false);
			sendBroadcast(intent);
			break;
		default:
			Crouton.makeText(MainActivity.this, "not supported", Style.INFO).show();
			break;
		}
    	
	}

	private void createStartTrackDialog() {
    	String[] items = new String[] {getResources().getString(R.string.track_mode_single),
				getResources().getString(R.string.track_mode_auto)};
		DialogUtil.createSingleChoiceItemsDialog(
				getString(R.string.question_track_mode),
				items,
				new DialogCallback() {
					@Override
					public void itemSelected(int which) {
						Intent intent;
						switch (which) {
						case 0:
							application.startConnection();
							Crouton.makeText(MainActivity.this, R.string.start_connection, Style.INFO).show();
							break;
						case 1:
							application.startConnection();
							intent = new Intent(DeviceInRangeService.STATE_CHANGE);
							intent.putExtra(DeviceInRangeService.STATE_CHANGE, true);
							sendBroadcast(intent);
							Crouton.makeText(MainActivity.this, R.string.start_connection, Style.INFO).show();
							break;
						}
					}
					
					@Override
					public void cancelled() {
						
					}
				}, MainActivity.this);		
	}

	/**
     * This method checks, whether a Fragment with a certain tag is visible.
     * @param tag The tag of the Fragment.
     * @return True if the Fragment is visible, false if not.
     */
    public boolean isFragmentVisible(String tag){
        
    	Fragment tmpFragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(tmpFragment != null && tmpFragment.isVisible()){
        	logger.info("Fragment with tag: " + tag + " is already visible.");
        	return true;
        }
        return false;
    	
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// Close db connection

//		application.closeDb();

		// Remove the services etc.

//		application.destroyStuff();
		
		Crouton.cancelAllCroutons();
		
//		this.unregisterReceiver(application.getBluetoothChangeReceiver());

	}
	
	@Override
	protected void onResume() {
		super.onResume();
		drawer.closeDrawer(drawerList);
	    //first init
	    firstInit();
	    
	    application.setActivity(this);
	    
	    checkKeepScreenOn();
	    
		alwaysUpload = preferences.getBoolean(SettingsActivity.ALWAYS_UPLOAD, false);
        uploadOnlyInWlan = preferences.getBoolean(SettingsActivity.WIFI_UPLOAD, true);
	}


	private void checkKeepScreenOn() {
		if (preferences.getBoolean(SettingsActivity.DISPLAY_STAYS_ACTIV, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}		
	}

	/**
	 * Determine what the menu buttons do
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case android.R.id.home:
			if (drawer.isDrawerOpen(drawerList)) {
				drawer.closeDrawer(drawerList);
			} else {
				drawer.openDrawer(drawerList);
			}
			return true;
		}
		return false;
	}
	
	private void firstInit(){
		if(!preferences.contains("first_init")){
			drawer.openDrawer(drawerList);
			
			Editor e = preferences.edit();
			e.putString("first_init", "seen");
			e.putBoolean("pref_privacy", true);
			e.commit();
		}
	}

}
