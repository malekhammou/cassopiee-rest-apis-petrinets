package org.processmining.sequencepredictionwithpetrinets.spmf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeContinuousImpl;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;

import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.Item;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.database.Sequence;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.predictor.LZ78.LZ78Predictor;
import ca.pfv.spmf.algorithms.sequenceprediction.ipredict.predictor.LZ78.LZNode;

@Plugin(
		name = "Train/Test LZ78", 
		parameterLabels = {"Train Log",  "Test Log"}, 
	    returnLabels = {"XLog"}, 
	    returnTypes = { XLog.class }
		)
public class LZ78EventPredictor {
	private int traceId = 1;
	private Map<String, Integer> actsToIntMap = new HashMap<String, Integer>();
	private Map<Integer, String> intsToActsMap = new HashMap<Integer, String>();
	LZ78Predictor predictor = null;
	
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Train/Test LZ78", requiredParameterLabels = {0, 1})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog trainLog, XLog testLog){
		// Get complete set of activities in training data (i.e., all possible labels)
		Set<String> trainActivities = new HashSet<String>();
		int currentKey = 1;
		for(XTrace trace : trainLog){
			for(XEvent event : trace){
				if(event.getAttributes().containsKey("concept:name")) {
					String cName = event.getAttributes().get("concept:name").toString();
					trainActivities.add(cName);
					if(!actsToIntMap.containsKey(cName)) {
						actsToIntMap.put(cName, currentKey);
						intsToActsMap.put(currentKey, cName);
						currentKey++;
					}
				}
			}
		}
		
		// MODEL TRAINING
		System.out.println("START TRAINING");
		predictor = new LZ78Predictor();
		List<Sequence> sequenceDatabase = xLogToSequenceDatabase(trainLog);
		predictor.Train(sequenceDatabase);
		System.out.println("TRAINING FINISHED");
		//
		
		// MAKE PREDICTIONS
		XLog testLogClone = (XLog) testLog.clone();
		int traceCount = 0;
		System.out.println("START PREDICTING");
		for(XTrace trace : testLogClone){
			traceCount++;
			System.out.println("TESTING: trace "+traceCount+" of "+testLog.size());

			for(int prefixLength=0; prefixLength<trace.size(); prefixLength++){
				// Make predictions for prefix
				Sequence prefixSequence = xTracePrefixToSequence(trace, prefixLength);
				Map<String, Double> probs = Predict(prefixSequence);
				for(String activity : trainActivities){
					// get likelihood for activity
					if(probs.containsKey(activity))
						trace.getAttributes().put("prob(p"+prefixLength+"l1="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l1="+activity+")", probs.get(activity)));
					else
						trace.getAttributes().put("prob(p"+prefixLength+"l1="+activity+")", new XAttributeContinuousImpl("prob(p"+prefixLength+"l1="+activity+")", 0));
				}
			}					
			
		}
		return testLogClone;
	}

	public Map<String, Double> Predict(Sequence target) {
		
		//Map each item from the alphabet to a probability
		HashMap<Integer, Double> results = new HashMap<Integer, Double>();
		
		//keeping the last X items from the target sequence
		//X being the order of this predictor.
		List<Integer> lzPhrase = new ArrayList<Integer>();
		List<Integer> prefix = new ArrayList<Integer>();
		List<Item> lastItems = target.getLastItems(predictor.order, 0).getItems();
		Collections.reverse(lastItems);
		
		//for each order, starting with the highest one
		for(Item item : lastItems) {
			
			//adding the current element in reverse order
			prefix.add(0, item.val);
			
			LZNode parent = predictor.mDictionary.get(prefix);
			
			//Stop the prediction if the current node does not exists
			//because if X does not exists than any node more precise than X cannot exists
			if(parent == null) {
				break;
			}
			
			//calculating the probability of the escape
			int escapeK = parent.getSup() - parent.getChildSup(); 
			
			//for each child of this prefix
			for(Integer value : parent.children) {
				
				lzPhrase = new ArrayList<Integer>(prefix);
				lzPhrase.add(value);
 				LZNode child = predictor.mDictionary.get(lzPhrase);
				
				if(child != null) {
					
					//prob for this item for order k+1
					Double probK1 = results.getOrDefault(value, 0d);
					Double probK = ((double) child.getSup() / parent.getSup()) + (escapeK * probK1);
					results.put(value, probK);	
				}
			}
		}
		
		//generating a prediction from the most probable item in the dictionary
		Map<String, Double> actResults = new HashMap<String, Double>();
		double sum = 0;
		for(Integer key : results.keySet()) 
			sum += results.get(key);
		for(Integer key : results.keySet()) 
			actResults.put(intsToActsMap.get(key), results.get(key)/sum);
		
		return actResults;
	}

	
	private List<Sequence> xLogToSequenceDatabase(XLog trainLog) {
		List<Sequence> sequenceDatabase = new ArrayList<Sequence>();
		for(XTrace trace : trainLog){
			Sequence traceAsSequence = xTraceToSequence(trace);
			sequenceDatabase.add(traceAsSequence);
		}
		return sequenceDatabase;
	}

	private Sequence xTracePrefixToSequence(XTrace trace, int prefixLength) {
		List<Item> traceAsItemList = new ArrayList<Item>();
		int currentLength = 0;
		traceAsItemList.add(new Item(0));
		for(XEvent event : trace) {
			if(currentLength >= prefixLength)
				break;
			if(event.getAttributes().containsKey("concept:name")) 
				traceAsItemList.add(new Item(actsToIntMap.get(event.getAttributes().get("concept:name").toString())));
			currentLength++;
		}
		Sequence traceAsSequence = new Sequence(traceId, traceAsItemList);
		traceId++;
		return traceAsSequence;
	}
	
	private Sequence xTraceToSequence(XTrace trace) {
		List<Item> traceAsItemList = new ArrayList<Item>();
		traceAsItemList.add(new Item(0));
		for(XEvent event : trace) {
			if(event.getAttributes().containsKey("concept:name"))
				traceAsItemList.add(new Item(actsToIntMap.get(event.getAttributes().get("concept:name").toString())));
		}
		Sequence traceAsSequence = new Sequence(traceId, traceAsItemList);
		traceId++;
		return traceAsSequence;
	}
}