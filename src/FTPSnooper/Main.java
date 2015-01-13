package FTPSnooper;

import java.io.IOException;
import java.util.Map;

public class Main {

	public static void main(String[] args) {
		
		System.out.println("FTP Snooper Version 1.0 \n");

		try {
		
			FTPSnooper ftpSnooper = new FTPSnooper("ftp.cs.ucl.ac.uk", "rfc", "rfc95[7-9]\\.txt");
	
			ftpSnooper.fetch();
			
			Map<String, String> fileInfo = ftpSnooper.getFileInfo();
			
			for (String filename: fileInfo.keySet()) {
				System.out.println("\n");
				System.out.println("     ************************************************************");
				System.out.println("     **** FILENAME: " + filename);
				System.out.println("     ************************************************************\n");
				System.out.println(fileInfo.get(filename));
			}
		
		} catch (IOException except) {
			System.out.println("FAILED due to IOException.\n");
			except.printStackTrace();
		}
		
	}
}
