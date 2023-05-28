package org.processmining.sequencepredictionwithpetrinets.plugins;

import java.util.Random;

import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;

@Plugin(
		name = "Make train/test split", 
		parameterLabels = {"Input log"}, 
	    returnLabels = {"Xlog"}, 
	    returnTypes = { XLog.class }
		)
public class TrainTestSplit {
	Random random;
	
	double trainRatio = 1d/3;
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = "N. Tax", email = "n.tax@tue.nl")
	@PluginVariant(variantLabel = "Make train/test split", requiredParameterLabels = {0})
	public XLog calculateDfgFromModelPredictions(PluginContext context, XLog log){
		XLog logTrain = (XLog) log.clone();
		XLog logTest = (XLog) log.clone();
		for(int i=log.size()-1; i>=0; i--){
			if(getRandomBoolean(trainRatio)){
				logTrain.remove(i);
			}else{
				logTest.remove(i);
			}
		}
		context.getProvidedObjectManager().createProvidedObject("train", logTrain, XLog.class, context);
		context.getProvidedObjectManager().createProvidedObject("test", logTest, XLog.class, context);
		return null;
	}
	
	public boolean getRandomBoolean(double p){
		if(random == null){
			random = new Random();
		}
		return random.nextFloat() < p;
	}
}
