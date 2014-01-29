package CollimatorPlan;

import java.util.ArrayList;

/**Definieert de collimator constraints van een type versneller, en bevat frameconversiealgoritmes. Bevat alle versnellerafhankelijke parameters.
 * @author Brent
 */
public class Accelerator {
	/**treatmentMachineName: TreatmentMachineName tag die in dicom metadata weg moet worden geschreven. */
	String treatmentMachineName;
	/**Is dit een Agility?*/
	boolean isAgility;
	/**Is dit een MLCi?*/
	boolean isMLCi;

	/**Aantal MLCX leafs*/
	int leafs;
	/**Aantal MLCX leafpairs*/
	int leafpairs;
	/**Hoever kan de ASYMY over het midden schuiven?*/
	int ASYMYovertravel;
	/**Welke MLCXbounds-indices passen bij de ASYMYovertravel waarden? */
	int ASYMYovertravelIndex[];
	/**minAngleMLCX: De minimale afstand vanaf midden die MLCX leaves moeten hebben bij sluiting in 0.1mm.
	 * Als deze contraint niet gehaald wordt, komt het frame en dus de dicomfile niet in aanmerking voor conversie. */
	int minAngleMLCX;
	/**Hoe dicht kunnen tegenoverliggende MLCX leafs elkaar naderen? */
	int minGap;
	/**Hoe ver kan een MLCX leaf de centrale as oversteken? */
	int MLCXovertravel;
	/**Hoe ver kunnen de MLCX leafs onderling uit elkaar liggen per bank? */
	int MLCXspacingMax;
	/**Als dit een type X versneller is, wat is het andere type dan? */
	Accelerator New;

	Accelerator(int MLCXlength){
		switch(MLCXlength){
			case 160:
				initAgility();
				New = new Accelerator(80,true);
            	break;
			case 80:
				initMLCi();
				New = new Accelerator(160,true);
            	break;
		}
	}
	private Accelerator(int MLCXlength, boolean stop){
		switch(MLCXlength){
			case 160: initAgility();
            	break;
			case 80: initMLCi();
            	break;
		}
	}
	
	private void initAgility(){
		isAgility = true;
		leafs = 160;
		leafpairs = 80;
		minAngleMLCX = 1000;
		treatmentMachineName = "AMC-U1-v2";
		ASYMYovertravel = 1200;
		/**ASYMYovertravelIndex: Bij welke MLCX slice zit de ASYMYovertravel? [threshold eerste ASYMY-coord,threshold 2e ASYMY coord.]
		 * index bereik waar MLCX gat moeten dichten: minField tot index(@120mm)=16, met -1 voor lower bound.
		 * index bereik waar MLCX gat moeten dichten: index(@-120mm)=64 tot maxField+1.
		 */
		ASYMYovertravelIndex = new int[]{64,15};
		minGap = 10;
		MLCXovertravel = 1500;
		MLCXspacingMax = 2000;
	}
	
	private void initMLCi(){
		isMLCi = true;
		leafs = 80;
		leafpairs = 40;
		minAngleMLCX = 1000;
		treatmentMachineName = "AMC-U3-v2";
		ASYMYovertravel = 0;
		/**ASYMYovertravelIndex: Bij welke MLCX slice zit de ASYMYovertravel? [threshold eerste ASYMY-coord,threshold 2e ASYMY coord.] 
		 * index bereik waar MLCX gat moeten dichten: minField tot index(@0mm)=20, met -1 voor lower bound.
		 * index bereik waar MLCX gat moeten dichten: index(@-0mm)=20 tot maxField+1.
		 */
		ASYMYovertravelIndex = new int[]{20,19};
		minGap = 50;
		MLCXovertravel = 1250;
		MLCXspacingMax = 3250;
	}
	
	/**Converteert collimatorframe aan de hand van een bestaand dicomframe.
	 * 
	 * Deze functie kijkt van welke type naar welk type geconverteerd moet worden en stelt op basis daarvan een stappenplan op.
	 * Deze stappen zijn andere functies binnen deze klasse. Soms kan dezelfde functie op beide type frames gebruikt worden, maar soms moet de logica echt anders zijn en is er dus een andere functie nodig.
	 * Het idee is dat met deze implementatie er zoveel mogelijk code gedeeld kan worden, en slechts waar nodig dit uitgesplitst wordt.
	 * 
	 * <p>  Algoritme overzicht:
	 * <ol>
	 * <li> Stap 1: aantal leafs verdubbelen/halveren voor MLCX.
	 * <li> Stap 2: bepaal welke MLCX leaf bijdragen aan veldvorm (oftewel, zitten ze binnen of buiten jaws).
	 * <li> Stap 3: Schuif ASYMY naar binnen zover als kan.
	 * <li> Stap 4: Schuif MLCX naar binnen zodat ze altijd het veld beperken.
	 * <li> Stap 5: Schuif MLCX dicht daar waar een intern nul-veld was maar niet door de ASYMY/X afgedekt kon worden.
	 * 		<ol>
	 * 		<li> Agility: leafs worden zo ver mogelijk uit midden gezet.
	 * 		<li> MLCi: leaf worden in het midden gezet.
	 * 		</ol>
	 * <li> Stap 6: Stel bladen achter ASYMY in zoals het hoort.
	 * <li> Stap 7 (alleen Agility>MLCi): Check en fix interdigitation.
	 * <li> Stap 8: Bepaal of aan alle constraints wordt voldaan en stel conversielog vast.
	 * </ol>
	 * 
	 * @param oldFrame 'oude' frame
	 * @return Volledig onafhankelijk geconverteerd frame.
	 */
	CollimatorFrame convertFrame(CollimatorFrame oldFrame){
		CollimatorFrame f = null;
		if(New.isMLCi){
			f = initFrameMLCi(oldFrame);
			setFieldPerMLCXslice(f);
			setASYMY(f);
			setMLCX(f);
			closeZeroFieldMLCi(f);
			fixBehindASMY(f);
			fixInterDigitation(f);
		}
		if(New.isAgility){
			f = initFrameAgility(oldFrame);
			setFieldPerMLCXslice(f);
			setASYMY(f);
			setMLCX(f);
			closeZeroFieldAgility(f);
			fixBehindASMY(f);
		}
		finalCheck(f);
		
		return f;
	}

	/**Stap1 Agility: Initialiseer nieuw Agility frame door aantal leafs te verdubbelen voor MLCX*/
	CollimatorFrame initFrameAgility(CollimatorFrame oldFrame){
		CollimatorFrame f = new CollimatorFrame();
		//Van 80 naar 160 leafs.
		f.MLCX = new int[oldFrame.MLCX.length*2];
		for (int leaf = 0; leaf < oldFrame.MLCX.length; leaf++) {
			f.MLCX[leaf*2] = oldFrame.MLCX[leaf];
			f.MLCX[leaf*2+1] = oldFrame.MLCX[leaf];
		}
		
		f.MLCX_bound = new int[ (oldFrame.MLCX_bound.length - 1) * 2 + 1 ];
		int delta = Math.abs( oldFrame.MLCX_bound[0] - oldFrame.MLCX_bound[1] ) / 2;
		for (int step = 0; step < oldFrame.MLCX_bound.length-1; step++) {
			f.MLCX_bound[step*2] = oldFrame.MLCX_bound[step];
			f.MLCX_bound[step*2+1] = oldFrame.MLCX_bound[step]+delta;
		}
		f.MLCX_bound[f.MLCX_bound.length-1] = oldFrame.MLCX_bound[oldFrame.MLCX_bound.length-1];
		
		f.ASYMY = oldFrame.ASYMY.clone();
		f.ASYMX = oldFrame.ASYMX.clone();
		return f;
	}
	
	/**Stap1 MLCi: Initialiseer nieuw MLCi frame door aantal leafs te halveren voor MLCX*/
	CollimatorFrame initFrameMLCi(CollimatorFrame oldFrame){
		CollimatorFrame f = new CollimatorFrame();
		//Van 160 naar 80 leafs.
		f.MLCX = new int[oldFrame.MLCX.length/2];
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			f.MLCX[leaf] = Math.min(oldFrame.MLCX[leaf*2],oldFrame.MLCX[leaf*2+1]);
			f.MLCX[New.leafpairs+leaf] = Math.max(oldFrame.MLCX[leafpairs+leaf*2],oldFrame.MLCX[leafpairs+leaf*2+1]);
		}
		f.MLCX_bound = new int[ (oldFrame.MLCX_bound.length - 1) / 2 + 1 ];
		for (int step = 0; step < f.MLCX_bound.length-1; step++) {
			f.MLCX_bound[step] = oldFrame.MLCX_bound[step*2];
		}
		f.MLCX_bound[f.MLCX_bound.length-1] = oldFrame.MLCX_bound[oldFrame.MLCX_bound.length-1];
		
		f._convState.add(18);//tbv debuggen eventueel uit, anders teveel vauten.
		f.ASYMY = oldFrame.ASYMY.clone();
		//TODO: ASYMX blijft null, maar er is nog niet gecheckt of hij het veld beperkte...
		return f;
	}
	
	/**Stap 2: Bepaal op welke MLCX slices er veld is.
	 * We loopen over de leafparen om fieldPerMLCXslice per leafpair van een getal te voorzien.
	 * 0) Geen veld (dus MLCX's onder ASYMX of ASYMY).
	 * 1) Wel Veld.
	 * 2) Wel Veld, maar de ASYMY ligt tussen de MLCXbounds, dus niet het volle veld tussen de leafs is hier aanwezig.
	 */
	void setFieldPerMLCXslice(CollimatorFrame f){
		f.fieldPerMLCXslice = new int[New.leafpairs]; // standaard is alles nul.
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			// wat ligt tussen Y-jaws (die haaks op MLCX bewegen) ?
			if( f.ASYMY[0] < f.MLCX_bound[leaf+1] &&
				f.ASYMY[1] > f.MLCX_bound[leaf]) {
				f.fieldPerMLCXslice[leaf] = 1;
				
				/* als leaf verder naar midden staat dan jaw of overstaande leaf verder naar midden dan overstaande jaw.
				 * bij nader inzien overbodig en zelfs vautiev.
				if( ASYMX1[0] <= MLCX[leaf] ||
					ASYMX1[1] >= MLCX[nrleafs+leaf] ) {
					// dan veldbijdrage = 1
					fieldPerMLCXslice[leaf] = 1;
				}*/
				
				// als ASYMX tussen leafranden ligt
				if( ( f.MLCX_bound[leaf] < f.ASYMY[0] && f.ASYMY[0] < f.MLCX_bound[leaf+1] ) ||
						( f.MLCX_bound[leaf+1] > f.ASYMY[1] && f.ASYMY[1] > f.MLCX_bound[leaf] ) ) {
						// dan gedeeltelijke overlap
					f.fieldPerMLCXslice[leaf] = 2;
					}
				if (New.isAgility) {
					// als leafs buiten ASYMX liggen, dan is bijdrage ook nul.
					if ((f.MLCX[New.leafpairs + leaf] <= f.ASYMX[0])
							|| (f.MLCX[leaf] >= f.ASYMX[1])) {
						f.fieldPerMLCXslice[leaf] = 0;
					}
				}
			}
		}
		
		//System.err.println(Arrays.toString(fieldPerMLCXslice));
		//System.err.println(Arrays.toString(ASYMY1));
	}
	
	/**Stap 3: Vind tot waar ASYMY naar binnen geschoven kan worden en doe dat.
	 * Hou tevens bij waar leafs alsnog dicht moeten als overtravel limiet van ASYMY bereikt wordt.
	 */
	void setASYMY(CollimatorFrame f){
		f.minField = 0;
		f.maxField = 0;
		for (int leaf = 0; leaf < f.fieldPerMLCXslice.length; leaf++) {
			f.minField = leaf;
			if (f.fieldPerMLCXslice[leaf] != 0){
				break;
			}
		}
		for (int leaf = 0; leaf < f.fieldPerMLCXslice.length; leaf++) {
			if (f.fieldPerMLCXslice[leaf] != 0){
				f.maxField = leaf;
			}
		}
		//Schuif nu ASYMY naar binnen en hou index bij waar dat zo is.
		f.indexBehindASYMY = new int[]{f.minField,f.maxField};
		//onderkant ASYMY schuiven
		if( f.MLCX_bound[f.minField] > f.ASYMY[0] && f.MLCX_bound[f.minField] <= New.ASYMYovertravel ) {
			//Tot New.ASYMYovertravel kunnen we schuiven, mits nodig.
			f.ASYMY[0] = f.MLCX_bound[f.minField];
		} else if ( f.MLCX_bound[f.minField] > f.ASYMY[0] && f.MLCX_bound[f.minField] > New.ASYMYovertravel ||
				f.MLCX_bound[f.minField] <= f.ASYMY[0] && f.ASYMY[0] > New.ASYMYovertravel ) {
			//Niet verder.
			//Als ASYMY al de rand van het veld beperkt, en toch overtravelled, ook dan op de threshold zetten.
			f._convState.add(17);
			f.ASYMY[0] = New.ASYMYovertravel;
			f.indexBehindASYMY[0] = New.ASYMYovertravelIndex[0];
		}
		//bovenkant ASYMY schuiven
		if( f.MLCX_bound[f.maxField+1] < f.ASYMY[1] && f.MLCX_bound[f.maxField+1] >= -New.ASYMYovertravel ){
			//Tot New.ASYMYovertravel kunnen we schuiven.
			f.ASYMY[1] = f.MLCX_bound[f.maxField+1];
		} else if ( f.MLCX_bound[f.maxField+1] < f.ASYMY[1] && f.MLCX_bound[f.maxField+1] < -New.ASYMYovertravel ||
				f.MLCX_bound[f.maxField+1] >= f.ASYMY[1] && f.ASYMY[1] < -New.ASYMYovertravel) {
			//Niet verder.
			//Als ASYMY al de rand van het veld beperkt, en toch overtravelled, ook dan op de threshold zetten.
			f._convState.add(17);
			f.ASYMY[1] = -New.ASYMYovertravel;
			f.indexBehindASYMY[1] = New.ASYMYovertravelIndex[1];
		}
		
		//Check met onderstaande of veldherkenning lekker werkt.
		/*System.err.println(nrBeam+" "+nrSegment);
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			System.err.println(
					"Op interval "+
					MLCX_bound[leaf]+"-"+MLCX_bound[leaf+1]+
					" is fieldPerMLCXslice "+
					fieldPerMLCXslice[leaf]+
					". Bounds op minField en maxField "+
					MLCX_bound[minField]+" "+MLCX_bound[maxField+1]+
					" en ASYMX is "+
					//ASYMX[0]+" "+ASYMX[1]+
					" en ASYMY is "+
					ASYMY[0]+" "+ASYMY[1]
			);
		}*/
	}
	
	/**Stap 4: Verplaats MLCX leafs zo ver mogelijk naar midden en bepaal minimum en maximum uitwijking per zijde.*/
	void setMLCX(CollimatorFrame f){
		f.leftMin = 2000;
		f.leftMax = -2000;
		f.rightMin = 2000;
		f.rightMax = -2000;
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			// als leaf en overstaande leaf veld beperkt
			if( f.fieldPerMLCXslice[leaf] != 0 ||
				( leaf > f.minField && leaf < New.ASYMYovertravelIndex[1]+1 && f.MLCX_bound[f.minField] <= New.ASYMYovertravel ) ||
				( leaf > New.ASYMYovertravelIndex[0] && leaf < (f.maxField+1) && f.MLCX_bound[f.maxField+1] >= -New.ASYMYovertravel ) ) {
				// en als jaw verder naar midden staat
				if( f.ASYMX != null && f.ASYMX[0] > f.MLCX[leaf] ){
					// dan schuif leaf naar plek van jaw.
					f.MLCX[leaf] = f.ASYMX[0];
				}
				// en hou bij hoe ver de meest linkse en rechtse MLCX nu staan.
				if( f.MLCX[leaf] < f.leftMin ){
					f.leftMin = f.MLCX[leaf];
				}
				if( f.MLCX[leaf] > f.leftMax ){
					f.leftMax = f.MLCX[leaf];
				}
				// en de overkant
				if( f.ASYMX != null && f.ASYMX[1] < f.MLCX[New.leafpairs+leaf] ) {
					f.MLCX[New.leafpairs+leaf] = f.ASYMX[1];
				}
				if( f.MLCX[New.leafpairs+leaf] < f.rightMin ){
					f.rightMin = f.MLCX[New.leafpairs+leaf];
				}
				if( f.MLCX[New.leafpairs+leaf] > f.rightMax ){
					f.rightMax = f.MLCX[New.leafpairs+leaf];
				}
			}
		}
		
		// Even de ASYMX zetten als we naar oud converteren
		if(New.isMLCi) f.ASYMX = new int[]{f.leftMin,f.rightMax};
		
		//System.err.println(leftMin+" "+leftMax+" "+rightMin+" "+rightMax+" "+ASYMX1[0]+" "+ASYMX1[1]);
	}

	/**Stap 5 Agility: Verwijder ASYMX en vul interne fieldPerMLCXslice[leaf] = 0 met MLCX bladen.
	 * Eerst bepalen hoever gesloten leafs naar rechts en links kunnen, dan verste van midden kiezen en vergelijken met minAngleMLCX.
	 * Hoever naar links? Vergelijk leftMax-2000 <= rightMax-2000-10 <= leftMin+2000. Dan MLCX[nrleafs+leaf]=rightMax-2000 en MLCX[leaf]=rightMax-2000-10
	 * Hoever naar rechts? rightMax-2000 <= leftMin+2000+10 <= rightMin+2000? Dan MLCX[leaf]=leftMin+2000 en MLCX[nrleafs+leaf]=leftMin+2000+10
	 * Onthoud: minimale afstand tussen leafs is 10 en de max spacing is 2000.
	 */
	void closeZeroFieldAgility(CollimatorFrame f){
		// Nu schuiven.
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			// hebben we een intern nulveld binnen ASYMY
			// of kon de ASYMY niet ver genoeg naar midden?
			if( ( leaf > f.minField && leaf < (f.maxField+1) && f.fieldPerMLCXslice[leaf] == 0 ) ||
				( f.fieldPerMLCXslice[leaf] == 0 && (f.MLCX_bound[f.minField] > New.ASYMYovertravel || f.MLCX_bound[f.maxField+1] < -New.ASYMYovertravel) ) ) {
				// dan sluit de gelederen en geef weer dat het paar nu wel bijdraagt aan veld.
				int[] MLCXl = null;
				int[] MLCXr = null;
				//Hoever kunnen we naar links?
				if( f.leftMax-New.MLCXspacingMax <= f.rightMax-New.MLCXspacingMax-New.minGap && f.rightMax-New.MLCXspacingMax-New.minGap <= f.leftMin+New.MLCXspacingMax ){
					MLCXl = new int[2]; //links=[0], rechts=[1]
					//check op overtravel.
					MLCXl[0] = Math.max(f.rightMax-New.MLCXspacingMax-New.minGap,-New.MLCXovertravel);
					MLCXl[1] = Math.max(f.rightMax-New.MLCXspacingMax,-New.MLCXovertravel+New.minGap);
				}
				//Hoever kunnen we naar rechts?
				if( f.rightMax-New.MLCXspacingMax <= f.leftMin+New.MLCXspacingMax+New.minGap && f.leftMin+New.MLCXspacingMax+New.minGap <= f.rightMin+New.MLCXspacingMax ){
					MLCXr = new int[2]; //links=[0], rechts=[1]
					MLCXr[0] = Math.min(f.leftMin+New.MLCXspacingMax,New.MLCXovertravel-New.minGap);
					MLCXr[1] = Math.min(f.leftMin+New.MLCXspacingMax+New.minGap,New.MLCXovertravel);
				}
				// Check of er uberhaupt een oplossing is.
				if( MLCXl == null && MLCXr == null ){
					//we are fucked
					f._convState.add(13);
					continue;
				}
				if( MLCXl != null && MLCXr != null ){
					//als rechteropl dichtbij klein
					if( Math.abs(MLCXr[0]) < Math.abs(MLCXl[1]) ){
						//dan kies linkeropl.
						f.MLCX[leaf] = MLCXl[0];
						f.MLCX[New.leafpairs+leaf] = MLCXl[1];
						f.fieldPerMLCXslice[leaf] = 1;
						if( Math.abs(MLCXl[1]) < New.minAngleMLCX ){
							f._convState.add(13);
							continue;
						}
					} else {
						//anders de rechteropl.
						f.MLCX[leaf] = MLCXr[0];
						f.MLCX[New.leafpairs+leaf] = MLCXr[1];
						f.fieldPerMLCXslice[leaf] = 1;
						if( Math.abs(MLCXr[0]) < New.minAngleMLCX ){
							f._convState.add(13);
							continue;
						}
					}
				}
				if( MLCXl != null && MLCXr == null ){
					f.MLCX[leaf] = MLCXl[0];
					f.MLCX[New.leafpairs+leaf] = MLCXl[1];
					f.fieldPerMLCXslice[leaf] = 1;
					if( Math.abs(MLCXl[1]) < New.minAngleMLCX ){
						f._convState.add(13);
						continue;
					}
				}
				if( MLCXl == null && MLCXr != null ){
					f.MLCX[leaf] = MLCXr[0];
					f.MLCX[New.leafpairs+leaf] = MLCXr[1];
					f.fieldPerMLCXslice[leaf] = 1;
					if( Math.abs(MLCXr[0]) < New.minAngleMLCX ){
						f._convState.add(13);
						continue;
					}
				}
				f._convState.add(12);
			}
		}
	}

	/**Stap 5 MLCi: Verwijder ASYMX en vul interne fieldPerMLCXslice[leaf] = 0 met MLCX bladen.
	 * Eerst bepalen hoever gesloten leafs naar rechts en links kunnen, dan verste van midden kiezen en vergelijken met minAngleMLCX.
	 * Indien nulveld, dan naastgelegen leafs in veld verhalen met minGap.
	 * Dit is anders dan het algoritme voor de Agility, omdat die erop rekent dat interdigitation geen probleem is.
	 * Het blijkt dat interdigitation oplossen veroorzaakt vaak extra gaten (dus extra veld), en dat is oplosbaar door geen interdigitation aan te brengen in the first place.
	 * Dat is het makkelijkst door veldranden 'smooth'te houden en ze dus gewoon te kopieren van naastgelegen leafs.
	 * Onthoud: minimale afstand tussen leafs is New.minGap.
	 */
	void closeZeroFieldMLCi(CollimatorFrame f){
		// Nu schuiven.
		//onderkantje, kopieren we ondergelegen veld.
		for (int leaf = 1; leaf < New.leafpairs/2; leaf++) {
			// hebben we een intern nulveld binnen ASYMY
			// of kon de ASYMY niet ver genoeg naar midden?
			if( ( leaf > f.minField && leaf < (f.maxField+1) && f.fieldPerMLCXslice[leaf] == 0 ) ||
				( f.fieldPerMLCXslice[leaf] == 0 && (f.MLCX_bound[f.minField] > New.ASYMYovertravel || f.MLCX_bound[f.maxField+1] < -New.ASYMYovertravel) ) ) {
				// dan sluit de gelederen en geef weer dat het paar nu wel bijdraagt aan veld.
				f.MLCX[leaf] = f.MLCX[leaf-1];
				f.MLCX[New.leafpairs+leaf] = f.MLCX[leaf-1]+New.minGap;
				f.fieldPerMLCXslice[leaf] = 1;
				f._convState.add(12);
			}
		}
		//bovenkantje, kopieren we bovengelegen veld.
		for (int leaf = New.leafpairs-1; leaf >= New.leafpairs/2; leaf--) {
			// hebben we een intern nulveld binnen ASYMY
			// of kon de ASYMY niet ver genoeg naar midden?
			if( ( leaf > f.minField && leaf < (f.maxField+1) && f.fieldPerMLCXslice[leaf] == 0 ) ||
				( f.fieldPerMLCXslice[leaf] == 0 && (f.MLCX_bound[f.minField] > New.ASYMYovertravel || f.MLCX_bound[f.maxField+1] < -New.ASYMYovertravel) ) ) {
				// dan sluit de gelederen en geef weer dat het paar nu wel bijdraagt aan veld.
				f.MLCX[leaf] = f.MLCX[leaf+1];
				f.MLCX[New.leafpairs+leaf] = f.MLCX[leaf+1]+New.minGap;
				f.fieldPerMLCXslice[leaf] = 1;
				f._convState.add(12);
			}
		}
	}

	/**Stap 6: Shit even goed zetten achter de ASYMY*/
	void fixBehindASMY(CollimatorFrame f){
		//2x delta correspondeert met 2 leafs.
		int delta2 = 2*Math.abs( f.MLCX_bound[0] - f.MLCX_bound[1] );		
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			//alles achter ASYMY goedzetten
			if( f.MLCX_bound[leaf+1] <= f.ASYMY[0] || f.MLCX_bound[leaf] >= f.ASYMY[1] ) {
				f.MLCX[leaf] = -200;
				f.MLCX[leaf+New.leafpairs] = 200;
			}
			//onderkantje 2 leafs herhalen
			if( f.MLCX_bound[leaf+1] > f.ASYMY[0]-delta2 && f.MLCX_bound[leaf+1] <= f.ASYMY[0] ) {
				f.MLCX[leaf] = f.MLCX[f.indexBehindASYMY[0]];
				f.MLCX[leaf+New.leafpairs] = f.MLCX[f.indexBehindASYMY[0]+New.leafpairs];
			}
			//bovenkantje
			if( f.MLCX_bound[leaf] < f.ASYMY[1]+delta2 && f.MLCX_bound[leaf] >= f.ASYMY[1] ) {
				f.MLCX[leaf] = f.MLCX[f.indexBehindASYMY[1]];
				f.MLCX[leaf+New.leafpairs] = f.MLCX[f.indexBehindASYMY[1]+New.leafpairs];
			}
		}
		if(New.isAgility) f.ASYMX = null;
	}
	
	/**Stap 7: Check en fix interdigitation want dat mag niet met de MLCi */
	void fixInterDigitation(CollimatorFrame f){
		for (int leaf = 1; leaf < New.leafpairs-1; leaf++) {
			if(f.MLCX[leaf]>f.MLCX[New.leafpairs+leaf-1]-New.minGap){
				f.MLCX[New.leafpairs+leaf-1] = f.MLCX[leaf] + New.minGap;
				f._convState.add(19);//tbv debuggen eventueel uit, anders teveel vauten.
			}
			if(f.MLCX[leaf]>f.MLCX[New.leafpairs+leaf+1]-New.minGap){
				f.MLCX[leaf] = f.MLCX[New.leafpairs+leaf+1]-New.minGap;
				f._convState.add(19);
			}
		}
	}
	
	/** Stap 8: Afhandeling en checks op constraints van MLCi of Agility, apart.*/
	void finalCheck(CollimatorFrame f){
		// Check op onderlinge afstand MLCX leafs.
		int check_leftMin = 2000, check_leftMax = -2000, check_rightMin = 2000, check_rightMax = -2000;
		for (int leaf = 0; leaf < New.leafpairs; leaf++) {
			if( f.MLCX[leaf] < check_leftMin ) check_leftMin = f.MLCX[leaf];
			if( f.MLCX[leaf] > check_leftMax ) check_leftMax = f.MLCX[leaf];
			if( f.MLCX[New.leafpairs+leaf] < check_rightMin ) check_rightMin = f.MLCX[New.leafpairs+leaf];
			if( f.MLCX[New.leafpairs+leaf] > check_rightMax ) check_rightMax = f.MLCX[New.leafpairs+leaf];
		}
		if( (check_leftMax-check_leftMin) >New.MLCXspacingMax || (check_rightMax-check_rightMin) >New.MLCXspacingMax ) f._convState.add(14);
		
		//Check op MLCX overtravel.
		if( check_leftMax>New.MLCXovertravel || check_rightMin<-New.MLCXovertravel ) f._convState.add(15);
		
		//Check op ASYMY overtravel. Zou nu nooit meer moeten voorkomen.
		if( f.ASYMY[0] > New.ASYMYovertravel || f.ASYMY[1] < -New.ASYMYovertravel ) f._convState.add(16);
		
		//Stel conversieinfo op frame in en bereken hoeveelheid veld.
		f.setConv();
	}
}
