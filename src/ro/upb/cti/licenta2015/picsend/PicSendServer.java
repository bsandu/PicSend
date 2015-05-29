package ro.upb.cti.licenta2015.picsend;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class PicSendServer {

    private static String TAG = "Server";
    private static String RESPONSE = "Pong";

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private boolean alive;

    public int listenPort() {
        if (serverSocket != null)
            return  serverSocket.getLocalPort();
        else
            throw new IllegalStateException("ServerSocket is null, probably not closed or not initialized");
    }

    public PicSendServer(final InetAddress bindAddress) throws IOException {
        serverSocket = new ServerSocket(0, 10, bindAddress);
        alive = true;
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (alive) {
                        clientSocket = serverSocket.accept();
                        Log.d(TAG, "Request received from: " + clientSocket.getRemoteSocketAddress().toString());
                        serve_file(clientSocket);
                    }
                    serverSocket.close();
                } catch (IOException ioe) {
                    Log.e(TAG, "Error in PongServer: " + ioe.getMessage());
                }
            }
        }).start();
    }
    
    public static void serve_file(Socket client) throws IOException {
    	DataInputStream inbound;
    	DataOutputStream outbound;
    	
    	try {
    		inbound = new DataInputStream(client.getInputStream());
            outbound = new DataOutputStream(client.getOutputStream());
    		
	    	String dev_name = inbound.readUTF();
	    	String filename = inbound.readUTF();
	    	String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/" + dev_name;
	    	File f = new File(path);
	    	File g = new File(path + "/" + filename);
	    	
	    	if(g.exists()) {
	    		return;
	    	}    	
	    	if(!(f.exists() && f.isDirectory())) {
	    		f.mkdir();
	    	}
	    	g.createNewFile();
	
	    	InputStream is = client.getInputStream();
	    	FileOutputStream fos = new FileOutputStream(g);
	    	BufferedOutputStream bos = new BufferedOutputStream(fos);
	    	
	    	byte buf[] = new byte[1024];
	    	int len = 0;
			while ((len = is.read(buf)) != -1){
			    bos.write(buf, 0, len);
			    bos.flush();
			}
			bos.close();
			fos.close();
			client.shutdownInput();
			outbound.writeUTF("File " + filename + " received!");
			client.shutdownOutput();
    	} finally {
    		client.close();
    	}
	}
    
    public void kill() {
        alive = false;
    }
	
}
