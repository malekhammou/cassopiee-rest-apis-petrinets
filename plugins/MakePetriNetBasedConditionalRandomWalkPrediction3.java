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
		name = "Train/Test Petri Net Classifier with Marking-Conditional Likelihoods using OnlineConformance Prefix-Alignments With TimeOut", 
		parameterLabels = {"Train Log",  "Test Log", "Model"}, 
	    returnLabels = {"XLog"}, 
	    returnTypes = { XLog.class }
		)
public class MakePetriNetBasedConditionalRandomWalkPrediction3 {
	private final int MAX_LOOKAHEAD = 1;
	private final int MONTE_CARLO_ITERATIONS = 1000;
	private Map<Marking, Map<Transition, Integer>> transitionFiringsPerMarking;
	private Map<Marking, Map<Transition, Double>> conditionalProbabilityPerMarking;
	private static final long MAX_ALIGNMENT_MILLIS = 10000;
	
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Train/Test Petri Net Classifier with Marking-Conditional Likelihoods using OnlineConformance Prefix-Alignments With TimeOut", requiredParameterLabels = {0, 1, 2})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog trainLog, XLog testLog, AcceptingPetriNet apn){
		// Get complete set of activities in training data (i.e., all possible labels)
		Set<String> trainActivities = new HashSet<String>();
		for(XTrace trace : trainLog){
			for(XEvent event : trace){
				if(event.getAttributes().containsKey("concept:name"))
					trainActivities.add(event.getAttributes().get("concept:name").toString());
			}
		}
		
		// POPULATE "conditionalProbabilityPerMarking" using trainLog
		transitionFiringsPerMarking = new HashMap<Marking, Map<Transition, Integer>>();
		PetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(apn.getNet());		
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = AlignmentUtils.makeReplayer(trainLog, apn);

		//PNRepResult alignmentTrain = calculateAlignment(context, trainLog, apn);
		IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>> alignment = AlignmentUtils.calculateAlignmentWithTimeout(trainLog, apn, replayer, trainLog.size() * MAX_ALIGNMENT_MILLIS, true);
		if(alignment==null){
			System.err.println("ALIGNING TRAINING LOG TAKES MORE THAN "+MAX_ALIGNMENT_MILLIS+", GIVING UP NOW");
		}else{
			for(XTrace trace : trainLog){
				String caseId = XConceptExtension.instance().extractName(trace);
				//for(int prefixSize=0; prefixSize<alignment.get(caseId).size(); prefixSize++){
				PartialAlignment<String, Transition, Marking> prefixAlignment = alignment.get(caseId).get(alignment.get(caseId).size()-1);
				semantics.setCurrentState(apn.getInitialMarking());
				//if(prefixSize>0){
					//PartialAlignment<String, Transition, Marking> prefixAlignment = alignment.get(caseId).get(alignment.get(caseId).size()-1);
					// go to marking after prefix
					for(int alignment_step=0; alignment_step<prefixAlignment.size(); alignment_step++){
						Move<String, Transition> nodeInstance = prefixAlignment.get(alignment_step);
						if(nodeInstance.getTransition()!=null){
							try {
								//System.out.println(semantics.getCurrentState());
								//System.out.println(nodeInstance.getTransition().getLabel());
								Marking markingBeforeFiring = new Marking(semantics.getCurrentState());
								semantics.executeExecutableTransition(nodeInstance.getTransition());
								//System.out.println(semantics.getCurrentState());
								//System.out.println();
								Map<Transition, Integer> currentTransitionFirings = transitionFiringsPerMarking.get(markingBeforeFiring);
								if(currentTransitionFirings==null)
									currentTransitionFirings=new HashMap<Transition, Integer>();
								Integer currentCount = currentTransitionFirings.get(nodeInstance.getTransition());
								if(currentCount==null)
									currentCount = 0;
								currentCount++;
								currentTransitionFirings.put(nodeInstance.getTransition(), currentCount);
								transitionFiringsPerMarking.put(markingBeforeFiring, currentTransitionFirings);

							} catch (IllegalTransitionException e) {
								e.printStackTrace();
								break;
							}
						}
					}
				//}
				//for(Transition t : semantics.getExecutableTransitions()){
					//if(t.getLabel().equals(label)){

					//}
				//}
				//}
			}
		}
		conditionalProbabilityPerMarking = new HashMap<Marking, Map<Transition, Double>>();
		for(Marking m : transitionFiringsPerMarking.keySet()){
			Map<Transition, Integer> transitionCounts = transitionFiringsPerMarking.get(m);
			Map<Transition, Double> transitionLikelihoods = new HashMap<Transition, Double>();
			int totalCount = 0;
			for(Transition t : transitionCounts.keySet()){
				totalCount += transitionCounts.get(t);
			}
			for(Transition t : transitionCounts.keySet()){
				transitionLikelihoods.put(t, ((double) transitionCounts.get(t))/totalCount);
			}
			conditionalProbabilityPerMarking.put(m, transitionLikelihoods);
		}
		
		// Do Monte Carlo simulations on Test Log using the transition likelihoods that are learned from the Train Log
		XLog testLogClone = (XLog) testLog.clone();
		XLog prefixForalignmentLog = (XLog) testLog.clone();
		int traceCount = 0;
		for(XTrace trace : testLogClone){
			traceCount++;
			String caseId = XConceptExtension.instance().extractName(trace);
			System.out.println("TESTING: trace "+traceCount+" of "+testLog.size());
			replayer = AlignmentUtils.makeReplayer(testLogClone, apn);
			prefixForalignmentLog.clear();
			XTrace traceClone = (XTrace) trace.clone();
			prefixForalignmentLog.add(traceClone);
			alignment = AlignmentUtils.calculateAlignmentWithTimeout(prefixForalignmentLog, apn, replayer, MAX_ALIGNMENT_MILLIS, false);

			/*
			if(trace.getAttributes().containsKey("concept:name"))
				System.out.println("Start aligning trace "+trace.getAttributes().get("concept:name").toString());
			else
				System.out.println("Start aligning unnamed trace");
			*/
			for(int prefixLength=0; prefixLength<trace.size(); prefixLength++){
				// Move to the correct marking
				semantics.setCurrentState(apn.getInitialMarking());
				if(prefixLength>0){
					if(alignment != null){
						PartialAlignment<String, Transition, Marking> traceAlignment = alignment.get(caseId).get(prefixLength-1);
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
				Marking prefixM = semantics.getCurrentState(); // temporary clone of Marking m, allowing it to be modified for multiple random walk steps
				for(int iteration = 0; iteration<MONTE_CARLO_ITERATIONS; iteration++){
					int lookAhead = 1;
					semantics.setCurrentState(prefixM);
					//trace.getAttributes().put("predicted_"+lookAhead, new XAttributeLiteralImpl("predicted_"+lookAhead, nextEvent));
					while(lookAhead<=MAX_LOOKAHEAD){
						Object[] result = null;
						if(conditionalProbabilityPerMarking.containsKey(prefixM))
							result = SamplingUtils.takeMonteCarloSample(apn, semantics, conditionalProbabilityPerMarking);
						else
							result = SamplingUtils.takeMonteCarloSample(apn, semantics); // if marking was never observed in training data, fall back to uniform marking
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
					int totalCount = 0;
					for(String activity : trainActivities){
						if(activityCountsMap!=null){
							if(activityCountsMap.containsKey(activity)){
								int count = activityCountsMap.get(activity);
								totalCount += count;
								trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", ((double)count)/MONTE_CARLO_ITERATIONS));
							}else{ // Always write 0 probabilities, Brier score method expects probability vector to be complete
								trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"="+activity+")", 0d));
							}
						}else{ // if marking was never observed during training, use uniform distribution
							System.err.println("THIS SHOULD NEVER HAPPEN, INVESTIGATE");
						}
					}
					if(totalCount<MONTE_CARLO_ITERATIONS) // predict 'END' if some simulations ended up not predicting something else 
						trace.getAttributes().put("prob(p"+prefixLength+"l"+lookAhead+"=END)", new XAttributeContinuousImpl("prob(p"+prefixLength+"l"+lookAhead+"=END)", ((double)MONTE_CARLO_ITERATIONS-totalCount)/MONTE_CARLO_ITERATIONS));
				}					
			}		
		}
		return testLogClone;
	}
}