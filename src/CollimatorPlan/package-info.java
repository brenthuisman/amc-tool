/**
 * Software die collimatorconfiguraties van behandelplannen converteert tussen Agility en MLCi versnellers.
 * Op basis van DicomJar en RTplan definieert dit programma een CollimatorPlan, en kan het converteren tussen Agility en MLCi versnellers.
 * Dit project is uitgevoerd door <a href="mailto:brenthuisman@gmail.com">Brent Huisman</a> in opdracht van Jorrit Visser en Anette Houweling.
 * 
 * <h3>Voor de eindgebruiker</h3>
 * Er zijn 3 main()'s:
 * <ul><li>{@link CollimatorPlan.MainGui} - Dit is een Gui applicatie die een DicomFile kan openen, en daarvan de collimator per beam per segment kan weergeven.
 * 			Geeft ook de geconverteerde file weer + conversielog zodat grafisch geverifieerd kan worden of de conversie ergens op lijkt. Kan geconverteerde file opslaan. Screenshot: <img src="doc-files/maingui.png">
 * <li>{@link CollimatorPlan.MainProduction} - Een commandline applicatie die als argument een filename vereist, en mits de file converteerbaar geacht wordt deze opslaat als 'oudefilename_converted.dcm'.
 * 			Het conversielog wordt geprint naar std.err. Bedoelt voor gebruik in klinische omgeving.
 * <li>{@link CollimatorPlan.MainStatistiek} - Een commandline tool die naar wens aangepast moet worden om statistieken te verzamelen. Bijvoorbeeld: hoeveel dcm files zijn (momenteel) convertible?</ul>
 * 
 * <h3>Implementatie van deze library</h3>
 * Verder bestaan er de Collimator* classes die delen van de collimatorconfiguratie van een dicomfile implementeren. Een kort overzicht van de implementatie:
 * <ul><li>{@link CollimatorPlan.CollimatorFile} - Definieert een CollimatorFile en is het entrypoint van een tool bovenop deze library. Handelt openen, dicom-interactie, sluiten, saven, conversielogs af. Beschrijft hoe een collimatorfile eruit ziet door de set collimatorframes onder te verdelen in beams en segments.
 * <li>{@link CollimatorPlan.CollimatorFrame} - Een dicomfile bestaat uit meerdere beams en meerdere segmenten/controlpoints. Met name dit laatste hierarchische niveau ons vaag gedefinieerd. Soms moeten controlpoints gegroepeerd worden, soms niet, en soms bevat een controlpoint enkel wig informatie, en geen collimatorconfiguraties.
 * 		Daarom leek het me handig nog een woord te introduceren, en wel een dat correspondeert met de rest van de wereld: het frame. Een frame is niets anders dan een collimatorconfig die voor bepaald tijd belicht wordt. Een frame definieert dus een collimatorvorm behorende bij een hoeveelheid tijd of hoeveelheid stralingsdosis. Een frame heeft dus MLCX, ASYMY/X leafinstellingen als voornaamste members.
 * <li>{@link CollimatorPlan.CollimatorPanel} - Een CollimatorPanel is een hulpklasse die gegeven een CollimatorFrame een JFrame produceert. Deze kan dan door een eventuele gui tool getekent worden, zoals {@link CollimatorPlan.MainGui}.
 * <li>{@link CollimatorPlan.ErrorCodes} - Definieert de Errorcodes die gebruikt worden om het conversielog op te bouwen, en bevat de definities te bepalen wat een succesvol on niet succesvolle conversie inhoud ({@link CollimatorPlan.ErrorCodes#errorToState}).
 * <li>{@link CollimatorPlan.Accelerator} - De Accelerator klasse is waar het conversiealgoritme huist en waar de definitie van de versnellers in opgeslagen zijn. De klasse wordt geinstantieerd als Agility of MLCi, en converteert het frame naar het andere type middels {@link CollimatorPlan.Accelerator#convertFrame(CollimatorFrame)}, zondat dat andere klassen iets van de verschillen tussen de collimators afweten.
 * 		Wel is het zo dat {@link CollimatorPlan.CollimatorFile} aaneemt dat er altijd precies 2 typen zijn. Mocht er een derde type versneller bijkomen dan moet ook CollimatorFile aangepast worden.</ul>
 * 
 * <h3>Algoritme</h3>
 * Voor een nauwkeurigere beschrijving moet {@link CollimatorPlan.Accelerator} gelezen worden, maar in het kort is de conversie als volgt:
 * 
 * <ul><li>Van MLCi naar Agility
 * 		<ul><li>Verdubbel aantal MLCX leafs.
 * 		<li>Schuif ASYMY jaws zover mogelijk naar binnen.
 * 		<li>Schuif MLCX zover mogelijk naar binnen.
 * 		<li>Identificeer interne nulvelden, dat wil zeggen MLCXleaf-slices waar het vuld nul moet zijn, maar de ASYMY/X jaws daar niet voor kunnen zorgen.
 * 			Dit komt door twee aparte regios in een frame of dat de ASYMY zijn overtravel limiet heeft bereikt.
 * 		<li>Compenseer interne nulvelden door gesloten MLCX leafs (beperkt door {@link CollimatorPlan.Accelerator#MLCXspacingMax}) zo ver mogelijk uit het midden te plaatsen.
 * 		<li>Stel bladen achter ASYMY in zoals het hoort.
 * 		<li>Check op fysieke mogelijkheid van configuratie.</ul>
 * 
 * <li>Van Agility naar MLCi
 * 		<ul><li>Halveer aantal MLCX leafs.
 * 		<li>Schuif ASYMY jaws zover mogelijk naar binnen.
 * 		<li>Schuif ASYMX jaws zover mogelijk naar binnen.
 * 		<li>Schuif MLCX zover mogelijk naar binnen.
 * 		<li>Identificeer interne nulvelden, dat wil zeggen MLCXleaf-slices waar het vuld nul moet zijn, maar de ASYMY/X jaws daar niet voor kunnen zorgen.
 * 			Dit komt door twee aparte regios in een frame of dat de ASYMY zijn overtravel limiet heeft bereikt.
 * 		<li>Compenseer interne nulvelden door gesloten MLCX leafs (beperkt door {@link CollimatorPlan.Accelerator#MLCXspacingMax}) zo ver mogelijk uit het midden te plaatsen.
 * 		<li>Stel leafs achter ASYMY in zoals het hoort.
 * 		<li>Verhelp interdigitation.
 * 		<li>Check op fysieke mogelijkheid van configuratie.</ul>
 * </ul>
 * 
 * <h3>Keuzes</h3>
 * Bij het algoritme zijn een aantal keuzes gemaakt. Opgesomt zijn de belangrijkste:
 * <ul><li>Van MLCi naar Agility
 * 		<ul><li>Bij interne nulvelden die niet afgedekt worden (doordat de ASYMY zijn overtravel limiet heeft bereikt of een frame 2 aparte velden omvat),
 * 		worden MLCX leafpairs gesloten (= tot op {@link CollimatorPlan.Accelerator#minGap}) en zo ver mogelijk uit het midden gezet (zonder de MLCX overtravel te overschreiden).
 * 		Deze keuze is gebaseerd op een presentatie van Dr Vivian Cosgrove "Clinical Implementation of the Elekta Agility", waar gekeken wordt wat de transmissies is in zulke scenario's.
 * 		Als {@link CollimatorPlan.Accelerator#minAngleMLCX} (= vastgesteld op 10cm, omdat de presentatie data daarvan toont) niet behaalt wordt,
 * 		dan wordt de conversie als fout aangemerkt.</ul>
 * 
 * <li>Van Agility naar MLCi
 * 		<ul><li>Bij het halveren van het aantal MLCX leafs (zie {@link CollimatorPlan.Accelerator#initFrameMLCi(CollimatorFrame)}) voeg je de posities van 2 Agility leafs samen tot 1 MLCi leaf.
 * 		Voor de hand liggende keuzes binnenkant, buitenkant, gemiddelde. Gekozen is voor buitenkant (de omhullende), met de gedachte dat de behandeling beter teveel dan te weinig straling moet afgeven.
 * 		<li>De oplossing voor interne nulvelden zijn hier kwa principe hetzelfde als bij de omgekeerde conversie, maar de MLCX kier worden nu binnen de laatste leafs posities waar wel veld is gezet.
 * 		Dit omdat het verbod op interdigitation anders een gat zal introduceren, en de afmeting van dat gat (hoogte MLCX leaf * afstand tot {@link CollimatorPlan.Accelerator#minAngleMLCX}) is typisch groter dan
 * 		de afmeting van het gat dat ontstaat als de kier ergens meer in het midden staat (hoogte MLCX leaf * aantal leafs tot ASYMY-jaw * {@link CollimatorPlan.Accelerator#minGap}). Vergelijk:
 * 		<img src="doc-files/sluitenleafs.png"><img src="doc-files/sluitenleafs2.png">
 * 		<li>Het verbod op interdigitation introduceert soms extra veldregio. Wegens bovenstaande, maar ook al reeds bestaande interne nulvelden. Deze eis komt ongeveer neer op het willen smoothen van de MLCX-rand.
 * 		Nu is de keuze om, vanaf de centrale (horizontale) as bekeken, in de naar buiten gelegen leaf het gat te laten vullen, zodat het stuk extra veld zo ver mogelijk naar buiten zit.
 * 		Dit is echter een vrij simpele oplossing, want het algoritme kijkt alleen naar direct naastgelegen leafs, dus niet verder. Een eventuele verbetering zou eventuele patronen over meerdere leafs kunnen
 * 		herkennen en zo er achter komen dat er een optimalere oplossing is. Omdat dit veel werk voor een randgeval leek is dat nu niet gedaan. Wel wordt er nu gekeken naar de ratio van het oude veld en het nieuwe,
 * 		en wordt aan het convLog van een file de totale ratio (gewogen met ME per segment/frame) meegegeven. Zo is duidelijk wat het percentage extra dose per fractie is. Zo ziet interdigitation er uit:
 * 		<img src="doc-files/interdigit.png"><img src="doc-files/interdigit2.png"></ul>
 * </ul>
 * 
 * <h3><a name="stat">Statistieken</a></h3>
 * Resultaat van conversie van alle (2953) bestanden uit 2012:
 * <ul><li>Conversie mislukt, frame is onconverteerbaar.: 3,25% (96).
 * <li>Conversie succesvol.: 45,78% (1352).
 * <li>Conversie succesvol, met concessies.: 3,15% (93).
 * <li>File is onconverteerbaar (aantal leafs niet 80, viewer only).: 0,00% (0).
 * <li>File is onconverteerbaar (geen IMRT plan).: 2,71% (80).
 * <li>File is onconverteerbaar (MLCX ontbreekt, zoals bij wig).: 45,11% (1332).
 * <li>File is onconverteerbaar (DicomJar Exception): 0,00% (0).
 * </ul>
 * In de categorie "Conversie succesvol, met concessies." kwamen de volgende foutmeldingen voor, met daarachter de frequentie (en die slaat op het aantal frames, niet files!).
 * <ul><li>ASYMY overtravel limiet werd gecompenseerd door MLCX te sluiten. 69
 * <li>Het interne nulveld werd met MLCX gedicht. 255</ul>
 * 
 * In de categorie "Conversie mislukt, frame is onconverteerbaar." kwamen de volgende foutmeldingen voor, met daarachter de frequentie (en die slaat op het aantal frames, niet files!).
 * <ul><li>Het interne nulveld kan niet met MLCX gedicht worden. De kier kan niet ver genoeg uit het midden geplaatst worden. 2
 * <li>Leafs per bank die het veld beperken staan verder uit elkaar dan mogelijk. 336</ul>
 * 
 * Voor het 6-tal Agility plans zijn de ratio oud/nieuw veldoppervlak vermenigvuldigd met ME per file als volgt:
 * <ul><li>Voor het totale Plan: gewogen ratio: 1.0820345679766692
 * <li>Voor het totale Plan: gewogen ratio: 1.0994231481104084
 * <li>Voor het totale Plan: gewogen ratio: 1.1704194159365842
 * <li>Voor het totale Plan: gewogen ratio: 1.0743552425431728
 * <li>Voor het totale Plan: gewogen ratio: 1.138998019108043
 * <li>Voor het totale Plan: gewogen ratio: 1.0998001249291793</ul>
 * Een toekomstige aanpassing kan zijn het bepalen en instellen van een tolerantie criterium: hoeveel % meer dose is acceptabel? De logica bevind zich in {@link CollimatorPlan.CollimatorFile#convert()}.
 * 
 * @author Brent
 */
package CollimatorPlan;