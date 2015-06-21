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

public class StartScreenActivity extends Activity {

	private static String TAG = "picsend";
	private static String FTAG = "ACTIVITY: ";
	private boolean isServiceRunning = false;
	
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
		if(isServiceRunning) {
			toggle.setText("Stop");
		} else {
			toggle.setText("Start");
		}
		
		toggle.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(isServiceRunning == false) {
					startService(new Intent(getBaseContext(), PicSendService.class));
					isServiceRunning = true;
					((Button)v.findViewById(R.id.button_toggle)).setText("Stop");
				} else {
					stopService(new Intent(getBaseContext(), PicSendService.class));
					isServiceRunning = false;
					((Button)v.findViewById(R.id.button_toggle)).setText("Start");
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
