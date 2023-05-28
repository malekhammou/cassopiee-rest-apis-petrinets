package rest.api;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

import com.raffaeleconforti.conversion.bpmn.BPMNToPetriNetConverter;

import au.edu.qut.processmining.miners.splitminer.SplitMiner;
import au.edu.qut.processmining.miners.splitminer.ui.dfgp.DFGPUIResult;
import au.edu.qut.processmining.miners.splitminer.ui.miner.SplitMinerUIResult;
import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;

public class MinePetrinetWithSplitMiner {
	 public static void main(String[] args) {



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
	        visualizePetrinet(generatedPetriNet);

			
			

		   
            
            
            
         

			
			
	/*
	 * String filePath = "C:/Users/Malek Hammou/petriNet.ser"; // Specify the path and filename for the serialized object
			File file = new File(filePath);
	        try (FileOutputStream fileOut = new FileOutputStream(file);
	             ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {
	            if (!file.exists()) {
	                file.createNewFile();
	            }
	            objectOut.writeObject(generatedPetriNet);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	 * */		
			

     /*
      *     PluginContext pluginContext = new DummyPluginContext();
         PnmlExportNetToPNML exporter = new PnmlExportNetToPNML();
         String pnmlFilePath = "C:/Users/output.pnml";
         File pnmlFile = new File(pnmlFilePath);
         try {
			exporter.exportPetriNetToPNMLFile(pluginContext, finalOutput, pnmlFile);
	        System.out.println("Petri net exported to " + pnmlFilePath);

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		*/
	    }
	
	 public static void visualizePetrinet(Petrinet petriNet) {
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
	        Layout<String, String> layout = new CircleLayout<>(graph);
	        VisualizationViewer<String, String> vv = new VisualizationViewer<>(layout);
	        vv.setPreferredSize(new Dimension(900, 700));
	        vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller<>());

	        // Customize vertex appearance
	   
	        // Create the frame to display the visualization
	        JFrame frame = new JFrame("Petri Net Visualization");
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.getContentPane().add(vv);
	        frame.pack();
	        frame.setVisible(true);
	        try {
	            Thread.sleep(1000); // Wait for the frame to fully render
	            BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
	            frame.paint(image.getGraphics());
	            ImageIO.write(image, "png", new File("petri_net.png"));
	            System.out.println("Visualization saved as petri_net.png");
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	 
	 
	 
	 
	 
	 
	 
/*
 * 
 *  public static void visualizePetrinet(Petrinet petriNet) {
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
	        Layout<String, String> layout = new CircleLayout<>(graph);
	        VisualizationViewer<String, String> vv = new VisualizationViewer<>(layout);
	        vv.setPreferredSize(new Dimension(500, 400));
	        vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller<>());

	        // Create the frame to display the visualization
	        JFrame frame = new JFrame("Petri Net Visualization");
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.getContentPane().add(vv);
	        frame.pack();
	        frame.setVisible(true);
	    }*/	 
	 
	 /**
	  * ANOTHER WORKING VERSION
	  */
	 /*
public static void visualizePetrinet(Petrinet petriNet) {
        // Create a GraphStream graph
        Graph graph = new SingleGraph("Petri Net");

        // Configure the visual appearance of the graph
        graph.addAttribute("ui.stylesheet", "node { size: 20px; text-size: 15px; }");

        // Add places as nodes with unique identifiers
        for (Place place : petriNet.getPlaces()) {
            String placeId = "P" + place.getId();
            Node placeNode = graph.addNode(placeId);
            placeNode.addAttribute("ui.label", place.getLabel());
            placeNode.addAttribute("ui.class", "place");
        }

        // Add transitions as nodes with unique identifiers
        for (Transition transition : petriNet.getTransitions()) {
            String transitionId = "T" + transition.getId();
            Node transitionNode = graph.addNode(transitionId);
            transitionNode.addAttribute("ui.label", transition.getLabel());
            transitionNode.addAttribute("ui.class", "transition");
        }

        // Add arcs as edges
        for (Place place : petriNet.getPlaces()) {
            for (Transition transition : petriNet.getTransitions()) {
                Arc arc = petriNet.getArc(place, transition);
                if (arc != null) {
                    int arcWeight = arc.getWeight();
                    if (arcWeight > 0) {
                        String edgeId = place.getId() + "_" + transition.getId();
                        graph.addEdge(edgeId, "P" + place.getId(), "T" + transition.getId(), true);
                    }
                }
            }
        }

        // Display the graph
        graph.display();
    }
    */
	 


}

