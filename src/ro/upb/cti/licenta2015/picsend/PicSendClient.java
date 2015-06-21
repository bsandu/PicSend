package ro.upb.cti.licenta2015.picsend;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

import android.util.Log;

public class PicSendClient {

	private static String TAG = "picsend";
	private static String FTAG = "\t\tCLIENT: ";

    public static String send_file(String dev_name, File f, InetAddress destination, int port) throws IOException {
    	
    	Socket socket = null;
        DataOutputStream writer;
        DataInputStream reader;
        String received;
        
        try {
        	socket = new Socket(destination, port);
        	reader = new DataInputStream(socket.getInputStream());
            writer = new DataOutputStream(socket.getOutputStream());
            
            writer.writeUTF(dev_name);
            writer.writeUTF(f.getName());
            
            received = reader.readUTF();
            
            Log.d(TAG,FTAG + dev_name + " is sending " + f.getName() + " to " + destination.toString());
            
            if (received.equals("NACK")) {
            	socket.shutdownInput();
            	socket.shutdownOutput();
            	Log.d(TAG,FTAG + destination.toString() + " denied the transfer");
            	return "File already exists on " + destination.toString();
            }
            
            OutputStream os = socket.getOutputStream();
            FileInputStream fis = new FileInputStream(f);
	    	BufferedInputStream bis = new BufferedInputStream(fis);
	    	
	    	byte buf[] = new byte[1024];
	    	int len = 0;
			while ((len = bis.read(buf)) != -1){
				os.write(buf, 0, len);
			    os.flush();
			}
			bis.close();
			fis.close();
			socket.shutdownOutput();
			received = reader.readUTF();
			return received;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }
    
    static String send_file(String dev_name, File f, String host, int port) throws IOException {
    	return send_file(dev_name, f, InetAddress.getByName(host), port);
    }
	
}
