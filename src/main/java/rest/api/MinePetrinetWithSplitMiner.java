package rest.api;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import org.apache.commons.collections15.Transformer;
import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.springframework.stereotype.Component;

import com.raffaeleconforti.conversion.bpmn.BPMNToPetriNetConverter;

import au.edu.qut.processmining.miners.splitminer.SplitMiner;
import au.edu.qut.processmining.miners.splitminer.ui.dfgp.DFGPUIResult;
import au.edu.qut.processmining.miners.splitminer.ui.miner.SplitMinerUIResult;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationImageServer;
import java.awt.Font;
import java.awt.Point;

import edu.uci.ics.jung.algorithms.layout.FRLayout;

@Component
public class MinePetrinetWithSplitMiner {

	 
	 public static BufferedImage generatePetrinet() {


		    String xesPath ="C:/Users/Malek Hammou/eclipse-workspace/SequencePredictionWithPetriNets/sampleXes.xes";
	        XesXmlParser xesParser = new XesXmlParser();
	        XLog log = null;
	        try {
	            log = xesParser.parse(new File(xesPath)).get(0);
	            System.out.println("Imported Event Log summary:");
	            System.out.println("Number of traces: " + log.size());
	            System.out.println("Number of events: " + log.stream().mapToInt(XTrace::size).sum());

	        } catch (Exception e) {
	            e.printStackTrace();
	        }

	        double eta = 0.4;//SplitMinerUIResult.FREQUENCY_THRESHOLD;	// 0.0 to 1.0
	        double epsilon = 0.1;//SplitMinerUIResult.PARALLELISMS_THRESHOLD;	// 0.0 to 1.0
			
			SplitMiner splitminer = new SplitMiner();
	        BPMNDiagram output = splitminer.mineBPMNModel(log, eta, epsilon, DFGPUIResult.FilterType.FWG, true, true, false, SplitMinerUIResult.StructuringTime.NONE);
			Object[] result = BPMNToPetriNetConverter.convert(output);
			Petrinet generatedPetriNet=(Petrinet) result[0];
			System.out.println("PETRINET GENERATED SUCCESSFULLY");
	        BufferedImage image= visualizePetrinet(generatedPetriNet);
	        return image;

			
			

 
	    }	
	 public static BufferedImage visualizePetrinet(Petrinet petriNet) {
		    Graph<String, String> graph = new DirectedSparseGraph<>();

		    // Add places as nodes
		    for (Place place : petriNet.getPlaces()) {
		        graph.addVertex(place.getLabel());
		    }

		    // Add transitions as nodes
		    for (Transition transition : petriNet.getTransitions()) {
		        graph.addVertex(transition.getLabel());
		    }

		    // Add arcs as edges
		    for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> arc : petriNet.getEdges()) {
		        String source = arc.getSource().getLabel();
		        String target = arc.getTarget().getLabel();
		        graph.addEdge(source + "-" + target, source, target);
		    }

		    // Create visualization components
		    Layout<String, String> layout = new FRLayout<>(graph);
		    layout.setSize(new Dimension(950, 750));
		    VisualizationImageServer<String, String> vv = new VisualizationImageServer<>(layout, new Dimension(1100, 900));
		    vv.setBackground(Color.WHITE); // Set the background color to white

		    // Set the vertex label transformer to display the labels inside the nodes
		    vv.getRenderContext().setVertexLabelTransformer(new Transformer<String, String>() {
		        @Override
		        public String transform(String v) {
		            return "<html><center>" + v + "</center></html>";
		        }
		    });

		    // Set the vertex shape transformer to display transition nodes as black rectangles and place nodes as white rectangles
		    vv.getRenderContext().setVertexShapeTransformer(v -> {
		        if (petriNet.getTransitions().stream().anyMatch(t -> t.getLabel().equals(v))) {
		            return new Rectangle(-20, -20, 40, 40);
		        } else {
		            return new Rectangle(-20, -20, 40, 40);
		        }
		    });

		    // Set the vertex label font and color
		    vv.getRenderContext().setVertexFontTransformer(v -> new Font("Arial", Font.PLAIN, 12));
		    vv.getRenderContext().setVertexFillPaintTransformer(v -> {
		        if (petriNet.getTransitions().stream().anyMatch(t -> t.getLabel().equals(v))) {
		            return Color.BLACK;
		        } else {
		            return Color.WHITE;
		        }
		    });

		    // Create an image of the graph
		    BufferedImage image = (BufferedImage) vv.getImage(new Point(-400, -200), vv.getGraphLayout().getSize());

		    return image;
		}




	 
	 

}

