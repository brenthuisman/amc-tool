package CollimatorPlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import dicomlib.*;
import dicomlib.rtplan.*;
import org.dcm4che2.data.*;
import org.dcm4che2.util.UIDUtils;
import javax.swing.JPanel;

/**Representatie van een RTplan in termen van de collimator.
 * 
 * De voornaamste functies van deze class is het genereren van een grafische weergave van de collimatorframes
 * van een Dicomfile, en de conversie tussen 80 en 160 leaf MLCX collimators (MLCi en Agility versnellers).
 * 
 * @author Brent
 */

public class CollimatorFile {
	/** filename: De instantie van CollimatorFile beschrijft dit file. */
	String filename;
	/** convState: {@link ErrorCodes#errorToState} */
	int convState;
	/** convLog: Beschrijft het resultaat van een conversie in woorden. {@link ErrorCodes#errorToString} */
	String convLog = "";
	/** convOK: Boolean dit zegt of het geconverteerde file opgeslagen mag worden. Dit hangt af van de eisen
	 * gesteld in {@link ErrorCodes#errorToString}. 0 == failure, 1 == success. */
	boolean convOK;
	/** convErrorCodes: Codes van foutcodes gegenereerd bij conversie, per frame. */
	HashMap<Integer, Integer> convErrorCodes = new HashMap<>();
	/** colliFrames: Representatie van alle collimator frames in een file, geindexeerd op beam en segment */
	CollimatorFrame[][] colliFrames;
	/** colliFramesNew: Representeerd de geconverteerde frames. */
	CollimatorFrame[][] colliFramesNew;
	/** accel: beschrijft de accelerator van het bronbestand. */
	Accelerator accel;
	
	DicomObject dicomObject;
	FractionGroup fractionGroup;

	JPanel collimatorPanel = new JPanel();
	JPanel collimatorPanelNew = new JPanel();
	
	
	
	/**Instantieer CollimatorFile class. Checkt op conversiefout klassen en rapporteert.
	 * @param fileName
	 */
	CollimatorFile(String fileName) {
		filename = fileName;
		convOK = true;
		try {
			init();
		} catch (InvalidNumberOfMLCXleafsException e) {
			convState=3;
			convOK = false;
			convLog+=(filename+ErrorCodes.errorToString(convState)+"\n");
		} catch (InvalidRTplanException e) {
			convState=4;
			convOK = false;
			convLog+=(filename+ErrorCodes.errorToString(convState)+"\n");
		} catch (NullPointerException e) {
			//e.printStackTrace(); //Shit met wiggen.
			convState=5;
			convOK = false;
			convLog+=(filename+ErrorCodes.errorToString(convState)+"\n");
		} catch (Exception e) {
			//Helaas gooit DicomJar geen specifiekere error, dus al zeg ik hier State=6, kan het vanalles zijn (vooral in convert()).
			convState=6;
			convOK = false;
			convLog+=(filename+ErrorCodes.errorToString(convState)+"\n");
		}
	}
	
	/**Stel dicomObjecten in en test op conversiefouten.
	 * 
	 * Let op! dicomObject is null als filename geen echt dicomfile is. RTplan() geeft dan sowieso een exception.
	 * RTplan geeft ook een exception als er geen rtPlan in een correct file aanwezig is.
	 * 
	 * Als het een IMRT file is, gooi Exception, want dan klopt de library niet meer.
	 * Heeft de file geen 80 of 160 MLCX bladen?
	 * 
	 * @throws Exception
	 */
	void init() throws Exception {
		dicomObject = DicomFileIO.read(filename);
		
		if(!dicomObject.getString(Tag.Modality).equals("RTPLAN")) throw new InvalidRTplanException();
		
		//Let op! dicomObject is null als filename geen echt dicomfile is. RTplan() geeft dan sowieso een exception.
		//RTplan geeft ook een exception als er geen rtPlan in een correct file aanwezig is.
		RTplan rtPlan = new RTplan(dicomObject);
		
        fractionGroup = rtPlan.getFractionGroup(0); // Er is in praktijk altijd maar 1 fractiongroup.
    	
    	//Als het een IMRT file is, gooi Exception, want dan klopt de library niet meer.
    	for(int j=0;j<fractionGroup.getNumberOfReferencedBeams();j++){
    		if (!fractionGroup.getReferencedBeam(j).getBeam().getDicomBeam().getString(Tag.BeamType).equals("STATIC")) throw new InvalidRTplanException();
    	}
    	
    	//Heeft de file geen 80 of 160 MLCX bladen?
    	int leafs = fractionGroup.getReferencedBeam(0).getBeam().getSegment(0).getFirstControlPoint().getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.MLCX).getLeafJawPositions().length;
    	if( leafs != 80 && leafs != 160 ) {
    		throw new InvalidNumberOfMLCXleafsException();
    	} else {
    		accel = new Accelerator(leafs);
    	}
    	
    	colliFrames = new CollimatorFrame[fractionGroup.getNumberOfReferencedBeams()][];
    	for(int b=0;b<fractionGroup.getNumberOfReferencedBeams();b++){
            Beam beam = fractionGroup.getReferencedBeam(b).getBeam();
    		colliFrames[b] = new CollimatorFrame[beam.getNumberOfSegments()];
    		// TODO herschrijven naar loopen over controlpoints, niet segments. dan werkt t ook op VMAT
    		for (int s=0;s<beam.getNumberOfSegments();s++){
    			colliFrames[b][s] = loadFrame(b,s);
    		}
    	}
    	
    	if(colliFrames==null) throw new InvalidRTplanException();
		convState = convert();
	}
	
	/**Converteert dicomrepresentatie naar interne representatie. {@link CollimatorFrame}
	 * @param darr array van doubles
	 * @return array van ints
	 */
	int[] d2i(double darr[]) {
		if( darr == null ){
			return null;
		} else {
			int iarr[] = new int[darr.length];
			for (int i = 0; i < darr.length; i++) {
				iarr[i] = (int) Math.round(darr[i] * 10);
				//exacte 0.1mm precisie
			}
			return iarr;
		}
	}
	
	/**Converteert interne representatie naar dicomrepresentatie.
	 * @param iarr array van ints
	 * @return array van doubles
	 */
	double[] i2d(int iarr[]) {
		if( iarr == null ){
			return null;
		} else {
			double darr[] = new double[iarr.length];
			for (int i = 0; i < iarr.length; i++) {
				darr[i] = ((double)iarr[i])/10.;
			}
			return darr;
		}
	}

	/**Laad collimatorframe vanuit dicomobject/rtplan.
	 * 
	 * De interne representatie van lengtes is int 1 = 0.1mm. De dicomlibrary levert doubles in millimeters aan.
	 * @param b beamindex
	 * @param s segmentindex
	 * @return Volledig onafhankelijk frame, welke onafhankelijk geconverteerd kan worden.
	 */
	CollimatorFrame loadFrame(int b, int s){
		int[] ASYMY = null;
		int[] ASYMX = null; // afwezig in Agility
		int[] MLCX = null;
		int[] MLCX_bound = null;

        Beam beam = fractionGroup.getReferencedBeam(b).getBeam();
        ControlPoint c0 = fractionGroup.getReferencedBeam(b).getBeam().getSegment(0).getFirstControlPoint();
        ControlPoint c1 = fractionGroup.getReferencedBeam(b).getBeam().getSegment(s).getFirstControlPoint();
        //getSecondControlPoint geeft kwa diafragmaconfig tzelfde
        
		try {
			MLCX_bound = d2i(beam.getBeamLimitingDeviceByType(BeamLimitingDeviceType.MLCX).getLeafPositionBoundaries());
		} catch (NullPointerException e) { /* Gooit nullpointers al het element niet bestaat.
											* Zou niet motten kennen. */
			//e.printStackTrace();
		}
		try {
			MLCX = d2i(c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.MLCX).getLeafJawPositions());
		} catch (NullPointerException e) { /* Ken net. MLCX is er altied. */
			//e.printStackTrace();
		}
		try {
			ASYMY = d2i(c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMY).getLeafJawPositions());
		} catch (NullPointerException e) {
			//als dat niet werkte, dan uit allereerste segment pakken
			ASYMY = d2i(c0.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMY).getLeafJawPositions());
		}
		if (accel.isMLCi) {
			//ASYMX is er niet (en blijft dus null) als het een Agility dicomfile was.
			try {
				ASYMX = d2i(c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX).getLeafJawPositions());
			} catch (NullPointerException e) {
				//als dat niet werkte, dan uit allereerste segment pakken
				ASYMX = d2i(c0.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX).getLeafJawPositions());
			}
		}
        
		return new CollimatorFrame( ASYMY, ASYMX, MLCX, MLCX_bound );
	}

	/**Converteer alle frames in Collimatorfile
	 * @return statuscode van de conversie
	 */
	int convert(){
		/* returncodes zeggen of de conversie goed ging.
		 * 0 = Mislukt
		 * 1 = Goed
		 * 2 = Goed met concessies
		 * 3 = Onconverteerbaar
		 */
		int returncode = 1;
		
		//if(convOK) convOK = true;
        colliFramesNew = new CollimatorFrame[colliFrames.length][];
    	for(int b=0;b<colliFrames.length;b++){
    		colliFramesNew[b] = new CollimatorFrame[colliFrames[b].length];
    		for (int s=0;s<colliFrames[b].length;s++){
    			//converteer het frame
    			colliFramesNew[b][s] = accel.convertFrame(colliFrames[b][s]);
    			
    			//hou frequentie van foutcodes bij.
    			try {
					for (Integer ec : colliFramesNew[b][s].convErrorCodes) {
					    if (convErrorCodes.containsKey(ec)) {
					    	convErrorCodes.put(ec, convErrorCodes.get(ec) + 1);
					    } else {
					    	convErrorCodes.put(ec, 1);
					    }
					}
				} catch (NullPointerException e) {
					//niets.
				}
    			//System.out.println(convErrorCodes);
    			//if( convErrorCodes.containsKey(13) ) System.out.println(filename);
    			
    			//Converteer frame fouten naar file fouten.
    			if(!colliFramesNew[b][s].convOK) convOK = false;
    			if(!colliFramesNew[b][s].convErrors.equals("")) convLog += "Fout voor ("+b+","+s+"): "+colliFramesNew[b][s].convErrors;
    			if(colliFramesNew[b][s].convState == 2) {returncode = returncode*2; continue;};
    			if(colliFramesNew[b][s].convState == 0) {returncode = returncode*0; continue;};
    			if(colliFramesNew[b][s].convState == 1) {returncode = returncode*1; };
    		}
    	}

		//Check of veldtoename binnen perken blijft.
    	//reken uit hoeveel meer straling dit file zou geven. Voor elk frame vermenigvuldig stralings opp. ratio met aantal ME's.
    	double olddose = 0;
    	double newdose = 0;
    	for(int b=0;b<fractionGroup.getNumberOfReferencedBeams();b++){
            Beam beam = fractionGroup.getReferencedBeam(b).getBeam();
    		for (int s=0;s<beam.getNumberOfSegments();s++){
    			olddose += (double)colliFrames[b][s].fieldArea*beam.getFinalCumulativeMetersetWeight();
    			newdose += (double)colliFramesNew[b][s].fieldArea*beam.getFinalCumulativeMetersetWeight();
    			//straling += "Oud veld: " + colliFrames[b][s].fieldArea;
    			//straling += ". Nieuw veld: " + colliFramesNew[b][s].fieldArea;
    			//straling += ". Ratio: " + (double)colliFramesNew[b][s].fieldArea / (double)colliFrames[b][s].fieldArea;
    			//straling += ". Gewogen Ratio: " + (double)colliFramesNew[b][s].fieldArea / (double)colliFrames[b][s].fieldArea * beam.getFinalCumulativeMetersetWeight();
    			//straling += ".\n";
    		}
    	}
    	convLog += "Voor het totale Plan: gewogen ratio: " + newdose/olddose + "\n";
    	System.out.println("Voor het totale Plan: gewogen ratio: " + newdose/olddose + "\n");
	
    	if ( returncode > 1) return 2;
    	return returncode;
	}
	
	/**Teken een Collimator frame. Wordt gebruikt door {@link MainGui}.
	 * @param b beamindex
	 * @param s segmentindex
	 */
	void draw(int b, int s){
		collimatorPanel.removeAll();
		collimatorPanel.add(new CollimatorPanel(colliFrames[b][s]));
		collimatorPanel.setVisible(true);
		
		if(convState!=3){
			collimatorPanelNew.removeAll();
			collimatorPanelNew.add(new CollimatorPanel(colliFramesNew[b][s]));
			collimatorPanelNew.setVisible(true);			
		}
	}
	
	/**Sla geconverteerde CollimatorFile op.
	 * @param dstfile bestandsnaam van geconverteerde file.
	 */
	void save(String dstfile) {
		//Alles OK?
		if(!convOK){
	    	System.out.println("File is onconverteerbaar, opslaan geannuleerd. ("+filename+")");			
			return;
		}
		
		//dstfile = dstfile+", "+dicomObject.getString(Tag.PatientBirthName)+", "+dicomObject.getString(Tag.PatientID);
		
		//Update SOPInstanceUID
		dicomObject.putString(Tag.SOPInstanceUID,VR.UI,UIDUtils.createUID());
    	
		//RTplan name, max 64bytes
    	String newName = "conv_";
    	if( dicomObject.getString(Tag.RTPlanName) != null ) newName += dicomObject.getString(Tag.RTPlanName);
    	if( newName.length()>64) newName = newName.substring(0, 64);
    	dicomObject.putString(Tag.RTPlanName, VR.LO, newName);

		//RTplan label, max 16bytes
    	String newLabel = "conv_";
    	if( dicomObject.getString(Tag.RTPlanLabel) != null ) newLabel += dicomObject.getString(Tag.RTPlanLabel);
    	if( newLabel.length()>16) newLabel = newLabel.substring(0, 16);
    	dicomObject.putString(Tag.RTPlanLabel, VR.SH, newLabel);
    	
    	for(int b=0;b<fractionGroup.getNumberOfReferencedBeams();b++){
            Beam beam = fractionGroup.getReferencedBeam(b).getBeam();
            //update TreatmentMachineName
        	beam.getDicomBeam().putString(Tag.TreatmentMachineName, VR.SH, accel.New.treatmentMachineName);
        	beam.getDicomBeam().putString(0x300b100a, VR.LO, accel.New.treatmentMachineName);
        	
        	//update LeafPositionBoundaries
        	beam.getBeamLimitingDeviceByType(BeamLimitingDeviceType.MLCX).setLeafPositionBoundaries(i2d(colliFramesNew[b][0].MLCX_bound));
        	beam.getBeamLimitingDeviceByType(BeamLimitingDeviceType.MLCX).setNumberOfLeafJawPairs(colliFramesNew[b][0].MLCX_bound.length-1);
        	beam.removeBeamLimitingDeviceByType(BeamLimitingDeviceType.ASYMX);

			// Check of alles wel keurig 6MV is, anders bail().
        	if( beam.getSegment(0).getFirstControlPoint().getDicomControlPoint().getDouble(Tag.NominalBeamEnergy) != 6. ) {return;};
			
    		for (int s=0;s<beam.getNumberOfSegments();s++){
    			//2 controlpoints updaten!!!
    	        ControlPoint c1 = beam.getSegment(s).getFirstControlPoint();
    	        ControlPoint c2 = beam.getSegment(s).getSecondControlPoint();
    	        
    	        try { c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.MLCX).setLeafJawPositions(i2d(colliFramesNew[b][s].MLCX));}
    	        catch ( NullPointerException e ) { c1.addBeamLimitingDevicePosition(BeamLimitingDeviceType.MLCX, i2d(colliFramesNew[b][s].MLCX));};
    	        try { c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMY).setLeafJawPositions(i2d(colliFramesNew[b][s].ASYMY));}
    	        catch ( NullPointerException e ) { c1.addBeamLimitingDevicePosition(BeamLimitingDeviceType.ASYMY, i2d(colliFramesNew[b][s].ASYMY));};
    	        if(accel.isAgility) c1.removeBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX);
    	        if(accel.isMLCi) try { c1.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX).setLeafJawPositions(i2d(colliFramesNew[b][s].ASYMX));}
        	        catch ( NullPointerException e ) { c1.addBeamLimitingDevicePosition(BeamLimitingDeviceType.ASYMX, i2d(colliFramesNew[b][s].ASYMX));};
    	        //System.out.println(b+" "+s);
    	        if( c2.getNumberOfBeamLimitingDevicePositions()>0 ) {
    	        	//Check voor conventioneel plan, die heeft geen enkele CPSequence in de tweede CP
					try {c2.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.MLCX).setLeafJawPositions(i2d(colliFramesNew[b][s].MLCX));}
					catch ( NullPointerException e ) { c2.addBeamLimitingDevicePosition(BeamLimitingDeviceType.MLCX, i2d(colliFramesNew[b][s].MLCX));};
					try {c2.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMY).setLeafJawPositions(i2d(colliFramesNew[b][s].ASYMY));}
					catch ( NullPointerException e ) { c2.addBeamLimitingDevicePosition(BeamLimitingDeviceType.ASYMY, i2d(colliFramesNew[b][s].ASYMY));};
					if(accel.isAgility) c2.removeBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX);
					if(accel.isMLCi) try {c2.getBeamLimitingDevicePositionByType(BeamLimitingDeviceType.ASYMX).setLeafJawPositions(i2d(colliFramesNew[b][s].ASYMX));}
					catch ( NullPointerException e ) { c2.addBeamLimitingDevicePosition(BeamLimitingDeviceType.ASYMX, i2d(colliFramesNew[b][s].ASYMX));};
				}
    		}
    	}
		
		DicomFileIO.save(dstfile, dicomObject);
	}
}