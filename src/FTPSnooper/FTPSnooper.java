package FTPSnooper;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.Socket;


public class FTPSnooper {
	
	private Socket socket = null;
	private Socket socketPASV = null;
	private BufferedReader input = null;
	private BufferedReader inputPASV = null;
	private PrintWriter output = null;
	private String response = null;
	private String responsePASV = null;
	private int port = 21;
	private ArrayList<String> requiredList = new ArrayList<String>();
	
	// Hostname of the FTP server to connect to.
	private final String hostname;
	
	// Directory on server to analyze.
	private final String directory;
	
	// Pattern object for file regular expression.
    private final Pattern filePattern;
    
	// Results that are generated with filenames given as the keys
	// and 'first 20 lines of each file' given as the values.
	// See fetch() method for dummy example data of layout.
	private final HashMap<String, String> fileInfo = new HashMap<String, String>();
	
	public FTPSnooper(String hostname, String directory,
			String filenameRegularExpression) {
		this.hostname = hostname;
		this.directory = directory;
		this.filePattern = Pattern.compile(filenameRegularExpression);
	}
	
	/**
	 * Fetch the required file overviews from the FTP server.
	 * 
	 * @throws IOException
	 */	
	
	private void command(String string) throws IOException {
		if (socket == null) {
			throw new IOException("FTPSnooper is not connected");
		}
		output.write(string + "\r\n");
		output.flush();
	}
	
	private void response()  throws IOException{
		response = input.readLine();
	}
	
	public synchronized void connectAndLogIn(String hostname,int port, String user, String password) throws IOException {
		if (socket != null) {
			throw new IOException("FTPSnooper is already connected. Disconnect first.");
		}
		socket = new Socket(hostname, port);
		input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		output = new PrintWriter(socket.getOutputStream());
		
		response();
		if (!response.startsWith("220 ")) {
			throw new IOException("FTPSnooper received an unknown response when connecting to the FTP server: " + response);
		}
		
		command("USER " + user);
		response();
		if(!response.startsWith("331 ")) {
			throw new IOException("FTPSnooper received an unknown response after sending the user: " + response);
		}
		
		command("PASS " + password);
		response();
		if(!response.startsWith("230 ")) {
			throw new IOException("FTPSnooper was unable to log in with the supplied password: " + response);
		}
	}
	
	public synchronized void changeDirectory(String directory) throws IOException {
		command("CWD " + directory);
		response();
		if(!response.startsWith("250 ")) {
			throw new IOException("Failed to change directory due to: " + response);
		}
	}
	
	public synchronized void connectPASVmode() throws IOException {
		command("PASV");
		response();
		if (!response.startsWith("227 ")) {
			throw new IOException ("Failed to enter PASV mode: " + response);
		}
		int opening = response.indexOf('(');
		int closing = response.indexOf(')', opening + 1);
		if (closing > 0) {
			String info = response.substring(opening + 1, closing);
			StringTokenizer tokenizer = new StringTokenizer(info, ",");
			try {
				for (int i = 4; i > 0; i--) {
					tokenizer.nextToken();
				}
				port = Integer.parseInt(tokenizer.nextToken()) * 256 + Integer.parseInt(tokenizer.nextToken());
			} catch (Exception e) {
				throw new IOException ("Received bad information: " + response);
			}
		}
		socketPASV = new Socket(hostname, port);
		inputPASV = new BufferedReader(new InputStreamReader(socketPASV.getInputStream()));
	}
	
	public synchronized void list() throws IOException {
		command("NLST");
		response();
		if (!response.startsWith("150 ")) {
			throw new IOException ("Error occurs: " + response);
		}
		response();
		if (!response.startsWith("226 ")) {
			throw new IOException ("Error occurs: " + response);
		}
	}
	
	private void checkPASVfile() throws IOException{
		try {
			while((responsePASV = inputPASV.readLine()) != null)
			{
				checker(filePattern, responsePASV);
			}
		} catch (Exception e) {
			System.err.println(e);
		}
	}
	
	public void checker(Pattern filePattern, String fileName) throws IOException{
		Matcher match = filePattern.matcher(fileName);
		while(match.find()) {
			if (match.group().length() != 0) {
				requiredList.add(fileName);
			}
		}
	}
	
	public synchronized void updateFileInfo() throws IOException{
		for (String fileName : requiredList) {
			connectPASVmode();
			fileInfo(fileName);
			saveFileInfo(fileName);
		}
	}
	
	public synchronized void fileInfo(String fileName) throws IOException {
		command("RETR " + fileName);
		response();
		if (!response.startsWith("150 ")) {
			throw new IOException ("Error occurs: " + response);
		}
		response();
		if (!response.startsWith("226 ")) {
			throw new IOException ("Error occurs: " + response);
		}
	}
	
	private synchronized void saveFileInfo(String fileName) throws IOException{
		try {
			String value = null;
			int i = 0;
			while((responsePASV = inputPASV.readLine()) != null && i != 20) {
				if (value == null) {
					value = responsePASV;
				}
				else {
					value = value + "\n" + responsePASV;
					i++;
				}
			}
			fileInfo.put(fileName, value);
		} catch (Exception e) {
			System.err.println(e);
		}
	}
	
	public synchronized void disconnect() throws IOException {
		try {
			command("QUIT");
		} finally {
			socket = null;
		}
	}
	
	public void fetch() throws IOException {
		connectAndLogIn(hostname, port, "anonymous", "ident");
		changeDirectory(directory);
		connectPASVmode();
		list();
		checkPASVfile();
		updateFileInfo();
		disconnect();
	}

	/**
	 * Return the result of the fetch command.
	 * @return The result as a map with keys = "filenames" and values = "first 20 lines of each file".
	 */
	public Map<String, String> getFileInfo() {
		return fileInfo;
	}

}
