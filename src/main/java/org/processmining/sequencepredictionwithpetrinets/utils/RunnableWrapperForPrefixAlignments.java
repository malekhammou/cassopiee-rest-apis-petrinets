package org.processmining.sequencepredictionwithpetrinets.utils;

import java.util.concurrent.Callable;

import org.deckfour.xes.model.XLog;
import org.processmining.acceptingpetrinet.models.AcceptingPetriNet;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.onlineconformance.algorithms.IncrementalReplayer;
import org.processmining.onlineconformance.models.IncrementalReplayResult;
import org.processmining.onlineconformance.models.PartialAlignment;
import org.processmining.onlineconformance.parameters.IncrementalRevBasedReplayerParametersImpl;

public class RunnableWrapperForPrefixAlignments implements Callable<IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>>> {

	private XLog log;
	private AcceptingPetriNet apn;
	private IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer;
	private boolean verbose;
	
	public RunnableWrapperForPrefixAlignments(final XLog log, final AcceptingPetriNet apn, IncrementalReplayer<Petrinet, String, Marking, Transition, String, PartialAlignment<String, Transition, Marking>, IncrementalRevBasedReplayerParametersImpl<Petrinet, String, Transition>> replayer, boolean verbose){
		this.log = log;
		this.apn = apn;
		this.replayer = replayer;
		this.verbose = verbose;
	}
	
	public IncrementalReplayResult<String, String, Transition, Marking, PartialAlignment<String, Transition, Marking>> call(){
		if(replayer==null)
			AlignmentUtils.makeReplayer(log, apn);
		
		return AlignmentUtils.calculateAlignment(log, apn, replayer, verbose);
	}
}
