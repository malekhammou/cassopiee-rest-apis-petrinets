package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.Random;

import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;

@Plugin(
		name = "Make temporal train/test split", 
		parameterLabels = {"Input log"}, 
	    returnLabels = {"Xlog"}, 
	    returnTypes = { XLog.class }
		)
public class TemporalTrainTestSplit {
	Random random;
	
	double trainRatio = 1d/3;
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Make temporal train/test split", requiredParameterLabels = {0})
	public XLog makeSplit(PluginContext context, XLog log){
		XLog[] logs = this.makeSplit(log, trainRatio);
		context.getProvidedObjectManager().createProvidedObject("train", logs[0], XLog.class, context);
		context.getProvidedObjectManager().createProvidedObject("test", logs[1], XLog.class, context);
		return null;
	}
	
	public static XLog[] makeSplit(XLog log, double ratio){
		XLog logTrain = (XLog) log.clone();
		XLog logTest = (XLog) log.clone();
		int testThreshold = (int) (ratio * log.size());
		for(int i=log.size()-1; i>=0; i--){
			if(i>testThreshold){
				logTrain.remove(i);
			}else{
				logTest.remove(i);
			}
		}
		return new XLog[]{logTrain, logTest}; 
	}
	
	public boolean getRandomBoolean(double p){
		if(random == null){
			random = new Random();
		}
		return random.nextFloat() < p;
	}
}
