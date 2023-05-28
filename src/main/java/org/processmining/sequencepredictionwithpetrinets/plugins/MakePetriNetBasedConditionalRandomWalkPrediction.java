package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.classification.XEventClass;
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
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;
import org.processmining.sequencepredictionwithpetrinets.utils.AlignmentUtils;
import org.processmining.sequencepredictionwithpetrinets.utils.SamplingUtils;

@Plugin(
		name = "Train/Test Petri Net Classifier with Marking-Conditional Likelihoods using PNetReplayer Prefix-Alignments", 
		parameterLabels = {"Train Log",  "Test Log", "Model"}, 
	    returnLabels = {"XLog"}, 
	    returnTypes = { XLog.class }
		)
public class MakePetriNetBasedConditionalRandomWalkPrediction {
	private final int MAX_LOOKAHEAD = 1;
	private final int MONTE_CARLO_ITERATIONS = 1000;
	private Map<Marking, Map<Transition, Integer>> transitionFiringsPerMarking;
	private Map<Marking, Map<Transition, Double>> conditionalProbabilityPerMarking;
	
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Train/Test Petri Net Classifier with Marking-Conditional Likelihoods using PNetReplayer Prefix-Alignments", requiredParameterLabels = {0, 1, 2})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog trainLog, XLog testLog, AcceptingPetriNet apn){
		// POPULATE "conditionalProbabilityPerMarking" using trainLog
		transitionFiringsPerMarking = new HashMap<Marking, Map<Transition, Integer>>();
		//int traceCount = 0;
		
		//traceCount++;
		//System.out.println("TRAINING: trace "+traceCount+" of "+trainLog.size());
		//for(int prefixLength=1; prefixLength<trace.size(); prefixLength++){
		//XLog prefixForalignmentLog = (XLog) trainLog.clone();
		//prefixForalignmentLog.clear();
		//XTrace traceClone = (XTrace) trace.clone();
		//prefixForalignmentLog.add(traceClone);
		//for(int removalCounter=0; removalCounter<(trace.size()-prefixLength); removalCounter++)
		//	traceClone.remove(traceClone.size()-1);
		PNRepResult alignmentTrain = AlignmentUtils.calculateAlignment2(context, trainLog, apn);
		PetrinetSemantics semantics = new EfficientPetrinetSemanticsImpl(apn.getNet());		
		for(SyncReplayResult s : alignmentTrain){
			// go to the marking selected by the prefix alignment
			List<Object> nodeInstances = s.getNodeInstance();
			Petrinet pn = apn.getNet();
			semantics.setCurrentState(apn.getInitialMarking());
			Marking oldM = null;
			Transition lastTransition = null;
			for(int alignment_step=0; alignment_step<nodeInstances.size(); alignment_step++){
				Object nodeInstance = nodeInstances.get(alignment_step);
				if(nodeInstance instanceof Transition){
					oldM = semantics.getCurrentState();
					lastTransition = (Transition) nodeInstance;
					try {
						semantics.executeExecutableTransition((Transition) nodeInstance);
					} catch (IllegalTransitionException e) {
						e.printStackTrace();
					}
					
					Map<Transition, Integer> currentTransitionFirings = transitionFiringsPerMarking.get(oldM);
					if(currentTransitionFirings==null)
						currentTransitionFirings=new HashMap<Transition, Integer>();
					Integer currentCount = currentTransitionFirings.get(lastTransition);
					if(currentCount==null)
						currentCount = 0;
					currentCount += s.getTraceIndex().size();
					currentTransitionFirings.put(lastTransition, currentCount);
					transitionFiringsPerMarking.put(oldM, currentTransitionFirings);
				}else{
					if(nodeInstance instanceof XEventClass){
						System.err.println("LogMove on: "+((XEventClass) nodeInstance).getId());
					}
				}
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
		}
		
		// Do Monte Carlo simulations on Test Log using the transition likelihoods that are learned from the Train Log
		XLog testLogClone = (XLog) testLog.clone();
		int traceCount = 0;
		for(XTrace trace : testLogClone){
			traceCount++;
			System.out.println("TESTING: trace "+traceCount+" of "+testLog.size());
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
				
				PNRepResult alignment = AlignmentUtils.calculateAlignment2(context, prefixForalignmentLog, apn);
				for(SyncReplayResult s : alignment){
					// go to the marking selected by the prefix alignment
					List<Object> nodeInstances = s.getNodeInstance();
					semantics.setCurrentState(apn.getInitialMarking());
					for(int alignment_step=0; alignment_step<nodeInstances.size(); alignment_step++){
						Object nodeInstance = nodeInstances.get(alignment_step);
						if(nodeInstance instanceof Transition)
							try {
								semantics.executeExecutableTransition((Transition) nodeInstance);
							} catch (IllegalTransitionException e) {
								e.printStackTrace();
							}
						
					} 
					// simulate next steps starting from this marking
					Map<Integer, Map<String, Integer>> lookAheadToActivityMap = new HashMap<Integer, Map<String, Integer>>();
					Marking prefixM = semantics.getCurrentState(); // temporary clone of Marking m, allowing it to be modified for multiple random walk steps
					for(int iteration = 0; iteration<MONTE_CARLO_ITERATIONS; iteration++){
						semantics.setCurrentState(prefixM);
						int lookAhead = 1;
						//trace.getAttributes().put("predicted_"+lookAhead, new XAttributeLiteralImpl("predicted_"+lookAhead, nextEvent));
						while(!apn.getFinalMarkings().contains(semantics.getCurrentState())&&lookAhead<=MAX_LOOKAHEAD){
							Object[] result = SamplingUtils.takeMonteCarloSample(apn, semantics, conditionalProbabilityPerMarking);
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
						//System.out.println("Predicted activity: "+nextEvent+", actual activity was: "+trace.getAttributes().get("nextEvent").toString());
					}					
				}		
			}
		}
		return testLogClone;
	}
}