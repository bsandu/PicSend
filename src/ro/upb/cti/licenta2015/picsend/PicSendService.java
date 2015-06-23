package ro.upb.cti.licenta2015.picsend;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import javax.jmdns.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PicSendService extends Service {

	private static String TAG = "picsend";
	private static String FTAG = "SERVICE: ";
    private static String DEFAULT_ID_PREFIX = "picsend_";
    private static String SERVICE_TYPE = "_picsend._tcp.local.";
    private ServiceInfo serviceInfo;
    private PicSendServer myServer;
    private String devId;
    private WifiManager.MulticastLock lock;
    private JmDNS jmdns;
    public String dev_name = DEFAULT_ID_PREFIX;
    private Object syncLock = new Object();
    
    private ArrayList<String> devName = null;
    private ArrayList<InetAddress> devAddr = null;
    private ArrayList<Integer> devPort = null;
    
    private myRunnable myFileObserver;
	private Thread myThread;
	private String path;
	
	private String name = "";
	private String room = "";
	private boolean sync = false;
	
	private void runAsForeground(String name, String room){
	    Intent notificationIntent = new Intent(this, StartScreenActivity.class);
	    PendingIntent pendingIntent=PendingIntent.getActivity(this, 0,
	            notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

	    Notification notification=new NotificationCompat.Builder(this)
	                                .setSmallIcon(R.drawable.ic_launcher)
	                                .setContentText(name + " is in room " + room)
	                                .setContentIntent(pendingIntent).build();

	    startForeground(12345, notification);
	}
	
	public class myRunnable implements Runnable {

		private boolean running;
		private ArrayList<String> myFiles;
		
		public void terminate() {
			this.running = false;
		}
		
		@Override
        public void run() {
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
	        	   if(!running) {
	        		   Log.d(TAG, FTAG + "Gracefully interrupted");
	        		   break;
	        	   }
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
							            @Override
							            protected Void doInBackground(Void... params) {
							                try {
							                    PicSendClient.send_file(devId,
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
								}
							}
	        			   
	        		   }
	               }
	        	   
	           } catch(InterruptedException ie) {
	        	   Log.d(TAG, FTAG + "Gracefully interrupted");
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
                                	String remName = serviceEvent.getName().toString();
                                	Log.i(TAG, FTAG + "Service removed " + remName);
                                	String[] remNameSplit = remName.split("_");
                                	if (remNameSplit.length != 4) {
                                		return;
                                	} else {
                                		if (!room.equals(remNameSplit[3])) {
                                			return;
                                		}
                                	}
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
                                	String addName = serviceEvent.getName().toString();
                                    Log.i(TAG, FTAG + "Peer found " + addName);
                                    String[] addNameSplit = addName.split("_");
                                    if (addNameSplit.length != 4) {
                                		return;
                                	} else {
                                		if (!room.equals(addNameSplit[3])) {
                                			Log.d(TAG,FTAG + "\t\t\t" + addName + " is not part of room " + room);
                                			return;
                                		}
                                	}

                                    //Daca e vorba de alt device, ii trimit toate fisierele existente.
                                    if (!serviceEvent.getName().equals(devId)
                                    		&& !devName.contains(serviceEvent.getName())) {
                                    	Log.d(TAG,FTAG + "BINGO!" + serviceEvent.getName() + " with " + devId);
                                    	synchronized (syncLock) {
	                                    	devName.add(serviceEvent.getName());
	                                    	devAddr.add(serviceEvent.getInfo().getInetAddresses()[0]);
	                                    	devPort.add(serviceEvent.getInfo().getPort());
                                    	}
                                    	if (sync == true) {
	                                    	new AsyncTask<Void, Void, Void>() {
	                                            @Override
	                                            protected Void doInBackground(Void... params) {
	                                                try {
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
	                                                        PicSendClient.send_file(devId,
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
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		if (intent !=null && intent.getExtras()!=null) {
		     name = intent.getExtras().getString("name");
		     room = intent.getExtras().getString("room");
		     sync = intent.getExtras().getBoolean("sync");
		}
		
		runAsForeground(name, room);
		synchronized (syncLock) {
			devName = new ArrayList<String>();
			devAddr = new ArrayList<InetAddress>();
			devPort = new ArrayList<Integer>();
		}
		
		Toast.makeText(this, "Service started!", Toast.LENGTH_LONG).show();
		//devId = DEFAULT_ID_PREFIX +	Settings.Secure.getString(getApplicationContext().getContentResolver(),Settings.Secure.ANDROID_ID).substring(0, 5);
		String model = android.os.Build.MODEL;
		model = model.replaceAll("[^A-Za-z0-9 ]", "");
		devId = DEFAULT_ID_PREFIX + model + "_" + name + "_" + room;
		Log.d(TAG,FTAG + "My name is " + devId);
		Log.i(TAG, FTAG + "Starting fileobserver...");
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
		try {
			stopForeground(true);
			myFileObserver.terminate();
			Thread.sleep(200);
			myThread.interrupt();
			myThread.join();
			jmdns.unregisterAllServices();
			jmdns.close();
		} catch (InterruptedException e) {
			Log.e(TAG, FTAG + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, FTAG + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			Log.e(TAG, FTAG + e.getMessage());
			e.printStackTrace();
		}
		Toast.makeText(this, "Service stopped!", Toast.LENGTH_LONG).show();
	}
}
