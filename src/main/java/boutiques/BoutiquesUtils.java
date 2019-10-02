package boutiques;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
interface SendMessage {
    public void apply(String message, boolean isError);
}

public class BoutiquesUtils {
    
	public static String writeTemporaryFile(String pFilename, String content) throws IOException {
	    File tempDir = new File(System.getProperty("java.io.tmpdir"));
	    File tempFile = File.createTempFile(pFilename, ".tmp", tempDir);
	    FileWriter fileWriter = new FileWriter(tempFile, true);
	    System.out.println(tempFile.getAbsolutePath());
	    BufferedWriter bw = new BufferedWriter(fileWriter);
	    bw.write(content);
	    bw.close();
	    return tempFile.getAbsolutePath();
	}

    private static void sendStream(BufferedReader bufferedReader, boolean isError, SendMessage sendMessage) {
    	try {
	    	String line;
			if((line=bufferedReader.readLine())!=null) {
	            System.out.println(line);
            	sendMessage.apply(line, isError);
			}
	    } catch (IOException ex) {
        	sendMessage.apply("Server error: " + ex.getMessage(), true);
	        ex.printStackTrace();
	    }
    }
    
    public static void sendProcessStreams(Process process, SendMessage sendMessage) {
        BufferedReader inputBufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        Timer timer = new Timer();
        
        timer.scheduleAtFixedRate(new TimerTask() {
        	  @Override
        	  public void run() {
        		  if(!process.isAlive()) {
        			  timer.cancel();
        			  return;
        		  }
        		  sendStream(inputBufferedReader, false, sendMessage);
        		  sendStream(errorBufferedReader, true, sendMessage);
        	  }
        	}, 0, 250);
    }

    public static Process runCommandLineAsync(final String cmdline, final String directory) throws IOException {
    	String[] command = { "/bin/bash", "-c", cmdline};
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.directory(new File(directory != null ? directory : "."));
        Process process = processBuilder.start();
        return process;
    }
    
    public static int runCommandLineSync(final String cmdline, final String directory, ArrayList<String> output) throws IOException, Exception {
    	String[] command = { "/bin/bash", "-c", cmdline};
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        processBuilder.directory(new File(directory != null ? directory : "."));
        Process process = processBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        while ((line = reader.readLine()) != null) {
        	output.add(line);
            System.out.println(line);
        }

        int exitCode = process.waitFor();
        return exitCode;
    }
    
    public static ArrayList<BoutiquesTool> parseBoutiquesSearch(ArrayList<String> searchOutput) {
        
    	String headingLine = searchOutput.get(1);
        int idIndex = headingLine.indexOf("ID");
        int titleIndex = headingLine.indexOf("TITLE");
        int descriptionIndex = headingLine.indexOf("DESCRIPTION");
        int nDownloadsIndex = headingLine.indexOf("DOWNLOADS");
        
        ArrayList<BoutiquesTool> searchResults = new ArrayList<BoutiquesTool>();
        for(int i=2 ; i<searchOutput.size() ; i++){
            String line = searchOutput.get(i);

            String id = line.substring(idIndex, titleIndex).trim();
            String name = line.substring(titleIndex, descriptionIndex).trim();
            String description = line.substring(descriptionIndex, nDownloadsIndex).trim();
            int nDownloads = Integer.parseInt(line.substring(nDownloadsIndex, line.length() - 1).trim());

            searchResults.add(new BoutiquesTool(id, name, description, nDownloads));
        }
        return searchResults;
    }
    
    private static String writeSearchResultsToJSON(ArrayList<BoutiquesTool> searchResults) {
    	
    	Path filePath = Paths.get(System.getProperty("user.home"), ".cache", "boutiques", "descriptors.json");

	    ObjectMapper mapper = new ObjectMapper();

	    try {     
	        mapper.writeValue(filePath.toFile(), searchResults);
	    } catch (IOException e) {  
	        e.printStackTrace();
	    }
	    
	    return filePath.toString();
    }

    public static ArrayList<BoutiquesTool> getAllTools() {

        ArrayList<BoutiquesTool> searchResults = new ArrayList<BoutiquesTool>();
        try {
        	ArrayList<String> output = new ArrayList<String>();
        	runCommandLineSync("python boutiques/bosh.py search -m 1000", null, output);
        	searchResults = parseBoutiquesSearch(output);
        	writeSearchResultsToJSON(searchResults);
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }

        return searchResults;
    }
    
    private static ObjectNode getToolDescriptor(String id) throws JsonParseException, JsonMappingException, IOException {

        String descriptorFileName = id.replace('.', '-') + ".json";
        ObjectMapper objectMapper = new ObjectMapper();

        File file = Paths.get(System.getProperty("user.home") , ".cache", "boutiques", descriptorFileName).toFile();

        ObjectNode descriptor = objectMapper.readValue(file, ObjectNode.class);

        return descriptor;
    }

    private static void updateToolDatabaseFiles(ArrayList<BoutiquesTool> searchResults) throws JsonParseException, JsonMappingException, IOException {
	    ObjectMapper mapper = new ObjectMapper();
        final ObjectNode descriptors = mapper.createObjectNode();
        for(BoutiquesTool boutiquesTool: searchResults)
        {
        	ObjectNode descriptorObject = getToolDescriptor(boutiquesTool.getId());
            descriptorObject.put("id", boutiquesTool.getId());
            descriptorObject.put("nDownloads", boutiquesTool.getNDownloads());
            descriptors.set(boutiquesTool.getId(), descriptorObject);
        }

    	Path filePath = Paths.get(System.getProperty("user.home"), ".cache", "boutiques", "descriptors.json");

	    try {     
	        mapper.writeValue(filePath.toFile(), descriptors);
	    } catch (IOException e) {  
	        e.printStackTrace();  
	    }
    }
    
    public static void updateToolDatabase() {
    	ArrayList<BoutiquesTool> searchResults = getAllTools();
    	String pullCommandLine = "python boutiques/bosh.py pull";
    	for (BoutiquesTool boutiquesTool : searchResults) {
    		pullCommandLine += " " + boutiquesTool.getId();
		}
        try {
        	Process process = runCommandLineAsync(pullCommandLine, null);

            Thread commandLineThread = new Thread(() -> {
                try {
					int exitCode = process.waitFor();
					if(exitCode == 0) {
						updateToolDatabaseFiles(searchResults);
					} else {
			            System.out.println("Error: the bosh ended with exit code " + exitCode);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
            });
            
            commandLineThread.start();
            
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }
    }
}
