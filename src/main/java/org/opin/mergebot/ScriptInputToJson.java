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
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

public class ScriptInputToJson {
	
	private static Logger logger = LogManager.getLogger(ScriptInputToJson.class);
	
	public void createScriptInputAsJson(ScriptInputPre scriptInput, File preFile) {
		JsonFactory jsonFactory = new JsonFactory();
		jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);
		jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
		ObjectMapper mapper = new ObjectMapper(jsonFactory);
		try {
			String jsonString = mapper.writeValueAsString(scriptInput);
			mapper.writeValue(preFile, scriptInput );
			logger.info("STRING from Pre-JSON {} {}",preFile.getName(), jsonString);
		} catch (IOException e) {
			logger.error("ERROR: Generating JSON for PreCommit", e);
		}
	}
	
	public void createScriptInputAsJson(ScriptInputPost scriptInput, File postFile) {
		JsonFactory jsonFactory = new JsonFactory();
		jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);
		jsonFactory.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
		ObjectMapper mapper = new ObjectMapper(jsonFactory);
		try {
			String jsonString = mapper.writeValueAsString(scriptInput);
			mapper.writeValue(postFile, scriptInput );
			logger.info("STRING from Post-JSON {} {}",postFile.getName(), jsonString);
		} catch (IOException e) {
			logger.error("ERROR: Generating JSON for PostCommit", e);
		}
	}
}
