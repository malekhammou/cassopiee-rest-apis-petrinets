package org.processmining.sequencepredictionwithpetrinets.utils;

import static org.processmining.sequencepredictionwithpetrinets.utils.PetrinetUtils.transitionToEventClassMapperByLabel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.acceptingpetrinet.models.AcceptingPetriNet;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.onlineconformance.algorithms.IncrementalReplayer;
import org.processmining.onlineconformance.algorithms.IncrementalReplayer.SearchAlgorithm;
import org.processmining.onlineconformance.models.IncrementalReplayResult;
import org.processmining.onlineconformance.models.ModelSemanticsPetrinet;
import org.processmining.onlineconformance.models.PartialAlignment;
import org.processmining.onlineconformance.parameters.IncrementalRevBasedReplayerParametersImpl;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayAlgorithm;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedprefix.CostBasedPrefixAlg;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedprefix.CostBasedPrefixParam;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;

import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import nl.tue.astar.AStarException;

public class AlignmentUtils {
	public static IncrementalReplayResult calculateAlignmentWithTimeout(final XLog log, final AcceptingPetriNet apn, IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, long maxTimeMillis, boolean verbose){
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>>> future = executor.submit(new RunnableWrapperForPrefixAlignments(log, apn, replayer, verbose));
        try {
			return future.get(maxTimeMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
			//e.printStackTrace();
			future.cancel(true);
			// if timed out, replayer might be in inconsistent state. repair
			replayer = makeReplayer(log, apn);
			System.err.println("TIMEOUT");
        }
        executor.shutdownNow();
        return null;
	}
	
	public static IncrementalReplayResult calculateAlignment(final XLog log, final AcceptingPetriNet apn, IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, boolean verbose){
		return calculateAlignment(null, log, apn, replayer, verbose);
	}
	
	public static IncrementalReplayResult calculateAlignment(PluginContext context, final XLog log, final AcceptingPetriNet apn, IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, boolean verbose){
		IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>> replayResult = IncrementalReplayResult.Factory
				.construct(IncrementalReplayResult.Impl.HASH_MAP);
		int i = 1;
		for (XTrace t : log) {
			if (!t.isEmpty()) {
				if(verbose)
					System.out.println("Trained on trace "+i+" of "+log.size());
	            String caseId = XConceptExtension.instance().extractName(t);
                replayResult.put(caseId, new ArrayList<PartialAlignment<String, Transition, Marking>>());
                PartialAlignment<String, Transition, Marking> partialAlignment = null;
                for (XEvent e : t) {
                    partialAlignment = replayer.processEvent(caseId, e.getAttributes().get("concept:name").toString());
                    replayResult.get(caseId).add((PartialAlignment<String, Transition, Marking>) partialAlignment);
                }
			}
			i++;
		}
		return replayResult;
	}
	
	/*
	 * Uses the implementation from the OnlineConformance package
	 * Alternative method calculateAlignment2 uses the prefix-alignment implementation from the PNetReplayer package
	 */
	public static IncrementalReplayResult calculateAlignment(PluginContext context, final XLog log, final AcceptingPetriNet apn, boolean verbose){
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = makeReplayer(
				log, apn);
		return calculateAlignment(context, log, apn, replayer, verbose);
	}

	public static IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> makeReplayer(
			final XLog log, final AcceptingPetriNet apn) {
		IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition> parameters = new IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>();
		parameters.setExperiment(false);
		parameters.setModel(apn.getNet());
		parameters.setUseMultiThreading(true);
		parameters.setNumberOfThreads(8);
		parameters.setSynchronousMoveCosts(1);
		parameters.setLookBackWindow(Integer.MAX_VALUE);
		parameters.setSearchAlgorithm(SearchAlgorithm.A_STAR);
		
		ModelSemanticsPetrinet<Marking> modelSemantics = ModelSemanticsPetrinet.Factory.construct(apn.getNet());
		// set mapping of Transitions to Event Classes
        Map<Transition, String> labelsInPN = new HashMap<Transition, String>();
        
        // Set Model Move Costs
        TObjectDoubleMap<Transition> modelMoveCosts = new TObjectDoubleHashMap<>();
		for (Transition t : apn.getNet().getTransitions()) {
	        if (!t.isInvisible()) {
	        	labelsInPN.put(t, t.getLabel());
	        	modelMoveCosts.put(t, 100);
	        }else{
	        	modelMoveCosts.put(t, 1);
	        }
		}
        parameters.setModelElementsToLabelMap(labelsInPN);
		parameters.setModelMoveCosts(modelMoveCosts);
		// Set Log Move Costs
		TObjectDoubleMap<String> logMoveCosts = new TObjectDoubleHashMap<>();
		XEventClasses classes = XEventClasses.deriveEventClasses(new XEventNameClassifier(), log);
		for(XEventClass xec : classes.getClasses())
			logMoveCosts.put(xec.getId(), 100);
		parameters.setLabelMoveCosts(logMoveCosts);
		
		Map<String, PartialAlignment<String, Transition, Marking>> store = new HashMap<>();
		IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer = IncrementalReplayer.Factory
                        .construct(apn.getInitialMarking(), apn.getFinalMarkings().toArray(new Marking[apn.getFinalMarkings().size()])[0], store, modelSemantics, parameters, labelsInPN, IncrementalReplayer.Strategy.REVERT_BASED);
		return replayer;
	}
	
	/*
	 * Uses the implementation from the PNetReplayer package
	 * Alternative method calculateAlignment uses the prefix-alignment implementation from the OnlineConformance package
	 */
	public static PNRepResult calculateAlignment2(PluginContext context, XLog log, AcceptingPetriNet apn){
		Petrinet ptnet = apn.getNet();
		TransEvClassMapping oldMap = transitionToEventClassMapperByLabel(log, ptnet);

		CostBasedPrefixParam parameter = new CostBasedPrefixParam();
		parameter.setInitialMarking(apn.getInitialMarking());
		//parameter.setFinalMarkings(apn.getFinalMarkings().toArray(new Marking[apn.getFinalMarkings().size()]));
		
		parameter.setGUIMode(false);
		parameter.setCreateConn(false);
		//parameter.setEpsilon(0.5);
		parameter.setInappropriateTransFireCost(100000);
		parameter.setSelfExecInviTaskCost(1);
		parameter.setReplayedEventCost(0);
		parameter.setSelfExecRealTaskCost(40);
		parameter.setSkippedEventCost(40);
		parameter.setHeuristicDistanceCost(0);
		parameter.setMaxNumOfStates(80000);
		parameter.setNumThreads(Runtime.getRuntime().availableProcessors());
		
		// select algorithm without ILP
		IPNReplayAlgorithm replayer = new CostBasedPrefixAlg();
		
		PNRepResult pnRepResult = null;
		try {
			pnRepResult = replayer.replayLog(context, ptnet, log, oldMap, parameter);
		} catch (AStarException e) {
			e.printStackTrace();
		} catch(Throwable e){
			e.printStackTrace();
		}
		return pnRepResult;
	}
}
