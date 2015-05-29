import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class StftpClient {
    
    private static void storeLong(byte[] array, int off, long val)
    {
        array[off + 0] = (byte)((val & 0xff00000000000000L) >> 56);
        array[off + 1] = (byte)((val & 0x00ff000000000000L) >> 48);
        array[off + 2] = (byte)((val & 0x0000ff0000000000L) >> 40);
        array[off + 3] = (byte)((val & 0x000000ff00000000L) >> 32);
        array[off + 4] = (byte)((val & 0x00000000ff000000L) >> 24);
        array[off + 5] = (byte)((val & 0x0000000000ff0000L) >> 16);
        array[off + 6] = (byte)((val & 0x000000000000ff00L) >>  8);
        array[off + 7] = (byte)((val & 0x00000000000000ffL));
        return;
    }
    
    private static long extractLong(byte[] array, int off)
    {
        long a = array[off+0] & 0xff;
        long b = array[off+1] & 0xff;
        long c = array[off+2] & 0xff;
        long d = array[off+3] & 0xff;
        long e = array[off+4] & 0xff;
        long f = array[off+5] & 0xff;
        long g = array[off+6] & 0xff;
        long h = array[off+7] & 0xff;
        return (a<<56 | b<<48 | c<<40 | d<<32 | e<<24 | f<<16 | g<<8 | h);
    }
    
    // Creates a client connects it to a server at a given address then downloads the file of the given name and creates the file on your own system
    public static void main(String[] args) throws IOException
    {
        byte[] REQpacket = new byte[1472];
        byte[] Datapacket = new byte[1472];
        byte[] Ackpacket = new byte[9];
        InetAddress serverIP = InetAddress.getByName(args[0]);
        int serverPort = 40080;
        long AckVal = 0;
        long FileSize = 0;
        String FileName = null;
        REQpacket[0] = 3;
        Ackpacket[0] = 42;
        
        String fileNameGiven = args[1] + '\u0000';
        byte[] fileName = fileNameGiven.getBytes();
        int j = 0;
        for (j = 0; j < fileName.length; j++){
            REQpacket[j+1] = fileName[j];
        }
        
        DatagramSocket ds = new DatagramSocket();
        DatagramPacket req = new DatagramPacket(REQpacket, REQpacket.length, serverIP, serverPort);
        DatagramPacket rc = new DatagramPacket(Datapacket, Datapacket.length);
        ds.send(req);
        
        try{
            FileName = args[2];
        }
        catch(Exception e){
            FileName = "NewFile";
        }
        
        FileOutputStream fOutput = new FileOutputStream(FileName);
        System.out.println(serverIP.toString());
        
        while(true){
            ds.receive(rc);
            if (Datapacket[0] == 0){
                System.out.println("NOTOK Bad packet");
                ds.close();
                fOutput.close();
                return;
            }
            if (Datapacket[0] == 1){
                FileSize = extractLong(Datapacket, 1);
                System.out.println("Filesize: " + FileSize + " - Commencing transfer.");
            }
            if (Datapacket[0] == 2){
                AckVal = extractLong(Datapacket, 1);
                fOutput.write(Datapacket, 9, rc.getLength() - 9);
                byte[] AckPacket = new byte[9];
                AckPacket[0] = 42;
                AckVal = AckVal + (rc.getLength() - 9);
                System.out.print("\r" + AckVal + "/" + FileSize);
                storeLong(AckPacket, 1, AckVal);
                DatagramPacket sn = new DatagramPacket(AckPacket, AckPacket.length, rc.getAddress(), rc.getPort());
                ds.send(sn);
                if (AckVal == FileSize){
                    System.out.println("\nFile Download Complete");
                    fOutput.close();
                    ds.close();
                    return;
                }
            }
        }
    }
}
