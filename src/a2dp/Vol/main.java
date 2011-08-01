package a2dp.Vol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

public class main extends Activity {

	public static Integer OldVol = 5;
	static AudioManager am = (AudioManager) null;
	static SeekBar VolSeek;
	static Button serv;
	boolean servrun = false;
	ListView lvl = null; // listview used on main screen for showing devices
	Vector<btDevice> vec = new Vector<btDevice>(); // vector of bluetooth devices
	private DeviceDB myDB; // database of device data stored in SQlite
	String activebt = null;
	private MyApplication application;
	SharedPreferences preferences;
	public static final String PREFS_NAME = "btVol";
	String[] lstring = null; // string array used for the listview
	ArrayAdapter<String> ladapt; // listview adapter
	btDevice btCon;
	static final int ENABLE_BLUETOOTH = 1;
	static final int RELOAD = 2;
	
	boolean carMode = false;
	boolean homeDock = false;
	private String a2dpDir = "";
	private static final String LOG_TAG = "A2DP_Volume";
	private static int resourceID =  android.R.layout.simple_list_item_1;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @Handles item selections for the options menu
	 */
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.Manage_data: // used to export the data
			this.myDB.getDb().close();
			Intent i = new Intent(getBaseContext(), ManageData.class);
			startActivityForResult(i, RELOAD);
			return true;

		case R.id.Exit:
			stopService(new Intent(a2dp.Vol.main.this, service.class));
			a2dp.Vol.main.this.finish();
			return true;

		case R.id.prefs: // set preferences
			Intent j = new Intent(a2dp.Vol.main.this, Preferences.class);
			startActivity(j);
			return true;

		case R.id.DelData: // clears the database of all devices and settings.
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.DeleteDataMsg)
					.setCancelable(false)
					.setPositiveButton(R.string.Yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									myDB.deleteAll();
									refreshList(loadFromDB());
								}
							})
					.setNegativeButton(R.string.No,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// put your code here
									dialog.cancel();
								}
							});
			AlertDialog alertDialog = builder.create();
			alertDialog.show();

			return true;

		case R.id.help: // launches help website
			String st = "http://code.google.com/p/a2dpvolume/wiki/Manual";
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(st)));
			return true;
		}
		return false;
	}
	
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		ComponentName comp = new ComponentName("a2dp.Vol", "main");
		PackageInfo pinfo;
		String ver = null;
		try {
			pinfo = getPackageManager()
					.getPackageInfo(comp.getPackageName(), 0);
			ver = pinfo.versionName;
		} catch (NameNotFoundException e) {
			Log.e(LOG_TAG, "error" + e.getMessage());
		}

		setTitle(getResources().getString(R.string.app_name) + " Version: "
				+ ver);
		// get "Application" object for shared state or creating of expensive
		// resources - like DataHelper
		// (this is not recreated as often as each Activity)
		this.application = (MyApplication) this.getApplication();

		preferences = PreferenceManager
				.getDefaultSharedPreferences(this.application);

		try {
			boolean local = preferences.getBoolean("useLocalStorage", false);
			if (local)
				a2dpDir = getFilesDir().toString();
			else
				a2dpDir = Environment.getExternalStorageDirectory()
						+ "/A2DPVol";

			File exportDir = new File(a2dpDir);

			if (!exportDir.exists()) {
				exportDir.mkdirs();
			}

			carMode = preferences.getBoolean("car_mode", true);
			homeDock = preferences.getBoolean("home_dock", false);
		} catch (Exception e2) {
			Log.e(LOG_TAG, "error" + e2.getMessage());
		}

		am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		final Button btn = (Button) findViewById(R.id.Button01);
		final SeekBar VolSeek = (SeekBar) findViewById(R.id.VolSeekBar);

		final Button locbtn = (Button) findViewById(R.id.Locationbtn);
		serv = (Button) findViewById(R.id.ServButton);

		// these 2 intents are sent from the service to inform us of the running
		// state
		IntentFilter filter3 = new IntentFilter("a2dp.vol.service.RUNNING");
		this.registerReceiver(sRunning, filter3);

		IntentFilter filter4 = new IntentFilter(
				"a2dp.vol.service.STOPPED_RUNNING");
		this.registerReceiver(sRunning, filter4);

		// this reciever is used to tell this main activity about devices connecting and disconnecting.
		IntentFilter filter5 = new IntentFilter("a2dp.Vol.main.RELOAD_LIST");
		this.registerReceiver(mReceiver5, filter5);

		IntentFilter filter6 = new IntentFilter("a2dp.vol.preferences.UPDATED");
		this.registerReceiver(mReceiver6, filter6);

		

		VolSeek.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));

		lstring = new String[] { "no data" };

		this.myDB = new DeviceDB(application);
		//this.myDB = application.getDeviceDB();

		try {
			if (myDB.getLength() < 1) {
				getBtDevices();
			}
		} catch (Exception e1) {
			Log.e(LOG_TAG, "error" + e1.getMessage());
		}

		this.ladapt = new ArrayAdapter<String>(application,
				resourceID, lstring);
		this.lvl = (ListView) findViewById(R.id.ListView01);
		this.lvl.setAdapter(ladapt);

		serv.setText(R.string.StartService);
		// start the service. The intent will report when the service has
		// started and toggle button text
		startService(new Intent(a2dp.Vol.main.this, service.class));
		servrun = true;

		// capture original media volume to be used when returning from
		// bluetooth connection
		if (OldVol < am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) {
			OldVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
		} else {
			OldVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		}

		// set the seek bar position for media volume
		VolSeek.setProgress(OldVol);

		// find bonded devices and load into the database and listview
		btn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				int test = getBtDevices();
				if (test > 0) {
					lstring = new String[test];
					for (int i = 0; i < test; i++) {
						lstring[i] = vec.get(i).toString();
					}
					refreshList(loadFromDB());
				}
			}
		});

		// This shows the details of the bluetooth device
		lvl.setOnItemLongClickListener(new OnItemLongClickListener() {
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {

				if (vec.isEmpty())
					return false;
				BluetoothAdapter mBTA = BluetoothAdapter.getDefaultAdapter();

				btDevice bt = new btDevice();
				bt = vec.get(position);
				BluetoothDevice btd = null;
				if (mBTA != null) {
					Set<BluetoothDevice> pairedDevices = mBTA
							.getBondedDevices();
					for (BluetoothDevice device : pairedDevices) {
						if (device.getAddress().equalsIgnoreCase(bt.mac)) {
							btd = device;
						}
					}
				}

				android.app.AlertDialog.Builder builder = new AlertDialog.Builder(
						a2dp.Vol.main.this);
				builder.setTitle(bt.toString());
				final String car = bt.toString();
				String mesg;
				if (btd != null) {
					mesg = bt.desc1 + "\n" + bt.mac + "\n";
					switch (btd.getBondState()) {
					case BluetoothDevice.BOND_BONDED:
						mesg += "Bonded = Bonded";
						break;
					case BluetoothDevice.BOND_BONDING:
						mesg += "Bonded = Bonding";
						break;
					case BluetoothDevice.BOND_NONE:
						mesg += "Bonded = None";
						break;
					case BluetoothDevice.ERROR:
						mesg += "Bonded = Error";
						break;
					}

					mesg += "\nClass = " + getBTClassDev(btd);
					mesg += "\nMajor Class = " + getBTClassDevMaj(btd);
					mesg += "\nService Classes = " + getBTClassServ(btd);
				} else {
					mesg = (String) getText(R.string.btNotOn);
				}

				builder.setMessage(mesg);
				builder.setPositiveButton("OK", null);
				builder.setNeutralButton(R.string.LocationString,
						new OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								File exportDir = new File(a2dpDir);

								if (!exportDir.exists())
									return;
								// String file =
								// "content://com.android.htmlfileprovider"
								String file = "file:///" + exportDir.getPath()
										+ "/" + car.replaceAll(" ", "_")
										+ ".html";
								String st = new String(file).trim();

								Uri uri = Uri.parse(st);
								Intent intent = new Intent();
								intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								intent.setAction(android.content.Intent.ACTION_VIEW);
								intent.setDataAndType(uri, "text");
								intent.setClassName("com.android.browser",
										"com.android.browser.BrowserActivity");
								try {
									startActivity(intent);
								} catch (Exception e) {
									// TODO Auto-generated catch block
									Toast.makeText(application, e.toString(),
											Toast.LENGTH_LONG).show();
									e.printStackTrace();
								}

							}
						});
				builder.show();

				return servrun;
			}
		});

		// display the selected item and allow editing
		lvl.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				if (vec.isEmpty())
					return;

				final btDevice bt = vec.get(position);
				final btDevice bt2 = myDB.getBTD(bt.mac);
				android.app.AlertDialog.Builder builder = new AlertDialog.Builder(
						a2dp.Vol.main.this);
				builder.setTitle(bt.toString());
				builder.setMessage(bt2.desc1 + "\n" + bt2.desc2 + "\n"
						+ bt2.mac + "\nConnected Volume: " + bt2.defVol
						+ "\nTrigger Volume: " + bt2.setV + "\nGet Location: "
						+ bt2.getLoc);
				builder.setPositiveButton(R.string.OK, null);
				builder.setNegativeButton(R.string.Delete,
						new OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								myDB.delete(bt2);
								refreshList(loadFromDB());
							}
						});
				builder.setNeutralButton(R.string.Edit, new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Intent i = new Intent(a2dp.Vol.main.this,
								EditDevice.class);
						i.putExtra("btd", bt.mac);
						startActivity(i);
					}
				});
				builder.show();
			}
		});

		// simple media volume adjusters. They also reset the default volume

		VolSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				OldVol = setVolume(progress, a2dp.Vol.main.this);

			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

		});

		locbtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Locationbtn();
			}
		});

		// long click opens the most accurate location
		locbtn.setOnLongClickListener(new View.OnLongClickListener() {

			public boolean onLongClick(View v) {
				try {
					byte[] buff = new byte[250];
					FileInputStream fs = openFileInput("My_Last_Location2");
					fs.read(buff);
					fs.close();
					String st = new String(buff).trim();
					Toast.makeText(a2dp.Vol.main.this, st, Toast.LENGTH_LONG)
							.show();
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(st)));
				} catch (FileNotFoundException e) {
					Toast.makeText(a2dp.Vol.main.this, R.string.NoData,
							Toast.LENGTH_LONG).show();
					Log.e(LOG_TAG, "error" + e.getMessage());
				} catch (IOException e) {
					Toast.makeText(a2dp.Vol.main.this, "Some IO issue",
							Toast.LENGTH_LONG).show();
					Log.e(LOG_TAG, "error" + e.getMessage());
				}
				return false;
			}
		});

		// toggle the service ON or OFF and change the button text to reflect
		// the new state
		serv.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {

				if (servrun) {
					stopService(new Intent(a2dp.Vol.main.this, service.class));
					// serv.setText(R.string.StartService);
					// servrun = false;
				} else {
					startService(new Intent(a2dp.Vol.main.this, service.class));
					// serv.setText(R.string.StopService);
					// servrun = true;
				}
			}
		});

		new CountDownTimer(2000, 1000) {

			public void onTick(long millisUntilFinished) {
				try {
					if (a2dp.Vol.service.run) {
						servrun = true;
						serv.setText(R.string.StopService);
					} else {
						servrun = false;
						serv.setText(R.string.StartService);
					}
				} catch (Exception x) {
					servrun = false;
					serv.setText(R.string.StartService);
					Log.e(LOG_TAG, "error" + x.getMessage());
				}
			}

			public void onFinish() {
				try {
					if (a2dp.Vol.service.run) {
						servrun = true;
						serv.setText(R.string.StopService);
					} else {
						servrun = false;
						serv.setText(R.string.StartService);
					}
				} catch (Exception x) {
					servrun = false;
					serv.setText(R.string.StartService);
					Log.e(LOG_TAG, "error" + x.getMessage());
				}
			}
		}.start();

		// load the list from the database
		refreshList(loadFromDB());
	}

	/**
	 * Retrieves the last stored location and sends it as a URL
	 */
	public void Locationbtn() {
		try {
			byte[] buff = new byte[250];
			FileInputStream fs = openFileInput("My_Last_Location");
			fs.read(buff);
			fs.close();
			String st = new String(buff).trim();
			Toast.makeText(a2dp.Vol.main.this, st, Toast.LENGTH_LONG).show();
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(st)));
		} catch (FileNotFoundException e) {
			Toast.makeText(a2dp.Vol.main.this, R.string.NoData,
					Toast.LENGTH_LONG).show();
			Log.e(LOG_TAG, "error" + e.getMessage());
		} catch (IOException e) {
			Toast.makeText(a2dp.Vol.main.this, "Some IO issue",
					Toast.LENGTH_LONG).show();
			Log.e(LOG_TAG, "error" + e.getMessage());
		}
	}

	// function to get all bonded audio devices, load into database, and load
	// the vector and listview.
	/**
	 * @return the number of devices listed
	 */
	private int getBtDevices() {
		int i = 0;
		vec.clear();

		// the section below is for testing only. Comment out before building
		// the application for use.
		/*
		 * btDevice bt3 = new btDevice(); bt3.setBluetoothDevice("Device 1",
		 * "Porsche", "00:22:33:44:55:66:77", 15); i = 1; btDevice btx =
		 * myDB.getBTD(bt3.mac); if(btx.mac == null) {
		 * a2dp.Vol.main.this.myDB.insert(bt3); vec.add(bt3); } else
		 * vec.add(btx);
		 * 
		 * btDevice bt4 = new btDevice();
		 * bt4.setBluetoothDevice("Motorola T605", "Jaguar",
		 * "33:44:55:66:77:00:22", 14); btDevice bty = myDB.getBTD(bt4.mac); i =
		 * 2; if(bty.mac == null) { a2dp.Vol.main.this.myDB.insert(bt4);
		 * vec.add(bt4); } else vec.add(bty);
		 * 
		 * List<String> names = this.myDB.selectAll(); StringBuilder sb = new
		 * StringBuilder(); sb.append("Names in database:\n"); for (String name
		 * : names) { sb.append(name + "\n"); } str2 += " " + i;
		 * refreshList(loadFromDB());
		 */
		// end of testing code

		if (carMode) {
			// add the car dock false device if car mode check is enabled
			btDevice fbt = new btDevice();
			fbt.setBluetoothDevice("Car Dock", "Car Dock", "1",
					am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
			btDevice fbt2 = myDB.getBTD(fbt.mac);
			if (fbt2.mac == null) {
				a2dp.Vol.main.this.myDB.insert(fbt);
				vec.add(fbt);
			} else
				vec.add(fbt2);

			refreshList(loadFromDB()); // make sure it is relisted
		}

		if (homeDock) {
			// add the home dock false device if car mode check is enabled
			btDevice fbt = new btDevice();
			fbt.setBluetoothDevice("Home Dock", "Home Dock", "2",
					am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
			btDevice fbt2 = myDB.getBTD(fbt.mac);
			if (fbt2.mac == null) {
				fbt.setGetLoc(false);
				a2dp.Vol.main.this.myDB.insert(fbt);
				vec.add(fbt);
			} else
				vec.add(fbt2);

			refreshList(loadFromDB()); // make sure it is relisted
		}

		BluetoothAdapter mBTA = BluetoothAdapter.getDefaultAdapter();

		if (mBTA == null) {
			Toast.makeText(application, R.string.NobtSupport, Toast.LENGTH_LONG)
					.show();
			return 0;
		}

		// If Bluetooth is not yet enabled, enable it
		if (!mBTA.isEnabled()) {
			Intent enableBluetooth = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, ENABLE_BLUETOOTH);
			// Now implement the onActivityResult() and wait for it to
			// be invoked with ENABLE_BLUETOOTH
			// onActivityResult(ENABLE_BLUETOOTH, result, enableBluetooth);
			return 0;
		}

		if (mBTA != null) {
			Set<BluetoothDevice> pairedDevices = mBTA.getBondedDevices();
			// If there are paired devices

			if (pairedDevices.size() > 0) {
				// Loop through paired devices
				for (BluetoothDevice device : pairedDevices) {
					// Add the name and address to an array adapter to show in a
					// ListView
					if (true) {
						btDevice bt = new btDevice();
						i++;
						bt.setBluetoothDevice(device, device.getName(), am
								.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
						btDevice bt2 = myDB.getBTD(bt.mac);

						if (bt2.mac == null) {
							myDB.insert(bt);
							vec.add(bt);
						} else
							vec.add(bt2);
					}
				}

			}
		}

		refreshList(loadFromDB());

		return i;
	}

	// Listen for results.
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// See which child activity is calling us back.

		if (resultCode == RESULT_OK) {
			switch (requestCode) {
			case ENABLE_BLUETOOTH:
				// This is the standard resultCode that is sent back if the
				// activity crashed or didn't doesn't supply an explicit result.
				if (resultCode == RESULT_CANCELED) {
					Toast.makeText(application, R.string.btEnableFail,
							Toast.LENGTH_LONG).show();
					refreshList(loadFromDB());
				} else {

					int test = getBtDevices();
					if (test > 0) {
						lstring = new String[test];
						for (int i = 0; i < test; i++) {
							lstring[i] = vec.get(i).toString();
						}
						refreshList(loadFromDB());
					}
				}
				break;
			case RELOAD:
				refreshList(loadFromDB());
				break;
			default:
				break;
			}
		}
	}

	// This function handles the media volume adjustments.
	/**
	 * @param inputVol
	 *            is the media volume to set to
	 * @param sender
	 *            is who called this function
	 * @return
	 */
	private static int setVolume(int inputVol, Context sender) {
		int outVol;
		if (inputVol < 0)
			inputVol = 0;
		if (inputVol > am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
			inputVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		am.setStreamVolume(AudioManager.STREAM_MUSIC, inputVol, 0);
		outVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);

		// Toast.makeText(sender, "Stored Volume:" + OldVol + "  New Volume:" +
		// outVol, Toast.LENGTH_LONG).show();
		return outVol;
	}

	@Override
	protected void onStop() {
		super.onStop();

		// We need an Editor object to make preference changes.
		// All objects are from android.context.Context
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();

		// Commit the edits!
		editor.commit();
	}

	// this is called to update the list from the database
	private void refreshList(int test) {
		if (test > 0) {
			lstring = new String[test];
			for (int i = 0; i < test; i++) {
				lstring[i] = vec.get(i).toString();
				if (btCon != null)
					if (vec.get(i).getMac().equalsIgnoreCase(btCon.getMac()))
						lstring[i] += " **";
			}
		} else {
			lstring = new String[] { "no data" };

			// Toast.makeText(this, "No data", Toast.LENGTH_LONG);
		}
		a2dp.Vol.main.this.lvl.setAdapter(new ArrayAdapter<String>(application,
				resourceID, lstring));
		a2dp.Vol.main.this.lvl.invalidateViews();
		a2dp.Vol.main.this.lvl.forceLayout();
	}

	// this just loads the bluetooth device array from the database
	private int loadFromDB() {
		myDB.getDb().close();
		if (!myDB.getDb().isOpen())
			//this.myDB = application.getDeviceDB();
			myDB = new DeviceDB(application);

		vec = myDB.selectAlldb();
		if (vec.isEmpty() || vec == null)
			return 0;

		return vec.size();
	}


	/**
	 * received the reload list intent
	 */
	private final BroadcastReceiver mReceiver5 = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context2, Intent intent2) {
			String str = intent2.getExtras().getString("device");
			if (str != null && str.length() > 0)
				btCon = myDB.getBTD(str);
			else
				btCon = null;

			refreshList(loadFromDB());
			// Toast.makeText(context2, "mReceiver5", Toast.LENGTH_LONG).show();
		}
	};

	/**
	 * preferences have changed, reload new
	 */
	private final BroadcastReceiver mReceiver6 = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context2, Intent intent2) {
			try {
				carMode = preferences.getBoolean("car_mode", true);
				homeDock = preferences.getBoolean("home_dock", true);
				boolean local = preferences
						.getBoolean("useLocalStorage", false);
				if (local)
					a2dpDir = getFilesDir().toString();
				else
					a2dpDir = Environment.getExternalStorageDirectory()
							+ "/A2DPVol";

				File exportDir = new File(a2dpDir);

				if (!exportDir.exists()) {
					exportDir.mkdirs();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
				Log.e(LOG_TAG, "error" + e2.getMessage());
			}

			// Toast.makeText(context2, "mReceiver5", Toast.LENGTH_LONG).show();
		}
	};

	private final BroadcastReceiver sRunning = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// toggle the service button depending on the state of the service
			try {
				if (a2dp.Vol.service.run) {
					servrun = true;
					serv.setText(R.string.StopService);
				} else {
					servrun = false;
					serv.setText(R.string.StartService);
				}
			} catch (Exception x) {
				x.printStackTrace();
				servrun = false;
				serv.setText(R.string.StartService);
				Log.e(LOG_TAG, "error" + x.getMessage());
			}

		}

	};

	// Returns the bluetooth services supported as a string
	private String getBTClassServ(BluetoothDevice btd) {
		String temp = "";
		if (btd == null)
			return temp;
		if (btd.getBluetoothClass().hasService(BluetoothClass.Service.AUDIO))
			temp = "Audio, ";
		if (btd.getBluetoothClass()
				.hasService(BluetoothClass.Service.TELEPHONY))
			temp += "Telophony, ";
		if (btd.getBluetoothClass().hasService(
				BluetoothClass.Service.INFORMATION))
			temp += "Information, ";
		if (btd.getBluetoothClass().hasService(
				BluetoothClass.Service.LIMITED_DISCOVERABILITY))
			temp += "Limited Discoverability, ";
		if (btd.getBluetoothClass().hasService(
				BluetoothClass.Service.NETWORKING))
			temp += "Networking, ";
		if (btd.getBluetoothClass().hasService(
				BluetoothClass.Service.OBJECT_TRANSFER))
			temp += "Object Transfer, ";
		if (btd.getBluetoothClass().hasService(
				BluetoothClass.Service.POSITIONING))
			temp += "Positioning, ";
		if (btd.getBluetoothClass().hasService(BluetoothClass.Service.RENDER))
			temp += "Render, ";
		if (btd.getBluetoothClass().hasService(BluetoothClass.Service.CAPTURE))
			temp += "Capture, ";
		// trim off the extra comma and space
		if (temp.length() > 5)
			temp = temp.substring(0, temp.length() - 2);
		// return the list of supported service classes
		return temp;
	}

	// Get the bluetooth device classes we care about most. Not an exhaustive
	// list.
	/**
	 * @param btd
	 *            is the BluetoothDevice to check
	 * @return a list of the bluetooth services this device supports
	 */
	private String getBTClassDev(BluetoothDevice btd) {
		String temp = "";
		if (btd == null)
			return temp;
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO)
			temp = "Car Audio, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
			temp += "Handsfree, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
			temp += "Headphones, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO)
			temp += "HiFi Audio, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER)
			temp += "Loudspeaker, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO)
			temp += "Portable Audio, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER)
			temp += "Camcorder, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX)
			temp += "Set Top Box, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER)
			temp += "A/V Display/Speaker, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR)
			temp += "Video Monitor, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_VCR)
			temp += "VCR, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_CELLULAR)
			temp += "Cellular Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART)
			temp += "Smart Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_CORDLESS)
			temp += "Cordless Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_ISDN)
			temp += "ISDN Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY)
			temp += "Phone Modem/Gateway, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_UNCATEGORIZED)
			temp += "Other Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET)
			temp += "Wearable Headset, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED)
			temp += "Uncategorized A/V, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_UNCATEGORIZED)
			temp += "Uncategorized Phone, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.TOY_UNCATEGORIZED)
			temp += "Incategorized Toy, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_DESKTOP)
			temp += "Desktop PC, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA)
			temp += "Handheld PC, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_LAPTOP)
			temp += "Laptop PC, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA)
			temp += "Palm Sized PC/PDA, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_WEARABLE)
			temp += "Wearable PC, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_SERVER)
			temp += "Server PC, ";
		if (btd.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_UNCATEGORIZED)
			temp += "Computer, ";
		// trim off the extra comma and space. If the class was not found,
		// return other.
		if (temp.length() > 3)
			temp = temp.substring(0, temp.length() - 2);
		else
			temp = "other";

		// return device class
		return temp;
	}

	// Get the bluetooth major device classes we care about most. Not an
	// exhaustive list.
	/**
	 * @param btd
	 *            the bluetooth device to test.
	 * @return the major bluetooth device type
	 */
	private String getBTClassDevMaj(BluetoothDevice btd) {
		String temp = "";
		if (btd == null)
			return temp;
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO)
			temp = "Audio Video, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER)
			temp += "Computer, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.HEALTH)
			temp += "Health, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.MISC)
			temp += "Misc, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.NETWORKING)
			temp += "Networking, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL)
			temp += "Peripheral, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE)
			temp += "Phone, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.UNCATEGORIZED)
			temp += "Uncategorized, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE)
			temp += "Wearable, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.TOY)
			temp += "Toy, ";
		if (btd.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.IMAGING)
			temp += "Imaging, ";

		// trim off the extra comma and space. If the class was not found,
		// return other.
		if (temp.length() >= 3)
			temp = temp.substring(0, temp.length() - 2);
		else
			temp = "other";

		// return device class
		return temp;
	}
}