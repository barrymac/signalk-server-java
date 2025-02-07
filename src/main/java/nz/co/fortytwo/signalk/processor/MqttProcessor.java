/*
 * 
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.co.fortytwo.signalk.processor;

import nz.co.fortytwo.signalk.util.Constants;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.Logger;


/**
 * Convert to Mqtt string to byte array message
 * 
 * @author robert
 * 
 */
public class MqttProcessor extends SignalkProcessor implements Processor {

	private static Logger logger = Logger.getLogger(MqttProcessor.class);

	
	public void process(Exchange exchange) throws Exception {

		try {
			logger.debug("Message: "+exchange.getIn().getBody().getClass());
			logger.debug("Message: "+exchange.getIn().getBody());
			if (exchange.getIn().getBody() == null || !(exchange.getIn().getBody() instanceof String)){
				return;
			}
			String msg = exchange.getIn().getBody(String.class);
			exchange.getIn().setBody(msg.getBytes());
			String dest = exchange.getIn().getHeader(Constants.DESTINATION, String.class);
			exchange.getIn().setHeader(Constants.CamelMQTTPublishTopic, dest.replace('.', '/'));
			if(logger.isDebugEnabled()){
				logger.debug("Message: "+msg);
				logger.debug("Message destination :" + exchange.getIn().getHeader(Constants.CamelMQTTPublishTopic));
			}
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

}
