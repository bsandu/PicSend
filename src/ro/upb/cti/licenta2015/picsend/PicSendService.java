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

	private static String TAG = "picsend";
	private static String FTAG = "SERVICE: ";
    private static String DEFAULT_ID_PREFIX = "picsend-";
    private static String SERVICE_TYPE = "_picsend._tcp.local.";
    private ServiceInfo serviceInfo;
    private PicSendServer myServer;
    private String devId;
    private WifiManager.MulticastLock lock;
    private JmDNS jmdns;
    public String dev_name = DEFAULT_ID_PREFIX;
//    private ServiceInfo[] devices;
    private Object syncLock = new Object();
    
    private ArrayList<String> devName = null;
    private ArrayList<InetAddress> devAddr = null;
    private ArrayList<Integer> devPort = null;
    
//    private ArrayList<devData> devices;
    
//    private class devData {
//    	private String devName;
//    	private InetAddress devAddr;
//    	private int devPort;
//    	
//    	public devData(String devName, InetAddress devAddr, int devPort) {
//    		this.devName = devName;
//    		this.devAddr = devAddr;
//    		this.devPort = devPort;
//    	}
//    }
    
    
    private myRunnable myFileObserver;
	private Thread myThread;
	private String path;
	public class myRunnable implements Runnable {

		private boolean running;
		private ArrayList<String> myFiles;
		
		public void terminate() {
			this.running = false;
		}
		
		@Override
        public void run() {
	        // TODO Auto-generated method stub
        	Log.d(TAG,FTAG + "\tFOBS: started");
        	this.running = true;
        	myFiles = new ArrayList<String>();
        	path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            		.toString() + "/Camera";
            File f = new File(path);
            File files[] = f.listFiles();
            for (int i=0; i<files.length; i++) {
            	myFiles.add(files[i].getName());
            }
            
	        while(running) {
	           try {
	        	   Thread.sleep(5000);
	        	   f = new File(path);
	        	   files = f.listFiles();
	        	   for (int i=0; i<files.length; i++) {
	        		   if(!myFiles.contains(files[i].getName())) {	        			   
	        			   myFiles.add(files[i].getName());
	        			   Log.d(TAG,FTAG + "\tFOBS: " + files[i].getName() + " was added");
	        			   
							synchronized(syncLock) {
								for(int j=0; j<devName.size(); j++) {
									Log.d(TAG,FTAG + "\tFOBS: Sending " + files[i].getName() + " to " + devName.get(j));
									final File fn = new File(path + "/" + files[i].getName());
									final InetAddress finalAddr = devAddr.get(j);
									final int finalPort = devPort.get(j);
							
									new AsyncTask<Void, Void, Void>() {
							        	String received = "";
							            @Override
							            protected Void doInBackground(Void... params) {
							                try {
							                    this.received = PicSendClient.send_file(devId,
							                        		fn,
							                        		finalAddr,
							                        		finalPort);
							                    return null;
							                } catch (IOException e) {
							                    Log.e(TAG, FTAG + "\tFOBS: Error in request:" + e.getMessage());
							                    return null;
							                }
							            }
									}.execute();
									
//									Log.d(TAG,"FOBS: Sent " + files[i].getName() + " to " + devName.get(i));
								}
							}
	        			   
	        		   }
	               }
	        	   
	           } catch (Exception e) {
	        	   Log.e(TAG,FTAG + "\tFOBS: ERROR - " + e.getMessage());
	        	   e.printStackTrace();
	           } 
	        }
        }
		
	}
    
    
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
                Log.i(TAG, FTAG + "My address is " + deviceIpAddress.getHostAddress());
                myServer = new PicSendServer(deviceIpAddress);
            } catch (IOException e) {
                Log.e(TAG, FTAG + "Error starting service " + e.getMessage());
            }

            lock = wifi.createMulticastLock(getClass().getName());
            lock.setReferenceCounted(true);
            lock.acquire();

            
            //Se porneste jmdns
            Log.d(TAG, FTAG + "Starting jmDNS service");
            try {
                jmdns = JmDNS.create(deviceIpAddress, deviceIpAddress.getHostName());
                jmdns.addServiceTypeListener(new ServiceTypeListener() {
                    @Override
                    public void serviceTypeAdded(ServiceEvent event) {
                        if (event.getType().equals(SERVICE_TYPE)) {
                            Log.d("TAG", FTAG + "Same service discovered");

                            jmdns.addServiceListener(event.getType(), new ServiceListener() {
                                @Override
                                public void serviceAdded(ServiceEvent serviceEvent) {
                                    Log.i(TAG, FTAG + "Service added " + serviceEvent.getInfo().toString());
                                }

                                @Override
                                public void serviceRemoved(ServiceEvent serviceEvent) {
                                    Log.i(TAG, FTAG + "Service removed " + serviceEvent.getInfo().toString());
                                    int index = devName.indexOf(serviceEvent.getName());
                                    if(index >= 0) {
                                    	synchronized (syncLock) {
	                                    	devName.remove(index);
	                                    	devAddr.remove(index);
	                                    	devPort.remove(index);
                                    	}
                                    }
                                }

                                @Override
                                public void serviceResolved(final ServiceEvent serviceEvent) {
                                    Log.i(TAG, FTAG + "Peer found " + serviceEvent.getInfo().toString());

                                    //Daca e vorba de alt device, ii trimit toate fisierele existente.
                                    if (!serviceEvent.getName().equals(devId)
                                    		&& !devName.contains(serviceEvent.getName())) {
                                    	Log.d(TAG,FTAG + "BINGO!" + serviceEvent.getName() + " with " + devId);
                                    	synchronized (syncLock) {
	                                    	devName.add(serviceEvent.getName());
	                                    	devAddr.add(serviceEvent.getInfo().getInetAddresses()[0]);
	                                    	devPort.add(serviceEvent.getInfo().getPort());
                                    	}
                                    	new AsyncTask<Void, Void, Void>() {
                                        	String received = "";
                                            @Override
                                            protected Void doInBackground(Void... params) {
                                                try {
//                                                    for (InetAddress i : serviceEvent.getInfo().getInet4Addresses()) {
//                                                        Log.d(TAG, FTAG + "Other peer is: " + i.getHostAddress());
//                                                    }
                                                    Log.d(TAG,FTAG + "Other peer details: " + 
                                                    		serviceEvent.getName().toString() +
                                                    		serviceEvent.getInfo().getInetAddresses()[0].toString() + 
                                                    		serviceEvent.getInfo().getPort());
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
                                                    Log.e(TAG, FTAG + "Error in request:" + e.getMessage());
                                                    return null;
                                                }
                                            }
                                    }.execute();
                                    }
                                }
                            });

                            jmdns.requestServiceInfo(event.getType(), event.getName());
                        }

                        Log.i(TAG, FTAG + "Service discovered: " + event.getType() + " : " + event.getName());
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
		synchronized (syncLock) {
			devName = new ArrayList<String>();
			devAddr = new ArrayList<InetAddress>();
			devPort = new ArrayList<Integer>();
		}
		
		Toast.makeText(this, "Service started!", Toast.LENGTH_LONG).show();
		//devId = DEFAULT_ID_PREFIX +	Settings.Secure.getString(getApplicationContext().getContentResolver(),Settings.Secure.ANDROID_ID).substring(0, 5);
		devId = DEFAULT_ID_PREFIX + android.os.Build.MODEL;
		Log.d(TAG,FTAG + "My name is " + devId);
		final String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/Camera";
		
		
		
		
//		FileObserver observer = new FileObserver(path) {
//
//		    @Override
//		    public void onEvent(int event, String file) {
//		    	//Daca un fisier a fost creat
//		        if (event == FileObserver.CREATE && !file.equals(".probe")) {
//		        	Log.d(TAG, "FILE OBSERVER: File created [" + path + file + "]");
////		        	try {
//	        		synchronized(syncLock) {
//	        			Log.d(TAG, "FILE OBSERVER: Entering critical section...");
//			        	for(int i=0; i<devName.size(); i++) {
//		        			Log.d(TAG,devName.get(i));
//		        			final File f = new File(path + "/" + file);
//		        			final InetAddress finalAddr = devAddr.get(i);
//		        			final int finalPort = devPort.get(i);
//
//		        			new AsyncTask<Void, Void, Void>() {
//                            	String received = "";
//                                @Override
//                                protected Void doInBackground(Void... params) {
//                                    try {
//                                        this.received = PicSendClient.send_file(devId,
//                                            		f,
//                                            		finalAddr,
//                                            		finalPort);
//                                        return null;
//                                    } catch (IOException e) {
//                                        Log.e(TAG, "Error in request:" + e.getMessage());
//                                        return null;
//                                    }
//                                }
//		        			}.execute();
//		        			
//		        			Log.d(TAG,"Sent " + file + " to " + devName.get(i));
//			        	}
//	        		}
////		        	} 
////		        	catch (Exception e) {
////		        		Log.d(TAG,"Something went bad...");
////		        	}
//		        }
//		    }
//		};
//
		Log.i(TAG, FTAG + "Starting fileobserver...");
//		observer.startWatching();
		myFileObserver = new myRunnable();
		myThread = new Thread(myFileObserver);
		myThread.start();
		Log.i(TAG,FTAG + "Starting resolver...");
		new ServiceResolver().execute();
		
        return START_STICKY;
	}

	@Override
	public void onDestroy(){
		super.onDestroy();
		myFileObserver.terminate();
		try {
			myThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		jmdns.unregisterAllServices();
		try {
			jmdns.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Toast.makeText(this, "Service stopped!", Toast.LENGTH_LONG).show();
	}

}
