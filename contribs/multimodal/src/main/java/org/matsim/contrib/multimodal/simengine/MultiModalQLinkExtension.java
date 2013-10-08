/* *********************************************************************** *
 * project: org.matsim.*
 * MultiModalQLinkExtension.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.contrib.multimodal.simengine;

import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.Wait2LinkEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.mobsim.framework.HasPerson;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.Tuple;

public class MultiModalQLinkExtension {

	private final Link qLink;
	private final MultiModalQNodeExtension toNode;
	private MultiModalSimEngine simEngine;

	/*
	 * Is set to "true" if the MultiModalQLinkExtension has active Agents.
	 */
	protected AtomicBoolean isActive = new AtomicBoolean(false);

	private final Queue<Tuple<Double, MobsimAgent>> agents = new PriorityQueue<Tuple<Double, MobsimAgent>>(30, new TravelTimeComparator());
	private final Queue<MobsimAgent> waitingAfterActivityAgents = new LinkedList<MobsimAgent>();
	private final Queue<MobsimAgent> waitingToLeaveAgents = new LinkedList<MobsimAgent>();

	public MultiModalQLinkExtension(Link link, MultiModalSimEngine simEngine, MultiModalQNodeExtension multiModalQNodeExtension) {
		this.qLink = link;
		this.simEngine = simEngine;
		this.toNode = multiModalQNodeExtension;
	}

	/*package*/ void setMultiModalSimEngine(MultiModalSimEngine simEngine) {
		this.simEngine = simEngine;
	}

	/*package*/ boolean hasWaitingToLeaveAgents() {
		return this.waitingToLeaveAgents.size() > 0;
	}

	/**
	 * Adds a mobsimAgent to the link (i.e. the "queue"), called by
	 * {@link MultiModalQNode#moveAgentOverNode(MobsimAgent, double)}.
	 *
	 * @param personAgent
	 *          the personAgent
	 */
	public void addAgentFromIntersection(MobsimAgent mobsimAgent, double now) {
		this.activateLink();

		this.addAgent(mobsimAgent, now);

		this.simEngine.getMobsim().getEventsManager().processEvent(new LinkEnterEvent(now, mobsimAgent.getId(), qLink.getId(), null));
	}

	private void addAgent(MobsimAgent mobsimAgent, double now) {

		Map<String, TravelTime> multiModalTravelTime = simEngine.getMultiModalTravelTimes();
		Person person = null;
		if (mobsimAgent instanceof HasPerson) {
			person = ((HasPerson) mobsimAgent).getPerson(); 
		}
		double travelTime = multiModalTravelTime.get(mobsimAgent.getMode()).getLinkTravelTime(qLink, now, person, null);
		double departureTime = now + travelTime;

		departureTime = Math.round(departureTime);

		agents.add(new Tuple<Double, MobsimAgent>(departureTime, mobsimAgent));
	}

	public void addDepartingAgent(MobsimAgent mobsimAgent, double now) {
		this.waitingAfterActivityAgents.add(mobsimAgent);
		this.activateLink();

		this.simEngine.getMobsim().getEventsManager().processEvent(
				new Wait2LinkEvent(now, mobsimAgent.getId(), qLink.getId(), null));
	}

	protected boolean moveLink(double now) {
		
		boolean keepLinkActive = moveAgents(now);
		this.isActive.set(keepLinkActive);

		moveWaitingAfterActivityAgents();

		// If agents are ready to leave the link, ensure that the to Node is active and handles them.
		if (this.hasWaitingToLeaveAgents()) toNode.activateNode();
		
		return keepLinkActive;
	}

	private void activateLink() {
		/*
		 * If isActive is false, then it is set to true ant the
		 * link is activated. Using an AtomicBoolean is thread-safe.
		 * Otherwise, it could be activated multiple times concurrently.
		 */
		if (this.isActive.compareAndSet(false, true)) {			
			simEngine.activateLink(this);
		}
	}

	/*
	 * Returns true, if the Link has to be still active.
	 */
	private boolean moveAgents(double now) {
		Tuple<Double, MobsimAgent> tuple = null;

		while ((tuple = agents.peek()) != null) {
			/*
			 * If the MobsimAgent cannot depart now:
			 * At least still one Agent is still walking/cycling/... on the Link, therefore
			 * it cannot be deactivated. We return true (Link has to be kept active).
			 */
			if (tuple.getFirst() > now) {
				return true;
			}

			/*
			 *  Agent starts next Activity at the same link or leaves the Link.
			 *  Therefore remove him from the Queue.
			 */
			agents.poll();

			// Check if MobsimAgent has reached destination:
			MobsimDriverAgent driver = (MobsimDriverAgent) tuple.getSecond();
			if ((qLink.getId().equals(driver.getDestinationLinkId())) && (driver.chooseNextLinkId() == null)) {
				driver.endLegAndComputeNextState(now);
				this.simEngine.internalInterface.arrangeNextAgentState(driver);
			}
			/*
			 * The PersonAgent can leave, therefore we move him to the waitingToLeave Queue.
			 */
			else {
				this.waitingToLeaveAgents.add(driver);
			}
		}
		
		return agents.size() > 0;
	}

	/*
	 * Add all Agents that have ended an Activity to the waitingToLeaveLink Queue.
	 * If waiting Agents exist, the toNode of this Link is activated.
	 */
	private void moveWaitingAfterActivityAgents() {
		waitingToLeaveAgents.addAll(waitingAfterActivityAgents);
		waitingAfterActivityAgents.clear();
	}

	public MobsimAgent getNextWaitingAgent(double now) {
		MobsimAgent personAgent = waitingToLeaveAgents.poll();
		if (personAgent != null) {
			this.simEngine.getMobsim().getEventsManager().processEvent(new LinkLeaveEvent(now, personAgent.getId(), qLink.getId(), null));
		}
		return personAgent;
	}

	public void clearVehicles() {
		double now = this.simEngine.getMobsim().getSimTimer().getTimeOfDay();

		for (Tuple<Double, MobsimAgent> tuple : agents) {
			MobsimAgent mobsimAgent = tuple.getSecond();
			this.simEngine.getMobsim().getEventsManager().processEvent(
					new PersonStuckEvent(now, mobsimAgent.getId(), qLink.getId(), mobsimAgent.getMode()));
			this.simEngine.getMobsim().getAgentCounter().incLost();
			this.simEngine.getMobsim().getAgentCounter().decLiving();
		}
		
		for (MobsimAgent mobsimAgent : this.waitingAfterActivityAgents) {
			this.simEngine.getMobsim().getEventsManager().processEvent(
					new PersonStuckEvent(now, mobsimAgent.getId(), qLink.getId(), mobsimAgent.getMode()));
			this.simEngine.getMobsim().getAgentCounter().incLost();
			this.simEngine.getMobsim().getAgentCounter().decLiving();
		}
		
		for (MobsimAgent mobsimAgent : this.waitingToLeaveAgents) {
			this.simEngine.getMobsim().getEventsManager().processEvent(
					new PersonStuckEvent(now, mobsimAgent.getId(), qLink.getId(), mobsimAgent.getMode()));
			this.simEngine.getMobsim().getAgentCounter().incLost();
			this.simEngine.getMobsim().getAgentCounter().decLiving();
		}
		
		this.agents.clear();
	}

	private static class TravelTimeComparator implements Comparator<Tuple<Double, MobsimAgent>>, Serializable {
		private static final long serialVersionUID = 1L;
		@Override
		public int compare(final Tuple<Double, MobsimAgent> o1, final Tuple<Double, MobsimAgent> o2) {
			int ret = o1.getFirst().compareTo(o2.getFirst()); // first compare time information
			if (ret == 0) {
				ret = o2.getSecond().getId().compareTo(o1.getSecond().getId()); // if they're equal, compare the Ids: the one with the larger Id should be first
			}
			return ret;
		}
	}
}
