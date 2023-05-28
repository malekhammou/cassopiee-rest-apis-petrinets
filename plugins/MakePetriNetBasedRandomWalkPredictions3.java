package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.processmining.acceptingpetrinet.models.AcceptingPetriNet;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.PetrinetSemantics;
import org.processmining.models.semantics.petrinet.impl.EfficientPetrinetSemanticsImpl;
import org.processmining.onlineconformance.algorithms.IncrementalReplayer;
import org.processmining.onlineconformance.models.IncrementalReplayResult;
import org.processmining.onlineconformance.models.Move;
import org.processmining.onlineconformance.models.PartialAlignment;
import org.processmining.onlineconformance.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.sequencepredictionwithpetrinets.utils.AlignmentUtils;
import org.processmining.sequencepredictionwithpetrinets.utils.SamplingUtils;


@Plugin(
		name = "Test Petri Net Classifier using OnlineConformance Prefix-Alignments With TimeOut", 
		parameterLabels = {"Train Log",  "Test Log", "Model"}, 
	    returnLabels = {"XLog"}, 
	    returnTypes = { XLog.class }
		)
public class MakePetriNetBasedRandomWalkPredictions3 {
	private final int MAX_LOOKAHEAD = 1;
	private final int MONTE_CARLO_ITERATIONS = 1000; 
	private static final long MAX_ALIGNMENT_MILLIS = 10000;

	
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Test Petri Net Classifier using OnlineConformance Prefix-Alignments With TimeOut", requiredParameterLabels = {0, 1, 2})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog trainLog, XLog testLog, AcceptingPetriNet apn){
		// Get complete set of activities in training data (i.e., all possible labels)
		Set<String> trainActivities = new HashSet<String>();
		for(XTrace trace : trainLog){
			for(XEvent event : trace){
				if(event.getAttributes().containsKey("concept:name"))
					trainActivities.add(event.getAttributes().get("concept:name").toString());
			}
		}
		
		PetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(apn.getNet());		
		XLog testLogClone = (XLog) testLog.clone();
		XLog prefixForalignmentLog = (XLog) testLog.clone();

		int traceCount = 0;

		for(XTrace trace : testLogClone){
			traceCount++;
			System.out.println("trace "+traceCount+" of "+testLog.size());
			String caseId = XConceptExtension.instance().extractName(trace);
			IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = AlignmentUtils.makeReplayer(testLogClone, apn);
			prefixForalignmentLog.clear();
			XTrace traceClone = (XTrace) trace.clone();
			prefixForalignmentLog.add(traceClone);
			IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>> alignment = AlignmentUtils.calculateAlignmentWithTimeout(prefixForalignmentLog, apn, replayer, MAX_ALIGNMENT_MILLIS, false);
			for(int prefixLength=0; prefixLength<trace.size(); prefixLength++){
				semantics.setCurrentState(apn.getInitialMarking());
				if(prefixLength>0){
					if(alignment!=null){
						PartialAlignment<String, Transition, Marking> traceAlignment = alignment.get(caseId).get(prefixLength-1);
						
						//SyncReplayResult s = alignment.toArray(new SyncReplayResult[1])[0];
						// go to the marking selected by the prefix alignment
						for(int alignment_step=0; alignment_step<traceAlignment.size(); alignment_step++){
							Move<String, Transition> nodeInstance = traceAlignment.get(alignment_step);
							if(nodeInstance.getTransition()!=null){
								try {
									//System.out.println(semantics.getCurrentState());
									//System.out.println(nodeInstance.getTransition().getLabel());
									semantics.executeExecutableTransition(nodeInstance.getTransition());
									//System.out.println(semantics.getCurrentState());
									//System.out.println();
								} catch (IllegalTransitionException e) {
									e.printStackTrace();
									break;
								}
							}
						}
					}
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
					for(String activity : trainActivities){
						if(activityCountsMap!=null && activityCountsMap.containsKey(activity)){
							int count = activityCountsMap.get(activity);
							trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", ((double)count)/MONTE_CARLO_ITERATIONS));
						}else{ // Always write 0 probabilities, Brier score method expects probability vector to be complete
							trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", 0d));
						}
					}
				}					
			}
		}
		return testLogClone;
	}
}