package CollimatorPlan;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

/**Commandline applicatie bedoelt om conversie statistieken te verzamelen over meerdere dicom-files.
 * @author Brent
 *
 */
public class MainStatistiek {
    public static void main(String[] args){
    	//CliMain main = new CliMain("./testfiles/RP1.3.6.1.4.1.2452.6.12960948.1271217527.3610621617.3550069132.dcm");
    	//CliMain main = new CliMain("./testfiles/rp_12.xml");
    	
    	//String srcdir = "testfiles";
    	//String srcdir = "../../dicomfilecollection";
    	//String srcdir = "../../imsure_rtplannen/oude_files_tm16nov2012";
    	//String srcdir = "dicomfiles/selectie_test/goed_jorrit";
    	String srcdir = "dicomfiles/160_2";
    	if(args.length > 0) srcdir = new File(args[0]).getAbsolutePath();
    	String dstdir = srcdir + "_converted2";//TODO haal 2 weg
    	File[] listOfFiles = new File(srcdir).listFiles(new FilenameFilter() {
    	    public boolean accept(File dir, String name) {
    	        return name.toLowerCase().endsWith(".dcm");
    	    }
    	});
    	new File(dstdir).mkdirs();
    	
    	int[] errorCodes = new int[listOfFiles.length];
    	StringBuilder convLog = new StringBuilder();
    	HashMap<Integer, Integer> convErrorCodes = new HashMap<>();//Codes van foutcodes gegenereerd bij conversie, per frame.
    	for (int i = 0; i < listOfFiles.length; i++){
    		if (listOfFiles[i].isFile()){
    			String filenameOld = listOfFiles[i].getAbsolutePath();
    			String filenameNew = new File(dstdir, listOfFiles[i].getName()).toString();
    			
    			CollimatorFile dicomFile = new CollimatorFile(filenameOld);
				errorCodes[i]=dicomFile.convState;
				convLog.append(dicomFile.convLog);
				
				for (Integer ec : dicomFile.convErrorCodes.keySet()) {
    			    if (convErrorCodes.containsKey(ec)) {
    			    	convErrorCodes.put(ec, convErrorCodes.get(ec) + dicomFile.convErrorCodes.get(ec));
    			    } else {
    			    	convErrorCodes.put(ec, dicomFile.convErrorCodes.get(ec));
    			    }
    			    
    			}
				//if(dicomFile.convState==2) System.out.println(dicomFile.filename);

				//dicomFile.save(filenameNew);
				
				//Print ratio's van straling.
				
    		}
    	}
    	
    	int errorHist[] = new int[7];
    	for (int i = 0; i < errorCodes.length; i++) {
    		errorHist[errorCodes[i]]++;
    	}
    	double errorHistFrac[] = new double[errorHist.length];
    	for (int i = 0; i < errorHist.length; i++) {
    		errorHistFrac[i] = ((double)errorHist[i]*100.)/((double)errorCodes.length);
        	//Print statussen
        	//System.out.format( ErrorCodes.errorToString(i)+": %.2f%% (%d).%n", errorHistFrac[i],errorHist[i] );
    	}
    	
		for (Entry<Integer, Integer> ec : convErrorCodes.entrySet()) {
	    	//Print typen conversiefouten
			//System.out.println( ErrorCodes.errorToString(ec.getKey()) +" "+ ec.getValue() );
		}
    	    	
    	//Print complete log
    	//System.out.println(convLog.toString());
		
		
    }
}