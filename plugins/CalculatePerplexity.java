package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.HashMap;
import java.util.Map;

import org.deckfour.xes.classification.XEventClasses;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.model.XAttributeContinuous;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;

@Plugin(
		name = "Calculate Brier Score / Perplexity", 
		parameterLabels = {"log"}, 
	    returnLabels = {"Null"}, 
	    returnTypes = { String.class }
		)
public class CalculatePerplexity {
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Calculate Brier Score / Perplexity", requiredParameterLabels = {0})
	public String calculatePerplexity(PluginContext context, XLog log){
		double crossEntropy = 0;
		double brier = 0;
		int totalPredictions = 0;
		for(XTrace trace : log){
			int index = 0;
			for(XEvent event : trace){
				String target = event.getAttributes().get("concept:name").toString();
				Map<String, Double> probabilities = new HashMap<String, Double>();
				for(String attributeKey: trace.getAttributes().keySet()){
					String prefixPart = "prob(p"+index;
					if(attributeKey.startsWith(prefixPart)){
						String remainder = attributeKey.replace(prefixPart, "");
						if(remainder.startsWith("l1")){
							String aName = remainder.substring(3, remainder.length()-1); // remove prefix "l1=" and suffix ")"
							Double probability = 0d;
							if(trace.getAttributes().containsKey(prefixPart+"l1="+aName+")")){
								probability = ((XAttributeContinuous) trace.getAttributes().get(prefixPart+"l1="+aName+")")).getValue();
							}else{
								System.err.println("[ "+prefixPart+"l1="+aName+")"+" ]"+" not found as attribute key");
							}
							probabilities.put(aName, probability);
						}
					}
				}
				if(!probabilities.isEmpty()){
					totalPredictions++;
					crossEntropy += calculateCrossEntropy(probabilities, target);
					brier += calculateBrier(probabilities, target);
					if(calculateBrier(probabilities, target)>1){
						System.err.println(calculateBrier(probabilities, target));
					}
						
					System.out.println(calculateBrier(probabilities, target));
				}else{
					System.out.println("ERROR!");
				}
				index++;
			}
			System.out.println();
		}
		System.out.println("Total Preds   = "+totalPredictions);
		double perplexity = Math.pow(2, natsToBits(crossEntropy)/totalPredictions);
		System.out.println("Cross Entropy = "+crossEntropy/totalPredictions);
		XEventClasses classes = XEventClasses.deriveEventClasses(new XEventNameClassifier(), log);
		System.out.println("Brier score   = "+brier/(totalPredictions*classes.size()));
		System.out.println("classes.size  = "+classes.size());
		return "Perplexity = "+perplexity;
	}
	
	private double calculateBrier(final Map<String, Double> probabilities, final String target) {
		double brier = 0;
		boolean targetSeen = false;
		double sum = 0;
		for(String key : probabilities.keySet()){
			sum+=probabilities.get(key);
			if(key.equals(target)){
				targetSeen = true;
				brier += Math.pow(probabilities.get(key)-1, 2);
			}else{
				brier += Math.pow(probabilities.get(key), 2);
			}
		}
		if(sum<0.99 || sum>1.01)
			System.err.println("sums to: "+sum);
		if(!targetSeen)
			brier += Math.pow(0-1, 2);
		return brier;
	}

	private static double natsToBits(double nats){
		return nats * (Math.log(Math.E)/Math.log(2)); // 1 nat = log_2(e) bits 
	}

	private static double calculateCrossEntropy(final Map<String, Double> probabilities, final String target) {
		double crossEntropy = 0;
		double epsilon = 1E-7;
		boolean targetSeen = false;
		for(String key : probabilities.keySet()){
			if(key.equals(target)){
				targetSeen = true;
				crossEntropy += - (1-epsilon) * Math.log(clipWithEpsilon(probabilities.get(key), epsilon));
			}else{
				crossEntropy += - epsilon * Math.log(clipWithEpsilon(probabilities.get(key), epsilon));
			}
		}
		if(!targetSeen)
			crossEntropy += - (1-epsilon) * Math.log(clipWithEpsilon(0, epsilon));
		return crossEntropy;
	}
	
	private static double clipWithEpsilon(double value, double epsilon){
		if(value<=0)
			return epsilon;
		if(value>=1)
			return (1-epsilon);
		else 
			return value;
	}
}
