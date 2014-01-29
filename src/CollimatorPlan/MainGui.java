package CollimatorPlan;
import javax.swing.*;

import java.lang.System;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

/**<h2>Grafische dicom collimator viewer en conversietool.</h2>
 * <p>
 * <h3>Voorbeeld van een file layout.</h3> Horizontal de beams, vertical de segmenten of controlpoints. Informatiebubbles duidt op concessies bij de conversie. Een rode bubble met kruis duidt op een onoverkomelijke fout bij de conversie. Wat onoverkomelijk is, wat een concessie en wat foutloos kan ingesteld worden in {@link ErrorCodes}.
 * <p><img src="doc-files/maingui.png"></p>
 * <h3>Voorbeeld van een collimatorview.</h3>
 * <p><img src="doc-files/interdigit2.png"></p>
 * 
 * 
 * @author Brent
 *
 */
public class MainGui extends JFrame {
	private static final long serialVersionUID = 1L;
	
	JFileChooser fc = new JFileChooser("./");
	static CollimatorFile dicomFile = null;
	static MainGui ex;
	static JPanel beamNav = new JPanel();
	
	public MainGui() {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e1) {};
        setTitle("Behandelplan conveRsie En Nakijk Tool (BRENT)");
        setSize(820, 895);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        
		final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);
		
		tabbedPane.addTab("Beam Navigator", null, beamNav, null);
		GridBagLayout gbl_beamNav = new GridBagLayout();
		gbl_beamNav.columnWidths = new int[]{0};
		gbl_beamNav.rowHeights = new int[]{0};
		gbl_beamNav.columnWeights = new double[]{Double.MIN_VALUE};
		gbl_beamNav.rowWeights = new double[]{Double.MIN_VALUE};
		beamNav.setLayout(new GridBagLayout());
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenuItem mntmOpenFile = new JMenuItem("Open file...");
		mntmOpenFile.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				int returnVal = fc.showOpenDialog(MainGui.this);
				if (returnVal == JFileChooser.APPROVE_OPTION) {
					//fc.setMultiSelectionEnabled(true);
					//fc.setFileSelectionMode(JFileChooser.FILES_ONLY)
					String filename = fc.getSelectedFile().getAbsolutePath();
					
					//Oude panels legen.
					beamNav.removeAll();
					try {
						tabbedPane.remove(dicomFile.collimatorPanel);
						tabbedPane.remove(dicomFile.collimatorPanelNew);
					} catch (NullPointerException e) {
						//Niets aan doen! Gebeurt alleen bij selectie eerder collimatorframe, daarna nooit meer.
					}
					
					//Bestand openen.
					dicomFile = new CollimatorFile(filename);
					if(dicomFile.convOK==false||dicomFile.convState==3) {
						JOptionPane.showMessageDialog(ex, ErrorCodes.errorToString(dicomFile.convState));
						System.err.println(ErrorCodes.errorToString(dicomFile.convState));
					}

					if(!dicomFile.convLog.equals("")) System.err.println(dicomFile.filename+"\n"+dicomFile.convLog);
			        //setTitle("BRENT (Behandelplan conveRsie En Nakijk Tool) "+dicomFile.filename.substring(dicomFile.filename.lastIndexOf('\\')+1,dicomFile.filename.length()));

					//BeamNav panel opbouwen
			        GridBagConstraints c = new GridBagConstraints();
			        c.fill = GridBagConstraints.BOTH;
			        c.weightx = 1.;
			        c.weighty = 1.;
			    	for (int b=0; b < dicomFile.colliFrames.length; b++){
			    		c.gridx = b+1;
			    		for (int s=0; s < dicomFile.colliFrames[b].length; s++){
			        		c.gridy = s+1;
			        		Icon icon = null;
			        		if(dicomFile.convState!=3){
				        		if(dicomFile.colliFramesNew[b][s].convState == 2) icon = UIManager.getIcon("OptionPane.informationIcon");
				        		if(dicomFile.colliFramesNew[b][s].convState == 0) icon = UIManager.getIcon("OptionPane.errorIcon");
			        		}
			        		JButton knop = new JButton("<html>beam "+Integer.toString(b)+"<br>segment "+Integer.toString(s)+"</html>",icon);
			        	    knop.setActionCommand(Integer.toString(b)+","+Integer.toString(s));
			        		knop.addActionListener(new ActionListener() {
			                    public void actionPerformed(ActionEvent evt) {
			                        String[] coord = evt.getActionCommand().split(",");
			                    	dicomFile.draw(Integer.parseInt(coord[0]),Integer.parseInt(coord[1]));
			                		tabbedPane.addTab("Current Collimator View "+Arrays.toString(coord), null, dicomFile.collimatorPanel, null);
			            			if(dicomFile.convState!=3){
			            				tabbedPane.addTab("Converted Collimator View "+Arrays.toString(coord), null, dicomFile.collimatorPanelNew, null);
				                		tabbedPane.setSelectedComponent(dicomFile.collimatorPanelNew);
				                		if(!dicomFile.colliFramesNew[Integer.parseInt(coord[0])][Integer.parseInt(coord[1])].convErrors.equals("")) {
				                			JOptionPane.showMessageDialog(new JFrame(), dicomFile.colliFramesNew[Integer.parseInt(coord[0])][Integer.parseInt(coord[1])].convErrors,"Conversiefout",JOptionPane.ERROR_MESSAGE);
				                		}
			            			} else {
				                		tabbedPane.setSelectedComponent(dicomFile.collimatorPanel);
			            			}
			                    }
			                });
			        		beamNav.add(knop,c);
			    		}
			    	}
				}
			}
		});
		mnFile.add(mntmOpenFile);
		
		//JMenuItem mntmConvertCurrentFile = new JMenuItem("Convert current file");
		//mnFile.add(mntmConvertCurrentFile);
		
		JMenuItem mntmSaveFile = new JMenuItem("Save converted file...");
		mntmSaveFile.addActionListener(new ActionListener() {
				public void actionPerformed (ActionEvent event) {
					dicomFile.save(dicomFile.filename+"_converted.dcm");
					JOptionPane.showMessageDialog(new JFrame(), "Bestand opgeslagen als\n"+dicomFile.filename+"_converted.dcm","Opslaan",JOptionPane.INFORMATION_MESSAGE);
					}
				});
		mnFile.add(mntmSaveFile);
	}
	
    public static void main(String[] args){
    	ex = new MainGui();
        ex.setVisible(true);
    }

}