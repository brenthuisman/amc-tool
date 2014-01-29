package CollimatorPlan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;

/**Definitie van een Collimatorframe.
 * 
 * Een Dicomfile bestaat uit beams en een beam uit controlpoints. Zo'n controlpoint kan slaan op verschillende dingen:
 * wiggen, collimator instellingen. Ook zijn in bijvoorbeeld een IMRT plan steeds twee controlpoints gegroepeerd in een segment.
 * Omdat het vaak niet eenduidig is wat met een van die termen bedoelt wordt, gebruik ik de notie van een 'frame' voor elke
 * collimator setting die je vind in een dicomfile.
 * 
 * <p>Belangrijk is de interne representatie van lengtes: integers waar 1 correspondeert met 0.1mm. Dit om floating point fouten
 * te voorkomen.
 * 
 * @author Brent
 *
 */
class CollimatorFrame {
	/**Het ASYMY element. */
	int[] ASYMY = null;
	/**Het ASYMX element. */
	int[] ASYMX = null;
	/**Het MLCX element. */
	int[] MLCX = null;
	/**De bounds van het MLCX element. Als er 10 leafpairs zijn, zijn er 5 leafs per zijde,
	 * en geeft MLCX_bound de afmetingen van de leafs door 6 waarden te geven. */
	int[] MLCX_bound = null;
	/**Conversiebool: 0=geen succesvolle conversie,1=succesvolle conversie */
	boolean convOK;
	/**{@link ErrorCodes#errorToState}*/
	int convState;
	/**{@link ErrorCodes#errorToString}*/
	String convErrors = "";
	/**Codes van foutcodes gegenereerd bij conversie, per frame. Kan gebruikt worden voor statistiek.*/
	Integer[] convErrorCodes;
	/**Aantal vierkante 0.1mm's bestraalt veld in dit frame. */
	int fieldArea;
	
	/**Conversievariabelen die state tijdens conversie vasthouden. Buiten conversie niet nodig. Kan misschien iets meer structuur gebruiken.*/
	int fieldPerMLCXslice[];
	int minField = 0,maxField = 0;
	int[] indexBehindASYMY;
	int leftMin, leftMax, rightMin, rightMax;
	ArrayList<Integer> _convState = new ArrayList<Integer>();
	
	CollimatorFrame(int[] _ASYMY, int[] _ASYMX, int[] _MLCX, int[] _MLCX_bound) {
		ASYMY = _ASYMY;
		ASYMX = _ASYMX;
		MLCX = _MLCX;
		MLCX_bound = _MLCX_bound;
		
		convState = 3;
		convOK = false;
		setFieldArea();
        //System.err.println("DicomFrame() "+Arrays.toString(ASYMY) + "\n" + Arrays.toString(ASYMX) + "\n" + Arrays.toString(MLCX));
	}

	CollimatorFrame() {
	}
	
	void setConv() {
		setFieldArea();
		setConvState(_convState);
		setConvErrors(_convState);
		setConvBool();
	}
	
	private void setConvState(ArrayList<Integer> _convErrors) {
		if (_convErrors.isEmpty()) {
			convState = 1;
			return;
		}
		//Verwijder dubbelen.
		convErrorCodes = (new ArrayList<Integer>(new LinkedHashSet<Integer>(_convErrors))).toArray(new Integer[0]);
		//System.out.println(Arrays.toString(convErrorCodes));
		
		ArrayList<Integer> _errorToState = new ArrayList<Integer>();
		for (Integer errorcode : _convErrors) {
			_errorToState.add(ErrorCodes.errorToState(errorcode));
		}
		if (_errorToState.contains(0)) {
			convState = 0;
			return;
		}
		if (_errorToState.contains(2)) {
			convState = 2;
			return;
		}
		if (_errorToState.contains(1)) {
			convState = 1;
			return;
		}
	}
	
	private void setConvErrors(ArrayList<Integer> _convState) {
		if( !(_convState == null) && !(_convState.isEmpty()) ) {
			for (Integer errorcode : _convState) {
				if (!convErrors.contains(ErrorCodes.errorToString(errorcode))) convErrors += ErrorCodes.errorToString(errorcode) + "\n" ;
			}
		}
	}

	private void setConvBool() {
		if( convState == 1 || convState == 2 ) {
			convOK = true;
		} else {
			convOK = false;
		}
	}
	
	private void setFieldArea(){
		fieldArea = 0;
		//int delta = Math.abs( MLCX_bound[0] - MLCX_bound[1] );
		for (int leaf = 0; leaf < MLCX_bound.length-1; leaf++) {
			// wat ligt tussen Y-jaws (die haaks op MLCX bewegen) ?
			int width=0,height=0;
			if( ASYMY[0] < MLCX_bound[leaf+1] &&
				ASYMY[1] > MLCX_bound[leaf]) {
				width = MLCX[leaf+(MLCX_bound.length-1)]-MLCX[leaf];
				if (ASYMX!=null) {
					// als ASYMX ook even checken of die verder naar binnen staat.
					// Door math.abs te nemen, wordt een leafpair onder de ASYMX geschoven nul, dus width == 0.
					width = Math.abs( Math.min(MLCX[MLCX_bound.length-1+leaf],ASYMX[1]) - Math.max(MLCX[leaf],ASYMX[0]) );
				}
				height = Math.min(MLCX_bound[leaf+1],ASYMY[1]) - Math.max(MLCX_bound[leaf],ASYMY[0]);
			}
			fieldArea += (width*height);
		}
	}
}