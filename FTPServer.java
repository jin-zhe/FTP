import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class FTPServer {
	String[] args;
	Vector<String> dirList;
	String serverDirectoryPath = "./server-directory/";
	private static final Logger logger = Logger.getLogger(FTPServer.class.getName());
	
	// constructor
	public FTPServer(String[] commands){
		args = commands;
	}
	
	// returns sorted directory and file listings
	public String getDirList(){
		dirList = new Vector<String>();
		populateDirList("./server-directory");
		if (dirList.isEmpty()) dirList.add("---the server directory is empty---");
		else Collections.sort(dirList);
		
		String output = "";
		String newline = System.getProperty("line.separator");
		for (String i: dirList) output += (i + newline);
		return output.substring(0,output.length()-1);
	}
	// DFS and populate list of directories
	public void populateDirList(String dirPath){
        File[] subFilesList = (new File(dirPath)).listFiles();
        // if empty directory
        if (subFilesList==null || subFilesList.length==0) dirList.add(dirPath.split("./server-directory/")[1] + "/");
        else{
            for (File file : subFilesList){
            	String filePath = file.getPath();
                // if directory
            	if (file.isDirectory())	populateDirList(filePath);
            	// if file
                else dirList.add(filePath.split("./server-directory/")[1]);
            }
        }
	}
	
	public void run() throws IOException{
		int controlPortNum = Integer.parseInt(args[0]);
		int dataPortNum = (args.length==1)? controlPortNum+1: Integer.parseInt(args[1]);
		
		Handler fh = new FileHandler("serverExceptions.log", true);
		fh.setFormatter(new SimpleFormatter()); // parse from XML
		logger.addHandler(fh);
		logger.setLevel(Level.FINE);
		try{
			// Control Socket
			ServerSocket serverControlSocket = new ServerSocket(controlPortNum);
			// listens for new connections
			while(true){
				Socket clientControlSocket = serverControlSocket.accept();
				
				// control socket I/O
				BufferedReader controlSocketIn = new BufferedReader(new InputStreamReader(clientControlSocket.getInputStream()));
				PrintWriter controlSocketOut = new PrintWriter(clientControlSocket.getOutputStream(), true);

				if (controlSocketIn.readLine().equals("PASV")){
					ServerSocket serverDataSocket = new ServerSocket(dataPortNum);
					controlSocketOut.println("200 PORT " + InetAddress.getLocalHost().getHostAddress() + " " + dataPortNum);
					String[] arguments = controlSocketIn.readLine().split(" ");
					
					// DIR
					if (arguments[0].equals("DIR")){
						if (arguments.length==1){
							controlSocketOut.println("200 DIR COMMAND OK");
							Socket clientDataSocket = serverDataSocket.accept();
							DataOutputStream dataSocketOut = new DataOutputStream(clientDataSocket.getOutputStream());
							dataSocketOut.write(getDirList().getBytes());
							dataSocketOut.flush();
							dataSocketOut.close();
							controlSocketOut.println("200 OK");
							clientDataSocket.close();
						}
						else controlSocketOut.println("501 INVALID ARGUMENTS");
					}
					
					// GET <file_name>
					else if (arguments[0].equals("GET")){
						if (arguments.length==2){
							String filePath = serverDirectoryPath + arguments[1];
							File file = new File (filePath);
							if (!file.exists()) controlSocketOut.println("401 FILE NOT FOUND");
							else{
								controlSocketOut.println("200 GET COMMAND OK");
								Socket clientDataSocket = serverDataSocket.accept();
								BufferedInputStream bis = new BufferedInputStream (new FileInputStream(file));
								BufferedOutputStream dataSocketOut = new BufferedOutputStream(clientDataSocket.getOutputStream());
								int inputChar;
						        while ((inputChar = bis.read()) != -1) dataSocketOut.write(inputChar);
						        bis.close();
								controlSocketOut.println("200 OK");
						        dataSocketOut.close();
								clientDataSocket.close();
							}
						}
						else controlSocketOut.println("501 INVALID ARGUMENTS");
					}
					
					// PUT <file_path> [<the_directory_to_put_the_file>]
					else if (arguments[0].equals("PUT")){
						if (arguments.length==2 || arguments.length==3){
							controlSocketOut.println("200 PUT COMMAND OK");
							Socket clientDataSocket = serverDataSocket.accept();
							String fileName = (new File(arguments[1])).getName();
							// deduce path of file to be put
							String path = "";
							if (arguments.length==2) path = serverDirectoryPath;
							else if (arguments.length==3){
								// if already ending with "/"
								if (arguments[2].charAt(arguments[2].length()-1)=='/') path = serverDirectoryPath + arguments[2];
								// else append "/"
								else path = serverDirectoryPath + arguments[2] + "/";
							}
							// create new path for file if not exist
							File filePath = new File(path);
							if (!filePath.exists()) filePath.mkdir();
							// create file
							File file = new File(path + fileName);
							if (!file.exists()) file.createNewFile();
							
							BufferedInputStream dataSocketIn = new BufferedInputStream(clientDataSocket.getInputStream());
							BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
							int inputChar;
							while((inputChar = dataSocketIn.read()) != -1) bos.write(inputChar);
							bos.close();
							controlSocketOut.println("200 OK");
							dataSocketIn.close();
							clientDataSocket.close();
						}
						else controlSocketOut.println("501 INVALID ARGUMENTS");
					}
					
					// invalid commands
					else controlSocketOut.println("500 UNKNOWN COMMAND");
					
					serverDataSocket.close();
				}
				else controlSocketOut.println("500 UNKNOWN COMMAND");
				// close client
				controlSocketIn.close();
				controlSocketOut.close();
				clientControlSocket.close();
			}
		}
		catch (Exception e){
	         logger.log(Level.SEVERE, e.getMessage(), e);
	    }
		fh.flush();
	    fh.close();
	}
	
	public static void main(String[] args) throws IOException {
		FTPServer ftpServer = new FTPServer(args);
		ftpServer.run();
	}
}