package ro.upb.cti.licenta2015.picsend;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class StartScreenActivity extends Activity {

	private static String TAG = "picsend";
	private static String FTAG = "ACTIVITY: ";
	private boolean isServiceRunning = false;
	
	public boolean isAlpha(String name) {
	    return name.matches("[a-zA-Z]+");
	}
	
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start_screen);
		isServiceRunning = isMyServiceRunning(PicSendService.class);
		Button toggle = (Button)findViewById(R.id.button_toggle);
		EditText editText_name = (EditText)findViewById(R.id.editText_name);
		EditText editText_room = (EditText)findViewById(R.id.editText_room);
		CheckBox checkBox_sync = (CheckBox)findViewById(R.id.checkBox_sync);
		if(isServiceRunning) {
			toggle.setText("Stop");
			toggle.setBackgroundResource(R.drawable.circle_stop);
			editText_name.setVisibility(View.INVISIBLE);
			editText_room.setVisibility(View.INVISIBLE);
			checkBox_sync.setVisibility(View.INVISIBLE);
		} else {
			toggle.setText("Start");
			toggle.setBackgroundResource(R.drawable.circle_start);
			editText_name.setVisibility(View.VISIBLE);
			editText_room.setVisibility(View.VISIBLE);
			checkBox_sync.setVisibility(View.VISIBLE);
		}
		
		toggle.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(isServiceRunning == false) {
					EditText editText_name = (EditText)findViewById(R.id.editText_name);
					EditText editText_room = (EditText)findViewById(R.id.editText_room);
					CheckBox checkBox_sync = (CheckBox)findViewById(R.id.checkBox_sync);
					
					if (editText_name.getText().toString().equals("")) {
						Toast.makeText(getBaseContext(), "Name cannot be empty!", Toast.LENGTH_SHORT).show();
						return;
					}
					if (!isAlpha(editText_name.getText().toString())) { 
						Toast.makeText(getBaseContext(), "Name should contain only letters!", Toast.LENGTH_SHORT).show();
						return;
					}
					if (editText_name.getText().toString().length() > 10) {
						Toast.makeText(getBaseContext(), "Name maximum length is 10 characters!", Toast.LENGTH_SHORT).show();
						return;
					}
					if (editText_room.getText().toString().equals("")) {
						Toast.makeText(getBaseContext(), "Room cannot be empty!", Toast.LENGTH_SHORT).show();
						return;
					}
					if (!isAlpha(editText_room.getText().toString())) {
						Toast.makeText(getBaseContext(), "Room should contain only letters!", Toast.LENGTH_SHORT).show();
						return;
					}
					if (editText_room.getText().toString().length() > 10) {
						Toast.makeText(getBaseContext(), "Room maximum length is 10 characters!", Toast.LENGTH_SHORT).show();
						return;
					}
					
					Intent mIntent = new Intent(getBaseContext(), PicSendService.class);
					mIntent.putExtra("name", editText_name.getText().toString());
					mIntent.putExtra("room", editText_room.getText().toString());
					mIntent.putExtra("sync", checkBox_sync.isChecked());
					startService(mIntent);
					isServiceRunning = true;
					((Button)v.findViewById(R.id.button_toggle)).setText("Stop");
					((Button)v.findViewById(R.id.button_toggle)).setBackgroundResource(R.drawable.circle_stop);
					Log.d(TAG,FTAG + editText_name.getText().toString());
					Log.d(TAG,FTAG + editText_room.getText().toString());
					Log.d(TAG,FTAG + checkBox_sync.isChecked());
					editText_name.setVisibility(View.INVISIBLE);
					editText_room.setVisibility(View.INVISIBLE);
					checkBox_sync.setVisibility(View.INVISIBLE);
					editText_name.setText("");
					editText_room.setText("");
					checkBox_sync.setChecked(false);
				} else {
					EditText editText_name = (EditText)findViewById(R.id.editText_name);
					EditText editText_room = (EditText)findViewById(R.id.editText_room);
					CheckBox checkBox_sync = (CheckBox)findViewById(R.id.checkBox_sync);
					stopService(new Intent(getBaseContext(), PicSendService.class));
					isServiceRunning = false;
					((Button)v.findViewById(R.id.button_toggle)).setText("Start");
					((Button)v.findViewById(R.id.button_toggle)).setBackgroundResource(R.drawable.circle_start);
					editText_name.setVisibility(View.VISIBLE);
					editText_room.setVisibility(View.VISIBLE);
					checkBox_sync.setVisibility(View.VISIBLE);
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.start_screen, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onStart() {
	    super.onStart();
	    isServiceRunning = isMyServiceRunning(PicSendService.class);
	}
	 
	@Override
	protected void onResume() {
	    super.onResume();
	    isServiceRunning = isMyServiceRunning(PicSendService.class);
	}
	
	@Override
	protected void onRestart() {
	    super.onRestart();
	    isServiceRunning = isMyServiceRunning(PicSendService.class);
	}
	
}
