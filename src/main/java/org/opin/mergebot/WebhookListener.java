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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONObject;

/**
 * Starts the application and the Jetty-Server at the specified port from the configuration.
 */
public class WebhookListener {
	static JSONObject configuration;
	static List<String> listPathsManifest = new ArrayList<>();
	static Logger logger;
	public static void main(String[] args) {
		try {
			String contentConfiguration = FileUtils.readFileToString(new File(args[0]),StandardCharsets.UTF_8);
			configuration = new JSONObject(contentConfiguration);
			ThreadContext.put("fileName", configuration.getString("LOG_NAME"));
			logger = LogManager.getLogger(WebhookListener.class);
			Server server = new Server(configuration.getInt("PORT"));
			ServletContextHandler context = new ServletContextHandler();
			server.setHandler(context);

			context.addServlet(new ServletHolder(new Servlet()), "/*");
			server.start();
			server.join();
		} catch (Exception e) {
			logger.error("ERROR: Jetty can not be started.", e);
		} 
	}
}