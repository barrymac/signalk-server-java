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

import static nz.co.fortytwo.signalk.util.SignalKConstants.*;

import java.io.IOException;

import mjson.Json;
import nz.co.fortytwo.signalk.handler.JsonStorageHandler;
import nz.co.fortytwo.signalk.model.SignalKModel;
import nz.co.fortytwo.signalk.util.Constants;
import nz.co.fortytwo.signalk.util.JsonConstants;
import nz.co.fortytwo.signalk.util.SGImplify;
import nz.co.fortytwo.signalk.util.SignalKConstants;
import nz.co.fortytwo.signalk.util.Util;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

/**
 * Periodic saves the current track to the signalkModel
 * 
 * @author robert
 * 
 */
public class TrackProcessor extends SignalkProcessor implements Processor {

	private static final String COORDINATES = "coordinates";
	private static final String GEOMETRY = "geometry";
	private static final String FEATURES = "features";
	private static Logger logger = Logger.getLogger(TrackProcessor.class);
	private static String latKey = vessels_dot_self_dot + nav_position_latitude;
	private static String lonKey = vessels_dot_self_dot + nav_position_longitude;
	private JsonStorageHandler storageHandler = null;
	private Json msg = Json.object();
	private Json currentTrack;
	private Json coords;
	private Json geometry;
	private static String geojson = "{\"features\":[{\"geometry\":{\"coordinates\":[],\"type\":\"LineString\"},\"properties\":null,\"id\":\"laqz\",\"type\":\"Feature\"}],\"type\":\"FeatureCollection\"}";
	private static int maxCount = 5000;
	private static int saveCount = 60;
	private static int count = 0;

	public TrackProcessor() throws Exception {
		storageHandler = new JsonStorageHandler();
		Json val = Json.object();
		val.set(JsonConstants.PATH, resources_routes + dot + SignalKConstants.currentTrack);
		currentTrack = Json.object();
		val.set(value, currentTrack);
		currentTrack.set(name, "Current Track");
		currentTrack.set(type, routes);
		currentTrack.set(key, SignalKConstants.currentTrack);
		currentTrack.set("description", "Auto saved current track");
		currentTrack.set(Constants.MIME_TYPE, Constants.MIME_TYPE_JSON);

		Json values = Json.array();
		values.add(val);

		Json update = Json.object();
		update.set(timestamp, DateTime.now().toDateTimeISO().toString());
		update.set(source, VESSELS_DOT_SELF);
		update.set(JsonConstants.VALUES, values);

		Json updates = Json.array();
		updates.add(update);

		msg.set(JsonConstants.CONTEXT, VESSELS_DOT_SELF);
		msg.set(JsonConstants.PUT, updates);
		// do we have an existing one? we dont want to stomp on it
		currentTrack.set("uri", "vessels/self/resources/routes/currentTrack.geojson");
		try {
			storageHandler.handle(msg);
			// should now have any existing track, so archive it, and start
			// fresh
			archiveTrack(msg);
		} catch (IOException io) {
			// no file, or unreadable
			currentTrack.delAt("uri");
		}
		Json geoJson = Json.read(geojson);
		geometry = geoJson.at(FEATURES).at(0).at(GEOMETRY);
		coords = geometry.at(COORDINATES);
		currentTrack.set(Constants.PAYLOAD, geoJson);

	}

	public void process(Exchange exchange) throws Exception {
		try {
			if (exchange.getIn().getBody() instanceof SignalKModel) {

				handle(exchange.getIn().getBody(SignalKModel.class));
			}else{
				if(logger.isDebugEnabled())logger.debug("Ignored, not a track:"+exchange.getIn().getBody(Json.class));
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	// @Override
	public void handle(SignalKModel node) {
		if (node.getData().size() == 0)
			return;

		if (node.getData().containsKey(latKey) && node.getData().containsKey(lonKey)) {
			if (logger.isTraceEnabled())
				logger.trace("TrackProcessor  updating " + node);
			// we have a track change.
			coords.add(Json.array(node.get(lonKey), node.get(latKey)));
			count++;
			// append to file
			if (count % saveCount == 0) {
				// save it
				// if(logger.isDebugEnabled())logger.debug("Track:"+msg);
				inProducer.sendBody(msg.toString());
			}
			if (count % (saveCount * 4) == 0) {
				// simplify to about 2m out of true (at equator)
				if (logger.isDebugEnabled())
					logger.debug("Simplify Track, size:" + coords.asList().size());// +":"+coords);
				coords = SGImplify.simplifyLine2D(0.00002, coords);
				geometry.set(COORDINATES, coords);
				if (logger.isDebugEnabled())
					logger.debug("  done, size:" + coords.asList().size());
				count = coords.asList().size();
			}
			// reset?
			if (count > maxCount) {
				archiveTrack(msg);
				count = 0;
				coords.asJsonList().clear();
			}
		}
	}

	private void archiveTrack(Json message) {
		Json lastTrack = message.dup();
		String time = Util.getIsoTimeString();
		if (logger.isDebugEnabled())
			logger.debug("Archive Track to File:" + SignalKConstants.currentTrack + time);
		Json val = lastTrack.at(JsonConstants.PUT).at(0).at(JsonConstants.VALUES).at(0);

		time = time.substring(0, time.indexOf("."));
		val.set(JsonConstants.PATH, resources_routes + dot + SignalKConstants.currentTrack + time);
		val.at(value).set(name, "Track at " + time);
		currentTrack.set(key, SignalKConstants.currentTrack + time);
		inProducer.sendBody(lastTrack.toString());

	}
}
