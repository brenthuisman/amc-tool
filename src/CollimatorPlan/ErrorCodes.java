package CollimatorPlan;

import java.util.HashMap;

/**Definities van errorCodes en conversieStates.
 * @author Brent
 */
public class ErrorCodes {
	
	/** errorToString: definitie van errorcodes. Range 0-10: File errorcodes. Range >10: Frame errorcodes. */
	static HashMap<Integer, String> errorToString = new HashMap<Integer, String>() {{
		   put(0,"Conversie mislukt, frame is onconverteerbaar.");
		   put(1,"Conversie succesvol.");
		   put(2,"Conversie succesvol, met concessies.");
		   put(3,"File is onconverteerbaar (aantal leafs niet 80, viewer only).");
		   put(4,"File is onconverteerbaar (geen IMRT plan).");
		   put(5,"File is onconverteerbaar (MLCX ontbreekt, zoals bij wig).");
		   put(6,"File is onconverteerbaar (DicomJar Exception)");
		   
		   put(11,"ASYMY word verplaatst in segment dat geen ASYMY veld kent.");
		   put(12,"Het interne nulveld werd met MLCX gedicht.");
		   put(13,"Het interne nulveld kan niet met MLCX gedicht worden. De kier kan niet ver genoeg uit het midden geplaatst worden.");
		   put(14,"Leafs per bank die het veld beperken staan verder uit elkaar dan mogelijk.");
		   put(15,"MLCX leaf schuift te ver over het midden.");
		   put(16,"ASYMY kan niet ver genoeg over het midden schuiven.");
		   put(17,"ASYMY overtravel limiet werd gecompenseerd door MLCX te sluiten.");
		   put(18,"Nieuwe leafs per omhullende bepaald.");
		   put(19,"Interdigitation aangetroffen en opgelost.");
	}};
		
	/** errorToState: Deelt frame errorcodes in in termen van file errorcodes.
	 * Als we fout 11 hebben, betekent dat dat de conversie van het file goed zal aflopen? */
	static HashMap<Integer, Integer> errorToState = new HashMap<Integer, Integer>() {{
		   put(0,0);
		   put(1,1);
		   put(2,2);
		   put(3,3);
		   put(4,4);
		   put(5,5);
		   put(6,6);
		   
		   put(11,1);
		   put(12,2);
		   put(13,0);
		   put(14,0);
		   put(15,0);
		   put(16,0);//kan niet meer voorkomen
		   put(17,2);
		   put(18,2);
		   put(19,2);
	}};

	/**Beschrijf foutcode.
	 * 
	 * @param errorCode Foutnummer
	 * @return Corresponderende string (beschrijving) van errorCode
	 */
	static String errorToString(int errorCode){
		return errorToString.get(errorCode);
	}
	/**Zet foutcode om in toestand van file.
	 * 
	 * @param errorCode Foutnummer
	 * @return Corresponderende foutstatus. Wat voor consequenties heeft deze fout voor de converteerbaarheid van het file?
	 */
	static int errorToState(int errorCode){
		return errorToState.get(errorCode);
	}

}
