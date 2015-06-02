package ro.upb.cti.licenta2015.picsend;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import javax.jmdns.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;






public class PicSendService extends Service {

	private static String TAG = "JmDNS App";
    private static String DEFAULT_ID_PREFIX = "picsend-";
    private static String SERVICE_TYPE = "_picsend._tcp.local.";
    private ServiceInfo serviceInfo;
    private PicSendServer myServer;
    private String devId;
    private WifiManager.MulticastLock lock;
    private JmDNS jmdns;
    public String dev_name = DEFAULT_ID_PREFIX;
    private ServiceInfo[] devices;
    
    
    private class ServiceResolver extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            WifiManager wifi = (android.net.wifi.WifiManager)
                    getSystemService(android.content.Context.WIFI_SERVICE);
            InetAddress deviceIpAddress = null;

            try {
                deviceIpAddress = InetAddress.getByAddress(
                        ByteBuffer.allocate(4).putInt(
                                Integer.reverseBytes(wifi.getConnectionInfo().getIpAddress())).array());

                if (deviceIpAddress == null)
                    throw new IOException("No IP address can be found");
                Log.i(TAG, "My address is " + deviceIpAddress.getHostAddress());
                myServer = new PicSendServer(deviceIpAddress);
            } catch (IOException e) {
                Log.e(TAG, "Error starting service " + e.getMessage());
            }

            lock = wifi.createMulticastLock(getClass().getName());
            lock.setReferenceCounted(true);
            lock.acquire();

            
            //Se porneste jmdns
            Log.d(TAG, "Starting jmDNS service");
            try {
                jmdns = JmDNS.create(deviceIpAddress, deviceIpAddress.getHostName());
                jmdns.addServiceTypeListener(new ServiceTypeListener() {
                    @Override
                    public void serviceTypeAdded(ServiceEvent event) {
                        if (event.getType().equals(SERVICE_TYPE)) {
                            Log.d("TAG", "Same service discovered");

                            jmdns.addServiceListener(event.getType(), new ServiceListener() {
                                @Override
                                public void serviceAdded(ServiceEvent serviceEvent) {
                                    Log.i(TAG, "Service added " + serviceEvent.getInfo().toString());
                                }

                                @Override
                                public void serviceRemoved(ServiceEvent serviceEvent) {
                                    Log.i(TAG, "Service removed " + serviceEvent.getInfo().toString());
                                }

                                @Override
                                public void serviceResolved(final ServiceEvent serviceEvent) {
                                    Log.i(TAG, "Peer found " + serviceEvent.getInfo().toString());

                                    //Daca e vorba de alt device, ii trimit toate fisierele existente.
                                    if (!serviceEvent.getName().equals(devId)) {
                                    	Log.d("RESOLVER","BINGO!" + serviceEvent.getName() + " with " + devId);
                                    	new AsyncTask<Void, Void, Void>() {
                                        	String received = "";
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
                                                    for (InetAddress i : serviceEvent.getInfo().getInet4Addresses()) {
                                                        Log.d(TAG, "Other peer is: " + i.getHostAddress());
                                                    }
                                                    //Aflu path-ul camerei
                                                    String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                                                    		.toString() + "/Camera";
                                                    File f = new File(path);
                                                    File files[] = f.listFiles();
                                                    //Trimit toate pozele
                                                    for(int i=0; i<files.length; i++) {
                                                        this.received = PicSendClient.send_file(devId,
                                                        		files[i],
                                                        		serviceEvent.getInfo().getInetAddresses()[0],
                                                        		serviceEvent.getInfo().getPort());
                                                    }
                                                    return null;
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Error in request:" + e.getMessage());
                                                    return null;
                                                }
                                            }
                                    }.execute();
                                    }
                                }
                            });

                            jmdns.requestServiceInfo(event.getType(), event.getName());
                        }

                        Log.i(TAG, "Service discovered: " + event.getType() + " : " + event.getName());
                    }

                    @Override
                    public void subTypeForServiceTypeAdded(ServiceEvent ev) {}
                });

                serviceInfo = ServiceInfo.create(SERVICE_TYPE, devId, myServer.listenPort(), "picsend");
                jmdns.registerService(serviceInfo);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Toast.makeText(this, "Service started!", Toast.LENGTH_LONG).show();
		devId = DEFAULT_ID_PREFIX + Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID).substring(0, 5);
		final String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Camera";
		FileObserver observer = new FileObserver(path) {

		    @Override
		    public void onEvent(int event, String file) {
		    	//Daca un fisier a fost creat
		        if (event == FileObserver.CREATE && !file.equals(".probe")) {
		        	Log.d("SERVICE", "File created [" + path + file + "]");
		        	try {
			        	devices = jmdns.list(SERVICE_TYPE);
			        	//Aflu ce alte device-uri exista in momentul de fata
			        	for(int i=0; i<devices.length; i++) {
			        		if(!devices[i].getName().equals(devId)) {
			        			
			        			Log.d("IMPORTANT",devices[i].getName());
			        			final File f = new File(path + "/" + file);
			        			final ServiceInfo device = devices[i];
			        			//Trimit fisierul spre acele device-uri
			        			new AsyncTask<Void, Void, Void>() {
                                	String received = "";
                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        try {
                                            this.received = PicSendClient.send_file(devId,
                                                		f,
                                                		device.getInetAddresses()[0],
                                                		device.getPort());
                                            return null;
                                        } catch (IOException e) {
                                            Log.e(TAG, "Error in request:" + e.getMessage());
                                            return null;
                                        }
                                    }
			        			}.execute();
			        			
			        			Log.d("IMPORTANT","Sent " + file + " to " + devices[i].getName());
			        		}
			        	}
		        	} catch (Exception e) {
		        		Log.d("IMPORTANT","Something went bad...");
		        	}
		        }
		    }
		};
		observer.startWatching();
		new ServiceResolver().execute();
		
        return START_STICKY;
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		Toast.makeText(this, "Service stopped!", Toast.LENGTH_LONG).show();
	}

}
