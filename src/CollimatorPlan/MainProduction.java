package CollimatorPlan;
import java.io.File;

/**Commandline conversie applicatie, bedoelt voor klinische omgeving.
 * @author Brent
 *
 */
public class MainProduction {
    public static void main(String[] args){
    	//File file = new File("dicomfiles/probleemgevallen/RP1.3.6.1.4.1.2452.6.715967562.1209642085.225238161.2102214293.dcm");
    	//File file = new File("dicomfiles/probleemgevallen/dsr21D2.dcm");
    	File file = new File("dicomfiles/160leafplan/RP1.3.6.1.4.1.2452.6.272772693.1162310821.589011129.1788349446.dcm");
    	if(args.length > 0) file = new File(args[0]);
    	
		String filenameOld = file.getAbsolutePath();
		String filenameNew = new File(file.getAbsolutePath() + "_converted.dcm").toString();
		
		CollimatorFile dicomFile = new CollimatorFile(filenameOld);
    	System.out.println(dicomFile.convLog.toString());
    	
		dicomFile.save(filenameNew);
    }
}