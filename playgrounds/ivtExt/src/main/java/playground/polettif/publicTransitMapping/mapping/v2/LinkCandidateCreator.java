/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

package playground.polettif.publicTransitMapping.mapping.v2;

import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import playground.polettif.publicTransitMapping.config.PublicTransitMappingConfigGroup;
import playground.polettif.publicTransitMapping.mapping.pseudoPTRouter.LinkCandidate;

import java.util.Set;

public interface LinkCandidateCreator {
	
	void createLinkCandidates();

	Set<LinkCandidate> getLinkCandidates(String scheduleTransportMode, TransitStopFacility transitStopFacility);

	void addManualLinkCandidates(Set<PublicTransitMappingConfigGroup.ManualLinkCandidates> manualLinkCandidates);

	boolean stopFacilityOnlyHasLoopLink(TransitStopFacility stopFacility, String transportMode);
}