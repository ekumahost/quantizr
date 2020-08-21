package org.subnode.mongo;

import org.subnode.util.MongoRunnable;
import org.subnode.util.MongoRunnableEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Helper class to run some processing workload as the admin user. Simplifies by encapsulating the
 * session management at this abstracted layer. Run primarily as a Java-8 Lambda, and working very
 * similar to what is also known as an AOP aspect, although not using an 'aspect' but mainly just a
 * Lambda (i.e. 'Functional Interface')
 */
@Component
public class RunAsMongoAdminEx<T> {
	private static final Logger log = LoggerFactory.getLogger(RunAsMongoAdmin.class);

	@Autowired
	private MongoApi api;

	public T run(MongoRunnableEx runner) {
		MongoSession session = null;
		Object ret = null;
		try {
			session = api.getAdminSession();
			ret = runner.run(session);
			api.saveSession(session);
		}
		catch (Exception ex) {
			log.error("error", ex);
			throw ex;
		}
		finally {
		}
		return (T)ret;
	}
}