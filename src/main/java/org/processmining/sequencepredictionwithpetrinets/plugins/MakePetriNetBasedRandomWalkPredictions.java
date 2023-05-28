package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.processmining.acceptingpetrinet.models.AcceptingPetriNet;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.PetrinetSemantics;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;
import org.processmining.sequencepredictionwithpetrinets.utils.AlignmentUtils;
import org.processmining.sequencepredictionwithpetrinets.utils.SamplingUtils;


@Plugin(
		name = "Test Petri Net Classifier using PNetReplayer Prefix-Alignments", 
		parameterLabels = {"Log", "Model"}, 
	    returnLabels = {"XLog"}, 
	    returnTypes = { XLog.class }
		)
public class MakePetriNetBasedRandomWalkPredictions {
	private final int MAX_LOOKAHEAD = 1;
	private final int MONTE_CARLO_ITERATIONS = 1000; 
	
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Test Petri Net Classifier using PNetReplayer Prefix-Alignments", requiredParameterLabels = {0, 1})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog testLog, AcceptingPetriNet apn){
		PetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(apn.getNet());
		
		XLog testLogClone = (XLog) testLog.clone();
		int traceCount = 0;
		for(XTrace trace : testLogClone){
			traceCount++;
			System.out.println("trace "+traceCount+" of "+testLog.size());
			/*
			if(trace.getAttributes().containsKey("concept:name"))
				System.out.println("Start aligning trace "+trace.getAttributes().get("concept:name").toString());
			else
				System.out.println("Start aligning unnamed trace");
			*/
			for(int prefixLength=0; prefixLength<trace.size(); prefixLength++){
				XLog prefixForalignmentLog = (XLog) testLog.clone();
				prefixForalignmentLog.clear();
				XTrace traceClone = (XTrace) trace.clone();
				prefixForalignmentLog.add(traceClone);
				for(int removalCounter=0; removalCounter<(trace.size()-prefixLength); removalCounter++)
					traceClone.remove(traceClone.size()-1);
				
				boolean markingObtained = false;
				while(!markingObtained){
					PNRepResult alignment = AlignmentUtils.calculateAlignment2(context, prefixForalignmentLog, apn);
					SyncReplayResult s = alignment.toArray(new SyncReplayResult[1])[0];
					// go to the marking selected by the prefix alignment
					List<Object> nodeInstances = s.getNodeInstance();
					semantics.setCurrentState(apn.getInitialMarking());
					boolean exceptionSeen = false;
					for(int alignment_step=0; alignment_step<nodeInstances.size(); alignment_step++){
						Object nodeInstance = nodeInstances.get(alignment_step);
						if(nodeInstance instanceof Transition){
							try {
								System.out.println(semantics.getCurrentState());
								System.out.println(((Transition) nodeInstance).getLabel());
								semantics.executeExecutableTransition((Transition) nodeInstance);
								System.out.println(semantics.getCurrentState());
								System.out.println();
							} catch (IllegalTransitionException e) {
								e.printStackTrace();
								exceptionSeen = true;
								System.err.println("error, trying again");
								break;
							}
						}
					}
					if(!exceptionSeen)
						markingObtained = true; // marking obtained if no exception for all steps
				}
				// simulate next steps starting from this marking
				Map<Integer, Map<String, Integer>> lookAheadToActivityMap = new HashMap<Integer, Map<String, Integer>>();
				Marking newM = semantics.getCurrentState(); // temporary clone of Marking m, allowing it to be modified for multiple random walk steps
				for(int iteration = 0; iteration<MONTE_CARLO_ITERATIONS; iteration++){
					 semantics.setCurrentState(newM);
					int lookAhead = 1;
					//trace.getAttributes().put("predicted_"+lookAhead, new XAttributeLiteralImpl("predicted_"+lookAhead, nextEvent));
					while(!apn.getFinalMarkings().contains(semantics.getCurrentState())&&lookAhead<=MAX_LOOKAHEAD){
						Object[] result = SamplingUtils.takeMonteCarloSample(apn, semantics);
						String sampleString = (String) result[0];
						
						Map<String, Integer> activityCountsMap = lookAheadToActivityMap.get(lookAhead);
						if(activityCountsMap==null)
							activityCountsMap = new HashMap<String, Integer>();
						Integer currentCount = activityCountsMap.get(sampleString);
						if(currentCount==null)
							currentCount = 0;
						currentCount++;
						activityCountsMap.put(sampleString, currentCount);
						lookAheadToActivityMap.put(lookAhead, activityCountsMap);
													
						lookAhead++;
					}
				}
				for(int lookAhead = 1; lookAhead<=MAX_LOOKAHEAD; lookAhead++){
					Map<String, Integer> activityCountsMap = lookAheadToActivityMap.get(lookAhead);
					if(activityCountsMap!=null){
						for(String activity : activityCountsMap.keySet()){
							int count = activityCountsMap.get(activity);
							trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", ((double)count)/MONTE_CARLO_ITERATIONS));
						}
					}
				}					
				
			}
		}
		return testLogClone;
	}
}