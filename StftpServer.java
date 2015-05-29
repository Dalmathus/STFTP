import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class StftpServer
{
    public void startServer(String directory)
    {
        try {
            DatagramSocket ds = new DatagramSocket(40080);
            System.out.println("StftpServer on port " + ds.getLocalPort() + " Using: " + directory);
            
            for(;;) {
                byte[] buf = new byte[1472];
                DatagramPacket p = new DatagramPacket(buf, 1472);
                ds.receive(p);
                
                StftpServerWorker worker = new StftpServerWorker(p, directory);
                worker.start();
            }
        }
        catch(Exception e) {
            System.err.println("Exception: " + e);
        }
        
        return;
    }
    
    public static void main(String args[])
    {
        StftpServer d = new StftpServer();
        String directory = null;
        try{
            if (!args[0].isEmpty()) { directory = args[0]; }
            d.startServer(directory);
        }
        catch(Exception e){
            d.startServer(directory);
        }
        
    }
}
