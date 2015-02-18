import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;


public class FTPClient {

	public static void main(String[] args) throws IOException{
		String clientDirectoryPath = "./client-directory/";
		Vector<String> serverResponses = new Vector<String>();
		
		if (args.length>=3 && args.length<=5){
			String serverAddress = args[0];
			int controlPortNum = Integer.parseInt(args[1]);
			String method = args[2];
			
			Socket controlSocket = new Socket(serverAddress, controlPortNum);
			// control socket I/O
			BufferedReader controlSocketIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
			PrintWriter controlSocketOut = new PrintWriter(controlSocket.getOutputStream(), true);

			// establish control channel
			controlSocketOut.println("PASV");
			String controlResponse = controlSocketIn.readLine();
			serverResponses.add(controlResponse);	// log
			System.out.println(controlResponse);
			
			// if control channel successfully established
			if (controlResponse.contains("200 PORT")){
				// establish data channel
				String[] responseToken = controlResponse.split(" ");
				String address = responseToken[2];
				int dataPortNum = Integer.parseInt(responseToken[3]);
				
				// make server command
				String arguments = "";
				for (int i=2; i<args.length; i++) arguments += args[i] + " ";
				controlSocketOut.println(arguments.substring(0,arguments.length()-1));
				
				String responseInitial = controlSocketIn.readLine();
				serverResponses.add(responseInitial);	// log
				System.out.println(responseInitial);
				
				// DIR
				if (method.equals("DIR")){
					if (responseInitial.split(" ")[0].equals("200")){
						Socket dataSocket = new Socket(address, dataPortNum);
						BufferedInputStream dataSocketIn = new BufferedInputStream(dataSocket.getInputStream());
						
						// directory-listing to be placed in client-directory
						File directoryListing = new File(clientDirectoryPath + "directory_listing");
				        if (!directoryListing.exists()) directoryListing.createNewFile();
						BufferedWriter bw = new BufferedWriter(new FileWriter(directoryListing));
						int inputChar;
				        while ((inputChar = dataSocketIn.read()) != -1) bw.write(inputChar);
						bw.flush();
				        bw.close();
						
						String responseFinal = controlSocketIn.readLine();
						serverResponses.add(responseFinal);	// log
						System.out.println(responseFinal);
						dataSocketIn.close();
						if (responseFinal.equals("200 OK")) dataSocket.close();
					}
				}
				
				// GET <file_name>
				else if (method.equals("GET")){
					if (responseInitial.split(" ")[0].equals("200")){
						Socket dataSocket = new Socket(address, dataPortNum);
						BufferedInputStream dataSocketIn = new BufferedInputStream (dataSocket.getInputStream());
						String fileName = (new File (args[3])).getName();
						File file = new File (clientDirectoryPath + fileName);
						if (!file.exists()) file.createNewFile();
						BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
						
						// write file
						int inputChar;
						while ((inputChar = dataSocketIn.read()) != -1) bos.write(inputChar);
						bos.close();
						
						String responseFinal = controlSocketIn.readLine();
						serverResponses.add(responseFinal);	// log
						System.out.println(responseFinal);
						dataSocketIn.close();
						if (responseFinal.equals("200 OK")) dataSocket.close();
					}
				}
				
				// PUT <file_path> [<the_directory_to_put_the_file>]
				else if (method.equals("PUT")){
					if (responseInitial.split(" ")[0].equals("200")){
						Socket dataSocket = new Socket(address, dataPortNum);
						File file = new File(clientDirectoryPath + args[3]);
						// if file does not exists
						if (!file.exists()){
							serverResponses.add("FILE NOT FOUND");
							System.out.println("FILE NOT FOUND");
						}
						// else put file on server
						else{
							BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
							BufferedOutputStream dataSocketOut = new BufferedOutputStream(dataSocket.getOutputStream());
							int inputChar;
							while((inputChar = bis.read()) != -1) dataSocketOut.write(inputChar);
							bis.close();
							dataSocketOut.close();
							
							String responseFinal = controlSocketIn.readLine();
							serverResponses.add(responseFinal);	// log
							System.out.println(responseFinal);
							if (responseFinal.equals("200 OK")) dataSocket.close();	// close data channel
						}
					}
				}
				
				// invalid commands
				else{
					controlSocketOut.println(method);
					String response = controlSocketIn.readLine();
					serverResponses.add(response);
					System.out.println(response);
				}
			}
			// close streams and sockets
			controlSocketIn.close();
			controlSocketOut.close();
			controlSocket.close();
		}
		
		else{
			serverResponses.add("501 INVALID ARGUMENTS");	// log
			System.out.println("501 INVALID ARGUMENTS");
		}
		
		// log all responses from server
		File log = new File("./log");
		if (!log.exists()) log.createNewFile();
		FileWriter fw = new FileWriter(log);
		fw.write(serverResponses.lastElement());
        fw.flush();
        fw.close();
	}
}