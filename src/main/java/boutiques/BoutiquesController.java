package boutiques;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class BoutiquesController {
	
	@Autowired
	private SimpMessagingTemplate brokerMessagingTemplate;
    
	private void sendMessage(String message) throws Exception {
    	this.brokerMessagingTemplate.convertAndSend("/message/messages", message);
    }
    
    private void sendError(String message) throws Exception {
    	this.brokerMessagingTemplate.convertAndSend("/message/errors", message);
    }

    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @GetMapping("/tool/search")
    public ArrayList<BoutiquesTool> searchTool(@RequestParam(value="query", defaultValue="") String query) {

        ArrayList<BoutiquesTool> searchResults = new ArrayList<BoutiquesTool>();
        try {
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync("python boutiques/bosh.py search " + query, null, output);
        	searchResults = BoutiquesUtils.parseBoutiquesSearch(output);
        	
        } catch (Exception e) {
            System.out.println("Error: " + e);
        }

        return searchResults;
    }
    
    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @GetMapping("/tool/all")
    public ArrayList<BoutiquesTool> getAllTools() {

    	Path filePath = Paths.get(System.getProperty("user.home"), ".cache", "boutiques", "descriptors.json");
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayList<BoutiquesTool> boutiquesTools = new ArrayList<BoutiquesTool>();
        try {
	        ObjectNode descriptors = objectMapper.readValue(filePath.toFile(), ObjectNode.class);

	        Iterator<Entry<String, JsonNode>> iter = descriptors.fields();
	        while (iter.hasNext()) {
	            Entry<String, JsonNode> entry = iter.next();
	            String toolId = entry.getKey();
	            JsonNode toolDescriptor = entry.getValue();
	            String name = toolDescriptor.findValue("name").asText();
	            String description = toolDescriptor.findValue("description").asText();
	            int nDownloads = toolDescriptor.findValue("nDownloads").asInt();
		        boutiquesTools.add(new BoutiquesTool(toolId, name, description, nDownloads));
	        }
	        
	        return boutiquesTools;
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @GetMapping("/tool/{id}/descriptor/")
    public ObjectNode getDescriptorById(@PathVariable String id) {

        String descriptorFileName = id.replace('.', '-') + ".json";
        ObjectMapper objectMapper = new ObjectMapper();

        try {
	        File file = Paths.get(System.getProperty("user.home") , ".cache", "boutiques", descriptorFileName).toFile();
	
	        ObjectNode descriptor = objectMapper.readValue(file, ObjectNode.class);

	        return descriptor;
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @GetMapping("/tool/{id}/invocation")
    public String getInvocationById(@PathVariable String id, @RequestParam(value="complete", defaultValue="false") String completeString) {

        Boolean complete = Boolean.parseBoolean(completeString);

        try {
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync("python boutiques/bosh.py example " + (complete ? "--complete" : "") + " " + id, null, output);
        	
	        return String.join("\n", output);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }
    
    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @PostMapping("/tool/{id}/generate-command/")
    public String generateCommandById(@RequestBody ObjectNode invocation, @PathVariable String id) {

        try {
        	String invocationFilePath = BoutiquesUtils.writeTemporaryFile("invocation.json", invocation.toString());
        	ArrayList<String> output = new ArrayList<String>();
        	BoutiquesUtils.runCommandLineSync("python boutiques/bosh.py exec simulate -i " + invocationFilePath + " " + id, null, output);
        	
	        return String.join("\n", output);
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return null;
        }
    }

    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @PostMapping("/tool/{id}/execute/")
    public String getExecuteOutputById(@RequestBody ObjectNode invocation, @PathVariable String id) {

        try {
        	String invocationFilePath = BoutiquesUtils.writeTemporaryFile("invocation.json", invocation.toString());
        	
        	String command = "python boutiques/bosh.py exec launch -s " + id + " " + invocationFilePath;
        	System.out.println(command);
        	Process process = BoutiquesUtils.runCommandLineAsync(command, null);
        	BoutiquesUtils.sendProcessStreams(process, (message, isError)-> {
        		try {
            		if(isError) {
            			this.sendError(message);
            		} else {
                		this.sendMessage(message);	
            		}
        		} catch (Exception ex) {
        	        ex.printStackTrace();
        	    }
        	});

	        return "Execution started...";
        } catch (Exception e) {
            System.out.println("Error: " + e);
            return "Error: " + e;
        }
    }
    
    @CrossOrigin(origins = "https://shanoir-ng-nginx")
    @PostMapping("/tool/update-database/")
    public String updateDatabase() {
    	BoutiquesUtils.updateToolDatabase();
        return "Database update started.";
    }
}
