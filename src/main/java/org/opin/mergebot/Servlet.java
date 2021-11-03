/**
 * Copyright 2020 Association for the promotion of open-source insurance software and for the establishment of open interface standards in the insurance industry (Verein zur Foerderung quelloffener Versicherungssoftware und Etablierung offener Schnittstellenstandards in der Versicherungsbranche)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opin.mergebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Receives the POST-request from the Webhook and delegates it to the
 * corresponding event class.
 */
public class Servlet extends jakarta.servlet.http.HttpServlet {
	private static final long serialVersionUID = -4244592568945901972L;

	private static Logger logger = LogManager.getLogger(Servlet.class);
	static final EventFactory eventFactory = new EventFactory();

	@Override
	public void doPost(HttpServletRequest incoming, HttpServletResponse outgoing) {
		Event event = null;
		try {
			final String eventType = incoming.getHeader("X-GitHub-Event");
			event = eventFactory.getEvent(eventType);
			event.process(incoming, outgoing, eventType);
		} catch (NullPointerException e) {
			logger.info("This type of event is not registered in the Event Mapper");
			logger.error("ERROR: This type of event is not registered in the Event Mapper {}{}", event, e);
		}
	}
}