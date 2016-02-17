/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 * 
 */
package org.matsim.contrib.noise;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import org.matsim.contrib.noise.data.NoiseAllocationApproach;
import org.matsim.contrib.noise.data.NoiseContext;
import org.matsim.contrib.noise.routing.NoiseTollDisutilityCalculatorFactory;
import org.matsim.contrib.noise.utils.ProcessNoiseImmissions;

/**
 * 
 * @author lkroeger, ikaddoura
 *
 */

public class NoiseOnlineControler {
	private static final Logger log = Logger.getLogger(NoiseOnlineControler.class);

	private static String configFile;

	public static void main(String[] args) throws IOException {
		
		if (args.length > 0) {
			
			configFile = args[0];
			log.info("Config file: " + configFile);
			
		} else {
			
			configFile = "/pathToConfigFile/config.xml";
		}
				
		NoiseOnlineControler noiseImmissionControler = new NoiseOnlineControler();
		noiseImmissionControler.run(configFile);
	}

	public void run(String configFile) {
				
		// noise parameters

		NoiseConfigGroup noiseParameters = new NoiseConfigGroup();		
		
		noiseParameters.setReceiverPointGap(100.);
		
		String[] consideredActivitiesForReceiverPointGrid = {"home", "work"};
		noiseParameters.setConsideredActivitiesForReceiverPointGridArray(consideredActivitiesForReceiverPointGrid);			
			
		String[] consideredActivitiesForDamages = {"home", "work"};
		noiseParameters.setConsideredActivitiesForSpatialFunctionalityArray(consideredActivitiesForDamages);
		
		noiseParameters.setNoiseAllocationApproach(NoiseAllocationApproach.MarginalCost);		
		noiseParameters.setScaleFactor(10.);
		
		// controler
		
		Controler controler = new Controler(configFile);

		NoiseContext noiseContext = new NoiseContext(controler.getScenario(), noiseParameters);
		final NoiseTollDisutilityCalculatorFactory tollDisutilityCalculatorFactory = new NoiseTollDisutilityCalculatorFactory(noiseContext);
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bindCarTravelDisutilityFactory().toInstance(tollDisutilityCalculatorFactory);
			}
		});
		controler.addControlerListener(new NoiseCalculationOnline(noiseContext));

		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		controler.run();
		
		log.info("Processing the noise immissions...");
		
		String workingDirectory = controler.getConfig().controler().getOutputDirectory() + "/ITERS/it." + controler.getConfig().controler().getLastIteration() + "/immisions/";
		String receiverPointsFile = controler.getConfig().controler().getOutputDirectory() + "/receiverPoints/receiverPoints.csv";

		ProcessNoiseImmissions readNoiseFile = new ProcessNoiseImmissions(workingDirectory, receiverPointsFile, noiseParameters.getReceiverPointGap());
		readNoiseFile.run();
		
	}
	
}