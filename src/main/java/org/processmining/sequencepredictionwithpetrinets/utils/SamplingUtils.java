package org.processmining.sequencepredictionwithpetrinets.utils;

import static org.processmining.sequencepredictionwithpetrinets.utils.PetrinetUtils.getEnabledTransitions;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.processmining.acceptingpetrinet.models.AcceptingPetriNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.semantics.IllegalTransitionException;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.models.semantics.petrinet.PetrinetSemantics;

import com.google.common.collect.HashMultiset;

public class SamplingUtils {
	/*
	 * result[0] = sampled String
	 * result[1] = new Marking m
	 */
	public static Object[] takeMonteCarloSample(final AcceptingPetriNet apn, final PetrinetSemantics m, final Map<Marking, Map<Transition, Double>> conditionalProbabilityPerMarking) {
		Set<Transition> enabledTransitions = new HashSet<Transition>(m.getExecutableTransitions());
		if(enabledTransitions.size()>0){
			Transition t = getRandomEnabledTransitionForMarkingUsingLikelihoodWhenAvailable(apn, m.getCurrentState(), conditionalProbabilityPerMarking);
			while(t.isInvisible()){
				try {
					m.executeExecutableTransition(t);
				} catch (IllegalTransitionException e) {
					e.printStackTrace();
				}
				enabledTransitions = new HashSet<Transition>(m.getExecutableTransitions());
				if(enabledTransitions.size()>0 && !apn.getFinalMarkings().contains(m.getCurrentState()))
					t = getRandomEnabledTransitionForMarkingUsingLikelihoodWhenAvailable(apn, m.getCurrentState(), conditionalProbabilityPerMarking);
				else
					break;
			}
			if(!apn.getFinalMarkings().contains(m.getCurrentState())){
				try {
					m.executeExecutableTransition(t);
				} catch (IllegalTransitionException e) {
					e.printStackTrace();
				}
			}
			String nextEvent = null;
			if(t.isInvisible() || apn.getFinalMarkings().contains(m.getCurrentState()))
				nextEvent = "Trace ended";
			else
				nextEvent = t.getLabel();
			return new Object[]{nextEvent, m.getCurrentState()};
		}
		return null;
	}
	
	/*
	 * result[0] = sampled String
	 * result[1] = new Marking m
	 */
	public static Object[] takeMonteCarloSample(final AcceptingPetriNet apn, final PetrinetSemantics m) {
		Set<Transition> enabledTransitions = new HashSet<Transition>(m.getExecutableTransitions());//getEnabledTransitions(apn.getNet(), markingAsMultiSet);
		if(enabledTransitions.size()>0){
			Transition t = getRandomEnabledTransition(enabledTransitions);
			while(t.isInvisible()){
				try {
					m.executeExecutableTransition(t);
				} catch (IllegalTransitionException e) {
					e.printStackTrace();
				}
				enabledTransitions = new HashSet<Transition>(m.getExecutableTransitions());//apn.getNet(), markingAsMultiSet);
				if(t.isInvisible() && enabledTransitions.size()>0 && !apn.getFinalMarkings().contains(m.getCurrentState()))
					t = getRandomEnabledTransition(enabledTransitions);
				else
					break;
			}
			String nextEvent = null;
			if(t.isInvisible() || apn.getFinalMarkings().contains(m.getCurrentState()))
				nextEvent = "Trace ended";
			else
				nextEvent = t.getLabel();
			return new Object[]{nextEvent, m.getCurrentState()};
		}
		return new Object[]{"Trace ended", m.getCurrentState()};
	}
	
	public static Transition getRandomEnabledTransition(final Set<Transition> ts){
		int item = new Random().nextInt(ts.size()); // In real life, the Random object should be rather more shared than this
		int i = 0;
		for(Transition obj : ts)
		{
			if (i == item)
			return obj;
			i = i + 1;
		}
		return null;
	}
	
	private static Transition getRandomEnabledTransitionForMarkingUsingLikelihoodWhenAvailable(final AcceptingPetriNet apn, final Marking newM, final Map<Marking, Map<Transition, Double>> conditionalProbabilityPerMarking) {
		Transition t;
		if(conditionalProbabilityPerMarking.containsKey(newM))
			t = getRandomEnabledTransitionForDistribution(conditionalProbabilityPerMarking.get(newM));
		else{
			HashMultiset<Place> markingAsMultiSet = HashMultiset.create();
			markingAsMultiSet.addAll(newM);
			t = getRandomEnabledTransition(getEnabledTransitions(apn.getNet(), markingAsMultiSet));
		}
		return t;
	}
	
	public static Transition getRandomEnabledTransitionForDistribution(final Map<Transition, Double> likelihoods){
		double rand = Math.random(); // generate a random number in [0,1]
		double F=0;
		// you test if rand is in [F(1)+..+F(i):F(1)+..+F(i)+F(i+1)] it is in this rnge with proba P(i) and therefore if it is in this range you return i
		Transition[] transitions = likelihoods.keySet().toArray(new Transition[likelihoods.size()]);
		for (Transition t : transitions){
		   F+=likelihoods.get(t);
		   if(rand < F)
		       return t;
		}
		return transitions[transitions.length-1];
	}
}
