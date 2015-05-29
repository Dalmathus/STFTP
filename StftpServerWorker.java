import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;

class StftpServerWorker extends Thread
{
	// variables used through the program constants are OPcodes for packets rest as follows
    private DatagramPacket req; // the original req packet containing all network data
    private String directory;	// the working directory for the server
    InetAddress clientAddress;	// the clients IP address
    int clientPort;				// the clients port
    DatagramSocket ds;			// the server workers personal socket
    
    private static final int NOTOK   = 0;
    private static final int OK      = 1;
    private static final int DATA    = 2;
    private static final int REQ     = 3;
    
    // Creates a offset for any packet using a long
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
    
    // Extracts a offset from a packet
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
    
    private void sendfile(String filename)
    {
        /*
        * open the file using a FileInputStream and send it, one block at
        * a time, to the receiver.
        */
        try{
            
            // Create the FileImputStream
            FileInputStream fInput = new FileInputStream(filename);
            
            // Set the timeout of the socket
            ds.setSoTimeout(1000);
            
            // Construction of the NOTOKpacket
            byte[] notOk = {NOTOK};
            DatagramPacket notOkPacket = new DatagramPacket(notOk, notOk.length, clientAddress, clientPort);
            
            // Construction of the ACKrecieve packet
            byte[] ackRecievePacket = new byte[9];
            
            // Construction of the OKpacket
            byte[] okPacket = new byte[9];
            okPacket[0] = OK;
            long FileSize = fInput.available();
            storeLong(okPacket, 1, FileSize);
            DatagramPacket okDataPacket = new DatagramPacket(okPacket, okPacket.length, clientAddress, clientPort);
            ds.send(okDataPacket);
            
            // Construction of the DATApacket
            byte[] dataPacket = new byte[1472];
            dataPacket[0] = DATA;
            long Offset = 0;
            
            loop:
            while(fInput.available() >= 1){		// while the file still has bytes to read in
                
                storeLong(dataPacket, 1, Offset);	
                int totalRead = fInput.read(dataPacket, 9, dataPacket.length - 9);	// read in the data to a packet
                DatagramPacket dataDatapacket = new DatagramPacket(dataPacket, dataPacket.length, clientAddress, clientPort);
                
                sleep(3);	// sleep 3 milliseconds this is to prevent offset clashes on large files on local networks
                
                if (totalRead < 1463){	// if it is the last packet
                    dataDatapacket.setLength(totalRead +9);	//set the size of the packet to the total amount of bytes read in + the offset
                    if (totalRead == -1){	// if the last packet is exactly 1463 bytes big
                        dataDatapacket.setLength(9);	// set the packet to be jsut the offset
                    }
                    ds.send(dataDatapacket);
                }
                else{
                    ds.send(dataDatapacket);
                }
                
                for (int k = 0; k < 10; k++){	// loop for receiving ACK files this is to time out the server if something goes wrong
                    try {
                        Offset = Offset + totalRead; // get the new offset for the ack packet to compare to
                        
                        DatagramPacket ackPacket = new DatagramPacket(ackRecievePacket, ackRecievePacket.length, clientAddress, clientPort);
                        ds.receive(ackPacket);
                        
                        long AckSet = extractLong(ackPacket.getData(), 1);
                        
                        if (Offset != AckSet){
                            System.err.println("\n OffSet is equal to: " + Offset + " / AckVal is equal to: " + AckSet);
                            ds.send(notOkPacket);
                            return;
                        }
                        break;
                    }
                    
                    catch(SocketTimeoutException e){
                        ds.send(dataDatapacket);
                        if(k == 9) {
                            ds.send(notOkPacket);
                        break loop;}
                        continue;
                    }
                }
            }
            System.out.println("Terminating Connection, Transfer Complete\n");
            ds.close();
            fInput.close();
        }
        
        catch(FileNotFoundException e){
            System.out.println(e);
        }
        
        catch(IOException e){
            System.out.println(e);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        return;
    }
    
    public void run()
    {
        try{
            
            ds = new DatagramSocket();
            
            byte[] notOk = {NOTOK};
            
            byte[] bytePacket = req.getData();
            int OpCode = bytePacket[0];
            
            clientAddress = req.getAddress();
            clientPort = req.getPort();
            
            DatagramPacket notOkPacket = new DatagramPacket(notOk, notOk.length, clientAddress, clientPort);
            
            if (OpCode != REQ){
                ds.send(notOkPacket);
            }
            
            System.err.println("working");
            String Filename = new String(req.getData(), 1, req.getLength() - 1);
            System.err.println(Filename.trim());
            if (Filename.contains("chroot") || Filename.startsWith("/")){
                ds.send(notOkPacket);
                System.err.println("\nSECURITY WARNING! CONNECTION ATTEMPTING TO ACCESS ILLEGAL FILES");
                return;
            }
            Filename = directory + "/" + Filename.trim();
            sendfile(Filename);
            
        }
        catch(SocketException e){
            System.out.println(e);
        }
        catch(IOException e){
            System.out.println(e);
        }
    }
    public StftpServerWorker(DatagramPacket req, String path)
    {
        this.directory = path;
        this.req = req;
    }
}
