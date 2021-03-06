package org.subnode.service;

import java.util.Map;

import com.mongodb.client.MongoDatabase;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.subnode.config.AppFilter;
import org.subnode.config.AppSessionListener;
import org.subnode.mongo.MongoUtil;
import org.subnode.mongo.MongoAppConfig;
import org.subnode.mongo.MongoRead;
import org.subnode.mongo.MongoSession;
import org.subnode.mongo.RunAsMongoAdmin;
import org.subnode.mongo.model.SubNode;
import org.subnode.util.Const;
import org.subnode.util.ValContainer;
import org.subnode.util.XString;

/**
 * Service methods for System related functions. Admin functions.
 */
@Component
public class SystemService {
	private static final Logger log = LoggerFactory.getLogger(SystemService.class);

	@Autowired
	private MongoAppConfig mac;

	@Autowired
	private MongoUtil util;

	@Autowired
	private MongoRead read;

	@Autowired
	private RunAsMongoAdmin adminRunner;

	@Autowired
	private ExportJsonService exportJsonService;

	@Autowired
	private AttachmentService attachmentService;

	public String initializeAppContent() {
		ValContainer<String> ret = new ValContainer<String>();
		adminRunner.run(session -> {
			ret.setVal(exportJsonService.resetNode(session, "public"));
			// ret.setVal(exportJsonService.resetNode(session, "books"));
			ret.setVal(exportJsonService.resetNode(session, "rss"));
		});

		return ret.getVal();
	}

	public String compactDb() {
		attachmentService.gridMaintenanceScan();

		MongoDatabase database = mac.mongoClient().getDatabase(MongoAppConfig.databaseName);
		Document result = database.runCommand(new Document("compact", "nodes"));

		StringBuilder ret = new StringBuilder();
		ret.append("Compact Results:\n");
		for (Map.Entry<String, Object> set : result.entrySet()) {
			ret.append(String.format("%s: %s%n", set.getKey(), set.getValue()));
		}
		return ret.toString();
	}

	public static void logMemory() {
		// Runtime runtime = Runtime.getRuntime();
		// long freeMem = runtime.freeMemory() / ONE_MB;
		// long maxMem = runtime.maxMemory() / ONE_MB;
		// log.info(String.format("GC Cycle. FreeMem=%dMB, MaxMem=%dMB", freeMem,
		// maxMem));
	}

	public String getJson(MongoSession session, String nodeId) {
		SubNode node = read.getNode(session, nodeId, true);
		if (node != null) {
			return XString.prettyPrint(node);
		} else {
			return "node not found!";
		}
	}

	public String getSystemInfo() {
		StringBuilder sb = new StringBuilder();
		Runtime runtime = Runtime.getRuntime();
		runtime.gc();
		long freeMem = runtime.freeMemory() / Const.ONE_MB;
		sb.append(String.format("Server Free Memory: %dMB<br>", freeMem));
		sb.append(String.format("Session Count: %d<br>", AppSessionListener.getSessionCounter()));
		sb.append(getIpReport());
		sb.append("<p>" + util.getNodeReport());
		return sb.toString();
	}

	private static String getIpReport() {
		return "Number of Unique IPs since startup: " + AppFilter.getUniqueIpHits().size();
		// StringBuilder sb = new StringBuilder();
		// sb.append("Unique IPs During Run<br>");
		// int count = 0;
		// HashMap<String, Integer> map = AppFilter.getUniqueIpHits();
		// synchronized (map) {
		// for (String key : map.keySet()) {
		// int hits = map.get(key);
		// sb.append("IP=" + key + " hits=" + hits);
		// sb.append("<br>");
		// count++;
		// }
		// }
		// sb.append("count=" + count + "<br>");
		// return sb.toString();
	}
}

