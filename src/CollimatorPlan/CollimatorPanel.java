package CollimatorPlan;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

/**Tekent een CollimatorFrame.
 * @author Brent
 *
 */
public class CollimatorPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private static class Line {
		final int x1;
		final int y1;
		final int x2;
		final int y2;
		final Color color;

		public Line(int x1, int y1, int x2, int y2, Color color) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.color = color;
		}
	}

	private final LinkedList<Line> lines = new LinkedList<Line>();

	private void addLine(int x1, int x2, int x3, int x4) {
		addLine(x1, x2, x3, x4, Color.black);
	}

	private void addLine(int x1, int x2, int x3, int x4, Color color) {
		lines.add(new Line(x1, x2, x3, x4, color));
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		for (Line line : lines) {
			g.setColor(line.color);
			Graphics2D g2d = (Graphics2D) g;
			g2d.setStroke(new BasicStroke(2));
			g.drawLine(line.x1, line.y1, line.x2, line.y2);
		}
	}
	
	private int[] scaleDown(int iarr[]) {
		if( iarr == null ){
			return null;
		} else {
			int iarr2[] = new int[iarr.length];
			for (int i = 0; i < iarr.length; i++) {
				iarr2[i] = iarr[i]/5;
				//gedeeld door 10 om weer bij -200<c<200 te komen, en maal 2 om op 800x800 pixels te komen
			}
			return iarr2;
		}
	}

	CollimatorPanel(CollimatorFrame frame) {
		int[] ASYMY = scaleDown(frame.ASYMY);
		int[] ASYMX = scaleDown(frame.ASYMX);
		int[] MLCX = scaleDown(frame.MLCX);
		int[] MLCX_bound = scaleDown(frame.MLCX_bound);
		
		this.setPreferredSize(new Dimension(800, 800));
		
		this.addLine(400,0,400,800,Color.red); //roos
		this.addLine(0,400,800,400,Color.red);

		if(ASYMY!=null){
			//CCW 90graden draaien
			this.addLine(0, 400-ASYMY[0], 800, 400-ASYMY[0]);
			this.addLine(0, 400-ASYMY[1], 800, 400-ASYMY[1]);
		}
		if(ASYMX!=null){
			this.addLine(400+ASYMX[0], 0, 400+ASYMX[0], 800);
			this.addLine(400+ASYMX[1], 0, 400+ASYMX[1], 800);
		}
		if(MLCX!=null){
			int nrleafs = MLCX_bound.length-1;//lengte aan 1 kant
			for(int leaf=0;leaf<nrleafs;leaf++){
				Color color = new Color(0, 0, 255);
				/*try {
					//if(leaf==frame.minField||leaf==frame.maxField||leaf==frame.indexBehindASYMY[0]||leaf==frame.indexBehindASYMY[1]){
					if(frame.framePosField[leaf]!=0||leaf==frame.indexBehindASYMY[0]||leaf==frame.indexBehindASYMY[1]){
						color = new Color(255, 200, 0);
					}
				} catch (NullPointerException e) {
					// we printen een ongemod frame, dan bestaan die waarden natuurlijk njet.
				}*/
				int coord = MLCX[leaf];
				int bound1 = MLCX_bound[leaf];
				int bound2 = MLCX_bound[leaf+1];
				//leafjawpos linkerzijde
				this.addLine(400+coord, 400-bound1, 400+coord, 400-bound2, color);
				this.addLine(400+coord-50, 400-bound1, 400+coord, 400-bound1, color);
				this.addLine(400+coord-50, 400-bound2, 400+coord, 400-bound2, color);
				
				coord = MLCX[leaf+nrleafs];
				//leafjawpos rechterzijde
				this.addLine(400+coord, 400-bound1, 400+coord, 400-bound2, color);
				this.addLine(400+coord+50, 400-bound1, 400+coord, 400-bound1, color);
				this.addLine(400+coord+50, 400-bound2, 400+coord, 400-bound2, color);
			}
		}
	}
}
